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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.typesafe.config.Config
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertTrue

class PandocTest {
    private val CP_COMMAND = "cp \${projectRootAbsPath}/\${workingDirRelPath}/\${inputFileName} \${outputFileName}"
    private val CONFIG_KEY = "pandoc.compile.command"
    private val tasksDir = "tasks"
    private val taskId = "taskId"

    @Before
    fun createDir() {
        File(this.tasksDir).resolve(taskId).mkdirs()
    }

    @Test
    fun basicCompile() {
        val source = Paths.get("src", "test", "resources", "example.Rmd").toString()
        val outputName = Files.getNameWithoutExtension(source) + ".tex"
        val outputFile = Paths.get(tasksDir).resolve(taskId).resolve(outputName).toFile()

        val publisher = mock<Publisher> {
            on { publish(any(), any()) }.then{}
        }
        val mockConfig = mock<Config> {
            on { getString(CONFIG_KEY) }.thenReturn(CP_COMMAND)
        }
        val markdownReceiver = MarkdownTaskReceiver(null, tasksDir, publisher, mockConfig)

        val request = CompileRequest
                .newBuilder()
                .setMainFileName(source)
                .setOutputBaseName(outputName)
                .setId(taskId)
                .build()
                .toByteString()
        val message = PubsubMessage.newBuilder().setData(request).build()
        val isPublished = markdownReceiver.processMessage(message)

        verify(mockConfig, times(1)).getString(CONFIG_KEY)
        assertTrue(outputFile.exists())
        assertTrue(isPublished)
    }

    @After
    fun deleteDir() {
        File(this.tasksDir).deleteRecursively()
    }
}