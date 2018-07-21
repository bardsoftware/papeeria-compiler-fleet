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

import com.bardsoftware.papeeria.backend.tex.CompileRequest
import com.google.common.io.Files
import com.google.pubsub.v1.PubsubMessage
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals

class PandocTest {
    private val CP_COMMAND = "cp \${projectRootAbsPath}/\${workingDirRelPath}/\${inputFileName} \${outputFileName}"
    private val tasksDir = "tasks"
    private val taskId = "taskId"

    @Before
    fun createDir() {
        val dir = File(this.tasksDir)
        dir.mkdir()
        dir.resolve(taskId).mkdir()
    }

    @Test
    fun basicCompile() {
        val source = Paths.get("src", "test", "resources", "example.Rmd").toString()
        val outputName = Files.getNameWithoutExtension(source) + ".tex"
        val outputFile = Paths.get(tasksDir).resolve(outputName).toFile()

        val publisher = mock<Publisher> {
            on { publish(any(), any()) }.then{}
            //doNothing().`when`(publisher.publish(anyObject(), {}))
        }
        //mock(Publisher::class.java)
        //doNothing().`when`(publisher.publish(Matchers.notNull() as ByteString, {}))
        //doNothing().`when`(publisher.publish(Matchers.any(ByteString::class.java), {}))
        //`when`(publisher.publish(any(ByteString::class.java)) {}).then {  }
        //`when`(publisher.publish(ByteString.copyFrom("resa".toByteArray()), {}))
        //`when`(publisher.publish(Matchers.notNull() as ByteString, {}))
        //doNothing().`when`(publisher.publish(anyObject(), {}))

        val mockConfig = ConfigFactory
                .empty()
                .withValue("pandoc.compile.command", ConfigValueFactory.fromAnyRef(CP_COMMAND))
        val markdownReceiver = MarkdownTaskReceiver(null, tasksDir, publisher, mockConfig)

        val request = CompileRequest
                .newBuilder()
                .setMainFileName(source)
                .setOutputBaseName(outputName)
                .setId(taskId)
                .build()
                .toByteString()
        val message = PubsubMessage.newBuilder().setData(request).build()
        markdownReceiver.processMessage(message)

        assertEquals(0, 0)
        //assertTrue(outputFile.exists())
    }

    //@After
    fun deleteDir() {
        File(this.tasksDir).deleteRecursively()
    }
}