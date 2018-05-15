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

import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.common.io.ByteStreams
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.SubscriptionName
import com.xenomachina.argparser.ArgParser
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SubscriberArgs(parser: ArgParser) {
    val subscriberName by parser.storing(
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

private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

abstract class CompilerFleetMessageReceiver : MessageReceiver {
    override fun receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        processMessage(message)
        consumer.ack()
    }

    abstract fun processMessage(message: PubsubMessage)
}

private val TASK_LOGGER = Logger.getLogger("TaskReceiver")

internal class TaskReceiver(tasksDirectory: String,
                            resultTopic: String,
                            private val onMessageProcessed: (message: String, md5sum: String) -> Unit
) : CompilerFleetMessageReceiver() {
    private val dockerProcessor = DockerProcessor()
    private val resultPublisher = Publisher(resultTopic)
    private val tasksDir: Path

    init {
        val directoryPath = Paths.get(tasksDirectory)
        val directoryFile = directoryPath.toFile()
        val directoryName = directoryFile.name

        if (!directoryFile.exists()) {
            throw IOException("tasksDir directory(name is $directoryName) doesn't exists")
        }

        if (!directoryFile.isDirectory) {
            throw IOException("tasksDir directory(name is $directoryName) actually isn't a directory")
        }

        if (!directoryFile.canWrite()) {
            throw IOException("tasksDir directory(name is $directoryName) isn't writable")
        }

        this.tasksDir = directoryPath
    }

    override fun processMessage(message: PubsubMessage) {
        val request = CompilerFleet.CompilerFleetRequest.parseFrom(message.data)

        val taskId = request.taskId
        val destination = this.tasksDir.resolve(taskId).resolve("files")
        val zipStream = ZipInputStream(ByteArrayInputStream(request.zipBytes.toByteArray()))
        var entry: ZipEntry? = zipStream.nextEntry

        while (entry != null) {
            val filename = entry.name
            val newFile = destination.resolve(filename).toFile()

            if (!newFile.parentFile.mkdirs()) {
                val dirName = newFile.parentFile.name
                throw IOException("In task(id = $taskId): unable to create $dirName directory while unzipping")
            }

            FileOutputStream(newFile).use {
                ByteStreams.copy(zipStream, it)
            }

            entry = zipStream.nextEntry
        }

        val rootFileName = request.rootFileName
        val rootFile = destination.resolve(rootFileName).toFile()
        if (!rootFile.exists()) {
            throw IOException("In task(id = $taskId): path to root file doesn't exists")
        }

        val md5sum = dockerProcessor.getMd5Sum(rootFile)
        val statusCode = 0
        this.onMessageProcessed("md5 sum of root file", md5sum)

        val onPublishFailureCallback= {
            TASK_LOGGER.info("Publish failed: taskId = $taskId, status code = $statusCode, md5 sum: $md5sum")
        }

        val data = getResultData(taskId, statusCode, md5sum)
        resultPublisher.publish(data, onPublishFailureCallback)
    }
}

class SubscribeManager(subscriptionId: String,
                       private val receiver: CompilerFleetMessageReceiver) {
    private val subscriptionName = SubscriptionName.of(PROJECT_ID, subscriptionId)

    fun subscribe() {
        val subscriber = Subscriber.newBuilder(this.subscriptionName, this.receiver).build()
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

    fun pushMessage(message: PubsubMessage) {
        this.receiver.processMessage(message)
    }
}

fun main(args: Array<String>) {
    val parsedArgs = ArgParser(args).parseInto(::SubscriberArgs)
    val subscriptionId = parsedArgs.subscriberName
    val tasksDir = parsedArgs.tasksDir
    val resultTopic = parsedArgs.resultTopic

    val printerCallback = { message: String, md5sum: String? ->
        println("Data: $message")
        println("md5 sum: $md5sum")
    }

    val taskReceiver = TaskReceiver(tasksDir, resultTopic, printerCallback)
    SubscribeManager(subscriptionId, taskReceiver).subscribe()
}