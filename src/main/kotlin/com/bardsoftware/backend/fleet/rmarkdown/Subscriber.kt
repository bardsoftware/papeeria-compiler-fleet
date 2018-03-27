package com.bardsoftware.backend.fleet.rmarkdown

import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.SubscriptionName
import java.util.concurrent.LinkedBlockingDeque
import com.xenomachina.argparser.ArgParser

class SubscriberArgs(parser: ArgParser) {
    val subscriberName by parser.storing(
            "--sub",
            help = "subscription topic name")
}

private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

private val messages = LinkedBlockingDeque<PubsubMessage>()

internal class MessageReceiverExample : MessageReceiver {

    override fun receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        messages.offer(message)
        consumer.ack()
    }
}

fun main(args: Array<String>) {
    val parsedArgs = ArgParser(args).parseInto(::SubscriberArgs)
    val subscriptionId = parsedArgs.subscriberName
    val subscriptionName = SubscriptionName.of(PROJECT_ID, subscriptionId)

    var subscriber: Subscriber? = null
    try {
        subscriber = Subscriber.newBuilder(subscriptionName, MessageReceiverExample()).build()
        subscriber.startAsync().awaitRunning()
        while (true) {
            val message = messages.take()
            println("Message Id: " + message.messageId)
            println("Data: " + message.data.toStringUtf8())
        }
    } finally {
        subscriber?.stopAsync()
    }
}