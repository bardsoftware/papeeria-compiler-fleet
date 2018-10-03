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

import com.bardsoftware.papeeria.backend.tex.*
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
import java.util.concurrent.*
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

@Throws(IOException::class)
private fun getTaskDir(tasksDirectory: String): Path {
    val directoryPath = Paths.get(tasksDirectory)
    val directoryFile = directoryPath.toFile()
    val directoryName = directoryFile.name

    directoryExistingCheck(directoryFile)
    if (!directoryFile.canWrite()) {
        throw IOException("tasksDir directory(name is $directoryName) isn't writable")
    }

    return directoryPath
}

open class TaskReceiver(tasksDirectory: String,
                        val resultPublisher: PublisherApi
) : CompilerFleetMessageReceiver() {
    private val tasksDir: Path = getTaskDir(tasksDirectory)
    private val dockerProcessor = DockerProcessor(getDefaultDockerClient())
    private val MOCK_FILE_NAME = "example.pdf"
    private val MOCK_PDF_BYTES = TaskReceiver::class.java.classLoader.getResourceAsStream(MOCK_FILE_NAME).readBytes()

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

        val rootFile = unzipCompileTask(request.taskId, rootFileFullPath, zippedProject)
        return dockerProcessor.compileRmdToPdf(rootFile)
    }

    override fun processMessage(message: PubsubMessage): Boolean {
        val request = CompilerFleet.CompilerFleetRequest.parseFrom(message.data)
        val taskId = request.taskId
        var isPublished = true

        val compiledBytes = if (request.compiler == CompilerFleet.Compiler.MOCK) {
            ByteString.copyFrom(MOCK_PDF_BYTES)
        } else {
            val compiledFile = compileProject(request)
            ByteString.copyFrom(FileUtils.readFileToByteArray(compiledFile))
        }

        val onPublishFailureCallback = {
            LOGGER.info("Publish $taskId failed with code ${StatusCode.FAILURE}")
            isPublished = false
        }

        val data = buildResultData(taskId, compiledBytes, StatusCode.SUCCESS.ordinal)
        resultPublisher.publish(data, onPublishFailureCallback)

        return isPublished
    }
}

class MarkdownTaskReceiver(
        private val texCompiler: CompilerApi,
        tasksDirectory: String,
        private val resultPublisher: PublisherApi,
        private val executor: ExecutorService,
        private val config: Config = DEFAULT_CONFIG) : CompilerFleetMessageReceiver() {

    private val tasksDir: Path = getTaskDir(tasksDirectory)
    private val arguments = listOf("projectRootAbsPath", "workingDirRelPath",
            "inputFileName", "outputFileName", "mainFont")
    private val COMPILE_COMMAND_KEY = "pandoc.compile.command"
    private val currentTasks = ConcurrentHashMap<String, Future<Boolean>>()

    override fun processMessage(message: PubsubMessage): Boolean {
        val request = Request.parseFrom(message.data)

        when (request.typeCase) {
            Request.TypeCase.COMPILE -> {
                processCompile(request.compile)
                return true
            }

            Request.TypeCase.CANCEL  -> {
                return processCancel(request.cancel)
            }

            else -> {
                LOGGER.error("Request type = {} is not set properly", request.typeCase)
            }
        }

        return true
    }

    private fun processCancel(request: CancelRequestProto): Boolean {
        LOGGER.debug("Canceling the task with id = {}", request.taskId)
        val status = cancelTask(request)
        val cancelBuilder = CompilerFleet.Cancel.newBuilder()
                .setTaskId(request.taskId)
                .setStatus(status)
                .setCpuTime(0) // TODO: put value when cancel task is fixed
        val onPublishFailureCallback = {
            LOGGER.info("Publish cancel task ${request.taskId} response failed with code ${StatusCode.FAILURE}")
        }

        val data = CompilerFleet.CompilerFleetResult
                .newBuilder()
                .setCancel(cancelBuilder)
                .build()
                .toByteString()
        resultPublisher.publish(data, onPublishFailureCallback)
        return true
    }

    private fun cancelTask(request: CancelRequestProto): CompilerFleet.Cancel.Status {
        return if (!currentTasks.contains(request.taskId)) {
            CompilerFleet.Cancel.Status.NOT_FOUND
        } else {
            val result = currentTasks[request.taskId]?.cancel(true)
            currentTasks.remove(request.taskId)
            // will canceling the thread solve the problem?
            // another idea: two tasks maps of each processing stage
            // 1) tasks are converting to latex at the moment
            // 2) tasks are pushed to the texbe
            //
            // 1) we stop by stopping the cmd process
            // 2) we stop by sending a cancel request to the texbe

            if (true == result) {
                CompilerFleet.Cancel.Status.OK
            } else {
                CompilerFleet.Cancel.Status.FAILED
            }
        }
    }

    fun isTaskDone(taskId: String): Boolean {
        return !this.currentTasks.containsKey(taskId)
    }

    private fun processCompile(request: CompileRequest) {
        LOGGER.debug("Converting Markdown to tex: {}", request.mainFileName)

        if (currentTasks.contains(request.id)) {
            LOGGER.debug("Task with id {} is already executing", request.id)
            return
        }

        val future: Future<Boolean> = executor.submit(Callable {
            processTask(request)
        })

        currentTasks[request.id] = future
    }

    private fun processTask(request: CompileRequest): Boolean {
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

        val data = buildResultData(request, response)
        resultPublisher.publish(data, onPublishFailureCallback)
        currentTasks.remove(request.id)
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

        // the '\' exists because of the double slash in the config,
        // where in turn $ should be protected, so we remove the value here
        val rawCommandLine = config.getString(COMPILE_COMMAND_KEY).replace("\\", "")
        val commandLine = substitutor.replace(rawCommandLine)
        return runCommandLine(commandLine)
    }

    private fun compileTex(request: CompileRequest, tex: File): CompileResponse {
        val targetTex = FileDto
                .newBuilder()
                .setName(tex.name)
                .build()

        val texMainName = File(request.mainFileName).parent + tex.name
        val texRequest = 
                request.toBuilder()
                       .setMainFileName(texMainName)
                       .setFileRequest(request.fileRequest
                               .toBuilder().addFile(targetTex).build())
                       .setSkipFetchFiles(true)
                       .build()
        return texCompiler.compile(texRequest)
    }
}