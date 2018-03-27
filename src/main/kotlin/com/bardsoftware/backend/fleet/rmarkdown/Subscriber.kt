package com.bardsoftware.backend.fleet.rmarkdown

import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.SubscriptionName
import java.util.concurrent.LinkedBlockingDeque

object SubscriberExample {
    private const val subscriberName = "rmarkdown-compiler"

    // use the default project id
    private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

    private val messages = LinkedBlockingDeque<PubsubMessage>()

    internal class MessageReceiverExample : MessageReceiver {

        override fun receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
            messages.offer(message)
            consumer.ack()
        }
    }

    /** Receive messages over a subscription.  */
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        // set subscriber id, eg. my-sub
        val subscriptionId = subscriberName
        val subscriptionName = SubscriptionName.of(PROJECT_ID, subscriptionId)
        var subscriber: Subscriber? = null
        try {
            // create a subscriber bound to the asynchronous message receiver
            subscriber = Subscriber.newBuilder(subscriptionName, MessageReceiverExample()).build()
            subscriber!!.startAsync().awaitRunning()
            // Continue to listen to messages
            while (true) {
                val message = messages.take()
                println("Message Id: " + message.messageId)
                println("Data: " + message.data.toStringUtf8())
            }
        } finally {
            if (subscriber != null) {
                subscriber.stopAsync()
            }
        }
    }
}