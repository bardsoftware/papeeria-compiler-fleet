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

import com.bardsoftware.backend.fleet.rmarkdown.CompilerFleet.CompilerFleetResult.Status.forNumber
import com.bardsoftware.papeeria.backend.tex.CompileRequest
import com.bardsoftware.papeeria.backend.tex.CompileResponse
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName
import org.slf4j.LoggerFactory

fun buildResultData(taskId: String, compiledBytes: ByteString, outputFileName: String, statusCode: Int): ByteString {
    return CompilerFleet.CompilerFleetResult.newBuilder()
            .setTaskId(taskId)
            .setResultBytes(compiledBytes)
            .setOutputFileName(outputFileName)
            .setStatusCode(forNumber(statusCode))
            .build()
            .toByteString()
}

fun buildResultData(request: CompileRequest, response: CompileResponse): ByteString {
    val engine = try {
        CompilerFleet.Engine.valueOf(request.engine.name)
    } catch (exception: IllegalArgumentException) {
        CompilerFleet.Engine.XELATEX
    }

    return CompilerFleet.CompilerFleetResult.newBuilder()
            .setTaskId(request.id)
            .setResultBytes(response.pdfFile)
            .setOutputFileName(request.outputBaseName)
            .setStatusCode(forNumber(response.status.ordinal))
            .setEngine(engine)
            .setUserId(request.userId)
            .setProjectId(request.projectId)
            .setMainFileId(request.mainFileId)
            .setEditSessionId(request.editSessionId)
            .setFlags(request.flags)
            .build()
            .toByteString()
}

private val LOGGER = LoggerFactory.getLogger("Publisher")

interface PublisherApi {
    fun publish(data: ByteString, onFailureCallback: () -> Unit)
}

class Publisher(private val topicName: String) : PublisherApi {
    private val pubsubPublisher: Publisher

    init {
        val projectId = ServiceOptions.getDefaultProjectId()
        val topicId = this.topicName
        val serviceTopicName = TopicName.of(projectId, topicId)

        this.pubsubPublisher = Publisher.newBuilder(serviceTopicName).build()
    }

    override fun publish(data: ByteString, onFailureCallback: () -> Unit) {
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