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

import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import org.apache.commons.io.FileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun getRequestData(zipBytes: ByteArray, rootFileName: String, taskId: String): ByteString {
    return CompilerFleet.CompilerFleetRequest.newBuilder()
            .setZipBytes(ByteString.copyFrom(zipBytes))
            .setRootFileName(rootFileName)
            .setTaskId(taskId)
            .build()
            .toByteString()
}

@Throws(IOException::class)
fun directoryExistingCheck(directory: File) {
    if (!directory.exists()) {
        throw IOException("tasksDir directory(name is $directory) doesn't exists")
    }

    if (!directory.isDirectory) {
        throw IOException("tasksDir directory(name is $directory) actually isn't a directory")
    }
}

fun zipDirectory(directory: File): ByteArray {
    directoryExistingCheck(directory)

    val byteOutput = ByteArrayOutputStream()
    val output = ZipOutputStream(byteOutput)
    for (file in directory.listFiles()) {
        val entry = ZipEntry(file.name)

        output.putNextEntry(entry)
        output.write(FileUtils.readFileToByteArray(file))
        output.closeEntry()
    }

    output.close()
    return byteOutput.toByteArray()
}

class ResultReceiver() : CompilerFleetMessageReceiver() {
    override fun processMessage(message: PubsubMessage) {
        val result = CompilerFleet.CompilerFleetResult.parseFrom(message.data)

        println(result.taskId)
        println(String(result.resultBytes.toByteArray()))
    }
}