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
import com.bardsoftware.papeeria.backend.tex.Engine
import com.bardsoftware.papeeria.backend.tex.TexbeGrpc
import com.google.api.client.util.ByteStreams
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.common.io.Files
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils
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
                        val resultPublisher: Publisher
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
        private val texbeCompilerStub: TexbeGrpc.TexbeBlockingStub,
        tasksDirectory: String,
        resultPublisher: Publisher) : TaskReceiver(tasksDirectory, resultPublisher) {

    override fun processMessage(message: PubsubMessage): Boolean {
        val request = CompileRequest.parseFrom(message.data)
        fetchProjectFiles(request)
        convertMarkdown(request)

        val taskId = request.id
        var isPublished = true

        val onPublishFailureCallback = {
            LOGGER.info("Publish $taskId failed with code ${StatusCode.FAILURE}")
            isPublished = false
        }

        val data = getResultData(taskId, ByteString.copyFrom(MOCK_PDF_BYTES),
                MOCK_FILE_NAME, StatusCode.SUCCESS.ordinal)
        resultPublisher.publish(data, onPublishFailureCallback)
        return isPublished
    }

    private fun fetchProjectFiles(request: CompileRequest) {
        val fetchRequest = request.toBuilder().setEngine(Engine.NONE).build()
        texbeCompilerStub.compile(fetchRequest)
    }

    // converts Markdown into tex via pandoc
    fun convertMarkdown(request: CompileRequest, config: Config = DEFAULT_CONFIG) {
        val outputFileName = Files.getNameWithoutExtension(request.mainFileName) + ".tex"
        val projTasks = this.tasksDir.resolve(request.id)
        val mainFile = projTasks.resolve("files").resolve(request.mainFileName)
        val outputFile = projTasks.resolve(outputFileName)
        val projectRootAbsPath = this.tasksDir.toAbsolutePath().parent

        val arguments = PandocArguments(projectRootAbsPath, projTasks, mainFile, outputFile)
        compile(config, arguments)
    }
}