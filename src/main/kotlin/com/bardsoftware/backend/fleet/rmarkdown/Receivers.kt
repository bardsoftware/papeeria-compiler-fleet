/**
    Copyright 2018 BarD Software s.r.o

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    Author: Mikhail Shavkunov (@shavkunov)
 */
package com.bardsoftware.backend.fleet.rmarkdown

import com.bardsoftware.papeeria.backend.tex.CompileRequest
import com.bardsoftware.papeeria.backend.tex.CompileResponse
import com.bardsoftware.papeeria.backend.tex.Engine
import com.bardsoftware.papeeria.backend.tex.FileDto
import com.google.api.client.util.ByteStreams
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.common.io.Files
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.apache.commons.text.StringSubstitutor
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private val LOGGER = LoggerFactory.getLogger("TaskReceiver")

abstract class CompilerFleetMessageReceiver : MessageReceiver {
    override fun receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        val isProcessed = processMessage(message)

        if (isProcessed) {
            consumer.ack()
        }
    }

    abstract fun processMessage(message: PubsubMessage): Boolean
}

open class TaskReceiver(tasksDirectory: String,
                        val resultPublisher: PublisherApi
) : CompilerFleetMessageReceiver() {
    protected val tasksDir: Path
    private val dockerProcessor = DockerProcessor(getDefaultDockerClient())
    protected val MOCK_FILE_NAME = "example.pdf"
    protected val MOCK_PDF_BYTES = TaskReceiver::class.java.classLoader.getResourceAsStream(MOCK_FILE_NAME).readBytes()

    init {
        val directoryPath = Paths.get(tasksDirectory)
        val directoryFile = directoryPath.toFile()
        val directoryName = directoryFile.name

        directoryExistingCheck(directoryFile)
        if (!directoryFile.canWrite()) {
            throw IOException("tasksDir directory(name is $directoryName) isn't writable")
        }

        this.tasksDir = directoryPath
    }

    fun unzipCompileTask(taskId: String, rootFileName: String, zipBytes: ByteString): File {
        val destination = this.tasksDir.resolve(taskId).resolve("files")
        val zipStream = ZipInputStream(ByteArrayInputStream(zipBytes.toByteArray()))
        var entry: ZipEntry? = zipStream.nextEntry

        while (entry != null) {
            val filename = entry.name
            val newFile = destination.resolve(filename).toFile()

            if (!newFile.parentFile.exists() && !newFile.parentFile.mkdirs()) {
                val dirName = newFile.parentFile.name
                throw IOException("In task(id = $taskId): unable to create $dirName directory while unzipping")
            }

            FileOutputStream(newFile).use {
                ByteStreams.copy(zipStream, it)
            }

            entry = zipStream.nextEntry
        }

        val rootFile = destination.resolve(rootFileName).toFile()
        if (!rootFile.exists()) {
            throw IOException("In task(id = $taskId): path to root file doesn't exists")
        }

        return rootFile
    }

    private fun compileProject(request: CompilerFleet.CompilerFleetRequest): File {
        val rootFileFullPath = request.rootFileName
        val zippedProject = request.zipBytes
        val engine = request.engine

        val rootFile = unzipCompileTask(request.taskId, rootFileFullPath, zippedProject)
        return dockerProcessor.compileRmdToPdf(rootFile)
    }

    override fun processMessage(message: PubsubMessage): Boolean {
        val request = CompilerFleet.CompilerFleetRequest.parseFrom(message.data)
        val taskId = request.taskId
        var isPublished = true

        val compiledBytes: ByteString
        val outputFileName: String

        if (request.compiler == CompilerFleet.Compiler.MOCK) {
            compiledBytes = ByteString.copyFrom(MOCK_PDF_BYTES)
            outputFileName = MOCK_FILE_NAME
        } else {
            val compiledFile = compileProject(request)
            outputFileName = compiledFile.name
            compiledBytes = ByteString.copyFrom(FileUtils.readFileToByteArray(compiledFile))
        }

        val onPublishFailureCallback = {
            LOGGER.info("Publish $taskId failed with code ${StatusCode.FAILURE}")
            isPublished = false
        }

        val data = getResultData(taskId, compiledBytes, outputFileName, StatusCode.SUCCESS.ordinal)
        resultPublisher.publish(data, onPublishFailureCallback)

        return isPublished
    }
}

class MarkdownTaskReceiver(
        private val texCompiler: CompilerApi,
        tasksDirectory: String,
        resultPublisher: PublisherApi,
        private val config: Config = DEFAULT_CONFIG) : TaskReceiver(tasksDirectory, resultPublisher) {

    private val arguments = listOf("projectRootAbsPath", "workingDirRelPath",
            "inputFileName", "outputFileName", "mainFont")
    private val COMPILE_COMMAND_KEY = "pandoc.compile.command"

    override fun processMessage(message: PubsubMessage): Boolean {
        val request = CompileRequest.parseFrom(message.data)
        LOGGER.debug("Converting Markdown to tex: {}", request.mainFileName)

        fetchProjectFiles(request)
        val commandArguments = getCmdLineArguments(request)
        val exitCode = convertMarkdown(commandArguments)
        if (exitCode != 0) {
            LOGGER.error("Failed to convert Markdown to tex with exitcode {}", exitCode)
            return false
        }

        val outputName = Files.getNameWithoutExtension(request.mainFileName) + ".tex"
        val convertedMarkdown = this.tasksDir
                .resolve(request.id)
                .resolve("files")
                .resolve(outputName)
                .toFile()
        val response = compileTex(request, convertedMarkdown)
        val taskId = request.id

        val onPublishFailureCallback = {
            LOGGER.info("Publish $taskId failed with code ${StatusCode.FAILURE}")
        }

        val data = getResultData(taskId, response.pdfFile,
                Files.getNameWithoutExtension(request.mainFileName) + ".pdf", response.status.ordinal)
        resultPublisher.publish(data, onPublishFailureCallback)
        return true
    }

    private fun fetchProjectFiles(request: CompileRequest) {
        LOGGER.debug("Fetching project with {} id from texbe", request.id)
        val fetchRequest = request.toBuilder().setEngine(Engine.NONE).build()
        texCompiler.compile(fetchRequest)
    }

    private fun getCmdLineArguments(request: CompileRequest): Map<String, String> {
        val outputFileName = Files.getNameWithoutExtension(request.mainFileName) + ".tex"
        val projTasks = this.tasksDir.resolve(request.id).resolve("files")
        val mainFile = StringUtils.stripStart(request.mainFileName, "/")
        val values = listOf(projTasks.toString(), "",
                mainFile, outputFileName, PANDOC_DEFAULT_FONT)

        return (arguments zip values).map { it.first to "\"${it.second}\"" }.toMap()
    }

    // converts Markdown into tex via pandoc
    private fun convertMarkdown(commandArguments: Map<String, String>): Int {
        val substitutor = StringSubstitutor(commandArguments)
        val rawCommandLine = config.getString(COMPILE_COMMAND_KEY)
        val commandLine = substitutor.replace(rawCommandLine)
        return runCommandLine(commandLine.replace("\\", ""))
    }

    private fun compileTex(request: CompileRequest, tex: File): CompileResponse {
        val targetTex = FileDto
                .newBuilder()
                .setName(tex.name)
                .build()

        request.fileRequest.toBuilder().addFile(targetTex).build()
        val texRequest = 
                request.toBuilder()
                       .setMainFileName(tex.name)
                       .setOutputBaseName(Files.getNameWithoutExtension(tex.name))
                       .setFileRequest(request.fileRequest
                               .toBuilder().addFile(targetTex).build())
                       .setIsFilesSaved(true)
                       .build()
        return texCompiler.compile(texRequest)
    }
}