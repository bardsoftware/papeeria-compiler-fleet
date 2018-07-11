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

import com.google.api.client.util.ByteStreams
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.SubscriptionName
import com.xenomachina.argparser.ArgParser
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SubscriberArgs(parser: ArgParser) {
    val subscription by parser.storing(
            "--sub",
            help = "subscription topic name")

    val resultTopic by parser.storing(
            "-r", "--result-topic",
            help = "result topic name"
    )

    val tasksDir by parser.storing(
            "--tasks-dir",
            help = "root of tasks content"
    )
}

enum class StatusCode {
    SUCCESS, FAILURE;
}

private val PROJECT_ID = ServiceOptions.getDefaultProjectId()
private val MOCK_PDF_FILE = Paths.get("src","main","resources", "example.pdf").toFile()

abstract class CompilerFleetMessageReceiver : MessageReceiver {
    override fun receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        val isProcessed = processMessage(message)

        if (isProcessed) {
            consumer.ack()
        }
    }

    abstract fun processMessage(message: PubsubMessage): Boolean
}

private val LOGGER = LoggerFactory.getLogger("TaskReceiver")

class TaskReceiver(tasksDirectory: String,
                   private val resultPublisher: Publisher
) : CompilerFleetMessageReceiver() {
    private val tasksDir: Path

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

    private fun compileProject(rootFileFullPath: String, zippedProject: ByteString): File {
        // TODO: Unzip project and compile via docker

        return MOCK_PDF_FILE
    }

    override fun processMessage(message: PubsubMessage): Boolean {
        val request = CompilerFleet.CompilerFleetRequest.parseFrom(message.data)
        val taskId = request.taskId
        val rootFileName = request.rootFileName
        val zippedProject = request.zipBytes
        val compiledPdf = compileProject(rootFileName, zippedProject)

        var isPublished = true
        val onPublishFailureCallback = {
            LOGGER.info("Publish $taskId failed with code ${StatusCode.FAILURE}")
            isPublished = false
        }

        val data = getResultData(taskId, StatusCode.SUCCESS, compiledPdf)
        resultPublisher.publish(data, onPublishFailureCallback)

        return isPublished
    }
}

fun subscribe(subscription: String, receiver: CompilerFleetMessageReceiver) {
    val subscriptionName = SubscriptionName.of(PROJECT_ID, subscription)
    val subscriber = Subscriber.newBuilder(subscriptionName, receiver).build()
    val onShutdown = CompletableFuture<Any>()
    Runtime.getRuntime().addShutdownHook(Thread(Runnable {
        onShutdown.complete(null)
    }))

    try {
        subscriber.startAsync().awaitRunning()
        onShutdown.get()
    } finally {
        subscriber.stopAsync()
    }
}

fun main(args: Array<String>) {
    val parsedArgs = ArgParser(args).parseInto(::SubscriberArgs)
    val subscriptionId = parsedArgs.subscription
    val tasksDir = parsedArgs.tasksDir
    val resultTopic = parsedArgs.resultTopic

    val publisher = Publisher(resultTopic)

    val taskReceiver = TaskReceiver(tasksDir, publisher)
    subscribe(subscriptionId, taskReceiver)
}