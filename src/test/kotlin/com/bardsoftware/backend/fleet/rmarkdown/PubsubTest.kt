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
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PubsubTest {
    //@Test
    fun testSimpleMessage() {
        val message = "hello"
        val sum = "b1946ac92492d2347c6235b4d2611184  -\n"

        val testCallback = { acceptedMessage: String, acceptedMd5sum: String ->
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)
        }

        val manager = SubscribeManager("", "", testCallback)
        //manager.pushMessage(message)
    }

    //@Test
    fun testMultipleMessages() {
        val message = "hello"
        val sum = "b1946ac92492d2347c6235b4d2611184  -\n"

        var messagesCount = 0
        val testCallback = { acceptedMessage: String, acceptedMd5sum: String ->
            messagesCount++
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)
        }

        val manager = SubscribeManager("", "", testCallback)
        //manager.pushMessage(message)
        //manager.pushMessage(message)
        //manager.pushMessage(message)
        assertEquals(3, messagesCount)
    }

    @Test
    fun processFileUnzipTest() {
        val tasksDir = "tasks"
        val folder = createTempDir(tasksDir)

        val mockCallback = { acceptedMessage: String, acceptedMd5sum: String -> }

        val message = "test message"
        val byteOutput = ByteArrayOutputStream()
        val output = ZipOutputStream(byteOutput)
        val entry = ZipEntry("mytext.txt")
        output.putNextEntry(entry)
        output.write(message.toByteArray())

        output.closeEntry()
        output.close()

        val zipBytes = ByteString.copyFrom(byteOutput.toByteArray())
        val rootFileName = "root_name"
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

        val manager = SubscribeManager(tasksDir, "", mockCallback)
        manager.pushMessage(pubsubMessage)
    }
}