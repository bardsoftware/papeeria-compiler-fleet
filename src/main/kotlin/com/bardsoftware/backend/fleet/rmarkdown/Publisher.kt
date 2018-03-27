package com.bardsoftware.backend.fleet.rmarkdown

import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.ApiException
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName

object PublisherExample {
    private const val topicName = "rmarkdown-tasks"

    // use the default project id
    private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

    /** Publish messages to a topic.
     * @param args topic name, number of messages
     */
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        // topic id, eg. "my-topic"
        val topicId = topicName
        val messageCount = 5
        val serviceTopicName = TopicName.of(PROJECT_ID, topicId)
        var publisher: Publisher? = null

        try {
            // Create a publisher instance with default settings bound to the topic
            publisher = Publisher.newBuilder(serviceTopicName).build()

            for (i in 0 until messageCount) {
                val message = "message-" + i

                // convert message to bytes
                val data = ByteString.copyFromUtf8(message)
                val pubsubMessage = PubsubMessage.newBuilder()
                        .setData(data)
                        .build()

                //schedule a message to be published, messages are automatically batched
                val future = publisher!!.publish(pubsubMessage)

                // add an asynchronous callback to handle success / failure
                ApiFutures.addCallback(future, object : ApiFutureCallback<String> {

                    override fun onFailure(throwable: Throwable) {
                        if (throwable is ApiException) {
                            // details on the API exception
                            println(throwable)
                            println(throwable.statusCode.code)
                            println(throwable.isRetryable)
                        }
                        println("Error publishing message : " + message)
                    }

                    override fun onSuccess(messageId: String) {
                        // Once published, returns server-assigned message ids (unique within the topic)
                        println("successful puplushed $messageId")
                    }
                })
            }
        } finally {
            if (publisher != null) {
                // When finished with the publisher, shutdown to free up resources.
                publisher.shutdown()
            }
        }
    }
}