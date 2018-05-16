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
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.SubscriptionName
import com.xenomachina.argparser.ArgParser
import org.apache.commons.io.FileUtils
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

internal class TaskReceiver(tasksDirectory: String,
                            resultTopic: String,
                            private val callback: (message: String, filename: String) -> Unit
) : CompilerFleetMessageReceiver() {
    private val dockerProcessor = DockerProcessor(getDefaultDockerClient())
    private val resultPublisher = Publisher(resultTopic)
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

    override fun processMessage(message: PubsubMessage) {
        val request = CompilerFleet.CompilerFleetRequest.parseFrom(message.data)
        val taskId = request.taskId
        val rootFileName = request.rootFileName
        val zipBytes = request.zipBytes
        val rootFile = unzipCompileTask(taskId, rootFileName, zipBytes)
        val compiledPdf = dockerProcessor.compileRmdToPdf(rootFile)
        this.callback("Compiled pdf name: ", compiledPdf.name)

        val data = getResultData(taskId, 0, FileUtils.readFileToByteArray(compiledPdf))
        resultPublisher.publish(data)
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

    fun pushMessage(taskId: String, rootFileName: String, zipBytes: ByteString): File {
        return (this.receiver as TaskReceiver).unzipCompileTask(taskId, rootFileName, zipBytes)
    }
}

fun main(args: Array<String>) {
    val parsedArgs = ArgParser(args).parseInto(::SubscriberArgs)
    val subscriptionId = parsedArgs.subscriberName
    val tasksDir = parsedArgs.tasksDir
    val resultTopic = parsedArgs.resultTopic

    val printerCallback = { message: String, filename: String? ->
        println("$message: $filename")
    }

    val taskReceiver = TaskReceiver(tasksDir, resultTopic, printerCallback)
    SubscribeManager(subscriptionId, taskReceiver).subscribe()
}