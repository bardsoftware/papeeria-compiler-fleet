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
import com.xenomachina.argparser.ArgParser
import org.slf4j.LoggerFactory
import java.io.File

class PublisherArgs(parser: ArgParser) {
    val directory by parser.storing(
            "--dir",
            help = "directory to be zipped")

    val publishTopic by parser.storing(
            "-t", "--publish-topic",
            help = "topic where zip will be published"
    )
}

fun getResultData(taskId: String, statusCode: StatusCode, resultBytes: ByteArray): ByteString {
    return CompilerFleet.CompilerFleetResult.newBuilder()
            .setTaskId(taskId)
            .setStatusCode(statusCode.ordinal)
            .setResultBytes(ByteString.copyFrom(resultBytes))
            .build()
            .toByteString()
}

private val LOGGER = LoggerFactory.getLogger("Publisher")

class Publisher(private val topicName: String) {
    private val pubsubPublisher: Publisher

    init {
        val projectId = ServiceOptions.getDefaultProjectId()
        val topicId = this.topicName
        val serviceTopicName = TopicName.of(projectId, topicId)

        this.pubsubPublisher = Publisher.newBuilder(serviceTopicName).build()
    }

    fun publish(data: ByteString, onFailureCallback: () -> Unit) {
        val pubsubMessage = PubsubMessage.newBuilder()
                .setData(data)
                .build()

        val future = pubsubPublisher.publish(pubsubMessage)

        ApiFutures.addCallback(future, object : ApiFutureCallback<String> {

            override fun onFailure(throwable: Throwable) {
                LOGGER.error("Failed on publish with message: {}", throwable.message, throwable)
                onFailureCallback()
            }

            override fun onSuccess(messageId: String) {
                LOGGER.info("successful published $messageId")
            }
        })
    }
}

fun main(args: Array<String>) {
    val parsedArgs = ArgParser(args).parseInto(::PublisherArgs)
    val directory = parsedArgs.directory
    val zippedData = zipDirectory(File(directory))
    val topic = parsedArgs.publishTopic

    val onFailureCallback = {
        // retry publish?
    }

    Publisher(topic).publish(ByteString.copyFrom(zippedData), onFailureCallback)
}