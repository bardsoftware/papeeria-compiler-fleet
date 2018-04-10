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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PubsubTest {
    val tasksDir = "tasks"


    @Before
    fun createDir() {
        File(tasksDir).mkdir()
    }

    @Test
    fun processFileUnzipTest() {
        val message = "test message"
        val byteOutput = ByteArrayOutputStream()
        val output = ZipOutputStream(byteOutput)
        val entry = ZipEntry("mytext.txt")
        output.putNextEntry(entry)
        output.write(message.toByteArray())

        output.closeEntry()
        output.close()

        val zipBytes = ByteString.copyFrom(byteOutput.toByteArray())
        val rootFileName = "mytext.txt"
        val taskId = "testId"
        val request = CompilerFleet.CompilerFleetRequest.newBuilder()

        val byteOutputObj = ByteArrayOutputStream()
        request.setZipBytes(zipBytes)
                .setRootFileName(rootFileName)
                .setTaskId(taskId)
                .build().writeTo(byteOutputObj)

        val data = ByteString.copyFrom(byteOutputObj.toByteArray())
        val pubsubMessage = PubsubMessage.newBuilder()
                .setData(data)
                .build()

        val mockCallback = { acceptedMessage: String, acceptedMd5sum: String ->
            assertEquals("f11a425906289abf8cce1733622834c8  -\n", acceptedMd5sum)
        }

        val manager = SubscribeManager(tasksDir, "", mockCallback)
        manager.pushMessage(pubsubMessage)
    }

    @After
    fun deleteDir() {
        File(tasksDir).deleteRecursively()
    }
}