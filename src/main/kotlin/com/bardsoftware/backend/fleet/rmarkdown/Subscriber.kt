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
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.SubscriptionName
import com.xenomachina.argparser.ArgParser

class SubscriberArgs(parser: ArgParser) {
    val subscriberName by parser.storing(
            "--sub",
            help = "subscription topic name")
}

private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

internal class MessageReceiverExample(private val callback: (message: String, md5sum: String) -> Unit) : MessageReceiver {
    private val dockerProcessor = DockerProcessor()

    override fun receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        processMessage(message)
        consumer.ack()
    }

    fun processMessage(message: PubsubMessage) {
        val messageContent = message.data.toStringUtf8()
        val md5sum = dockerProcessor.getMd5Sum(messageContent)

        callback(messageContent, md5sum)
    }
}

class SubscribeManager(subscriptionId: String, callback: (message: String, md5sum: String) -> Unit) {
    private val subscriptionName = SubscriptionName.of(PROJECT_ID, subscriptionId)
    private val receiver = MessageReceiverExample(callback)

    fun subscribe() {
        val subscriber = Subscriber.newBuilder(subscriptionName, receiver).build()
        try {
            subscriber.startAsync().awaitRunning()
        } finally {
            subscriber?.stopAsync()
        }
    }

    fun pushMessage(message: String) {
        receiver.processMessage(createMessage(message))
    }
}

fun main(args: Array<String>) {
    val parsedArgs = ArgParser(args).parseInto(::SubscriberArgs)
    val subscriptionId = parsedArgs.subscriberName

    val printerCallback = { message: String, md5sum: String? ->
        println("Data: $message")
        println("md5 sum: $md5sum")
    }

    SubscribeManager(subscriptionId, printerCallback).subscribe()
}