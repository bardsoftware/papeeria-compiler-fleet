package com.bardsoftware.backend.fleet.rmarkdown

import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.ApiException
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName

class Publisher(private val topicName: String) {
    private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

    fun publish(message: String) {
        val topicId = topicName
        val serviceTopicName = TopicName.of(PROJECT_ID, topicId)
        var publisher: Publisher? = null

        try {
            publisher = Publisher.newBuilder(serviceTopicName).build()

            val data = ByteString.copyFromUtf8(message)
            val pubsubMessage = PubsubMessage.newBuilder()
                    .setData(data)
                    .build()

            val future = publisher.publish(pubsubMessage)

            ApiFutures.addCallback(future, object : ApiFutureCallback<String> {

                override fun onFailure(throwable: Throwable) {
                    println(throwable)
                    println("Error publishing message : $message")
                }

                override fun onSuccess(messageId: String) {
                    println("successful published $messageId")
                }
            })
        } finally {
            publisher?.shutdown()
        }
    }
}