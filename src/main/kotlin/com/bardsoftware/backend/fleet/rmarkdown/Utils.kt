package com.bardsoftware.backend.fleet.rmarkdown

import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage

fun getRequestData(zipBytes: ByteArray, rootFileName: String, taskId: String): ByteString {
    return CompilerFleet.CompilerFleetRequest.newBuilder()
            .setZipBytes(ByteString.copyFrom(zipBytes))
            .setRootFileName(rootFileName)
            .setTaskId(taskId)
            .build()
            .toByteString()
}

class ResultReceiver() : CompilerFleetMessageReceiver() {
    override fun processMessage(message: PubsubMessage) {
        val result = CompilerFleet.CompilerFleetResult.parseFrom(message.data)

        println(result.taskId)
        println(String(result.resultBytes.toByteArray()))
    }
}