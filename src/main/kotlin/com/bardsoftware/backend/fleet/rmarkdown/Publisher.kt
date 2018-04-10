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

import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName

fun createMessage(message: String): PubsubMessage {
    val data = ByteString.copyFromUtf8(message)

    return PubsubMessage.newBuilder()
            .setData(data)
            .build()
}

class Publisher(private val topicName: String) {
    private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

    fun publish(message: String) {
        val topicId = this.topicName
        val serviceTopicName = TopicName.of(this.PROJECT_ID, topicId)
        var publisher: Publisher? = null

        try {
            publisher = Publisher.newBuilder(serviceTopicName).build()
            val pubsubMessage = createMessage(message)

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