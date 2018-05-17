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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import kotlin.test.assertEquals

class PubsubTest {
    private val tasksDir = "tasks"
    private var rootFileName = "example.Rmd"

    @Before
    fun createDir() {
        File(this.tasksDir).mkdir()
    }

    @Test
    fun processFileUnzipTest() {
        val resourcesDirectory = Paths.get("src","test","resources").toFile()

        val zipBytes = ByteString.copyFrom(zipDirectory(resourcesDirectory))
        val taskId = "testId"
        val request = CompilerFleet.CompilerFleetRequest.newBuilder()

        val byteOutputObj = ByteArrayOutputStream()
        request.setZipBytes(zipBytes)
                .setRootFileName(rootFileName)
                .setTaskId(taskId)
                .build()
                .writeTo(byteOutputObj)

        val mockCallback = { _: String, _: String ->
        }

        val publisher = mock(Publisher::class.java)
        val taskReceiver = TaskReceiver(tasksDir, publisher, mockCallback)
        val manager = SubscribeManager("", taskReceiver)
        val rootFile = manager.pushMessage(taskId, rootFileName, zipBytes)
        assertEquals(rootFileName, rootFile.name)
    }

    @Test(expected = IOException::class)
    fun fileIsNotDirTest() {
        deleteDir()
        File(this.tasksDir).createNewFile()
        processFileUnzipTest()
    }

    @Test(expected = IOException::class)
    fun dirNotExistTest() {
        deleteDir()
        processFileUnzipTest()
    }

    @Test(expected = IOException::class)
    fun dirNotWritableTest() {
        File(this.tasksDir).setWritable(false)
        processFileUnzipTest()
    }

    @Test(expected = IOException::class)
    fun rootFileNameNotExist() {
        this.rootFileName = "another name"
        processFileUnzipTest()
    }

    @After
    fun deleteDir() {
        File(this.tasksDir).deleteRecursively()
    }
}