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
import org.apache.commons.io.FileUtils.readFileToString
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SubscriberArgs(parser: ArgParser) {
    val subscriberName by parser.storing(
            "--sub",
            help = "subscription topic name")

    val tasksDir by parser.storing(
            "--tasks-dir",
            help = "root of tasks content"
    )
}

private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

internal class TaskReceiver(private val tasksDir: Path,
                            private val callback: (message: String, md5sum: String) -> Unit
) : MessageReceiver {
    private val dockerProcessor = DockerProcessor()

    override fun receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        processMessage(message)
        consumer.ack()
    }

    fun processMessage(message: PubsubMessage) {
        val request = CompilerFleet.CompilerFleetRequest.parseFrom(message.data)

        val destination = this.tasksDir.resolve(request.taskId).resolve("files")
        val zipStream = ZipInputStream(ByteArrayInputStream(request.zipBytes.toByteArray()))
        var entry: ZipEntry? = zipStream.nextEntry

        while (entry != null) {
            val filename = entry.name
            val newFile = destination.resolve(filename).toFile()

            if (!newFile.parentFile.mkdirs()) {
                throw IOException("unable to create dirs while unzipping")
            }

            FileOutputStream(newFile).use {
                ByteStreams.copy(zipStream, it)
            }

            entry = zipStream.nextEntry
        }

        val rootFileName = request.rootFileName
        val rootFile = destination.resolve(rootFileName).toFile()
        if (!rootFile.exists()) {
            throw IOException("path to root doesn't exists")
        }

        this.callback("md5 sum of root file", dockerProcessor.getMd5Sum(rootFile))
    }
}

class SubscribeManager(tasksDir: String,
                       subscriptionId: String,
                       callback: (message: String, md5sum: String) -> Unit) {
    private val subscriptionName = SubscriptionName.of(PROJECT_ID, subscriptionId)
    private val receiver: TaskReceiver

    init {
        val directory = Paths.get(tasksDir)

        if (!directory.toFile().exists()) {
            throw IOException("tasksDir directory doesn't exists")
        }

        if (!directory.toFile().canWrite()) {
            throw IOException("tasksDir directory isn't writable")
        }

        this.receiver = TaskReceiver(directory, callback)
    }

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

    val printerCallback = { message: String, md5sum: String? ->
        println("Data: $message")
        println("md5 sum: $md5sum")
    }

    SubscribeManager(tasksDir, subscriptionId, printerCallback).subscribe()
}