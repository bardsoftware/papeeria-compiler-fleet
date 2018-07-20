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

import com.google.common.io.Files
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PandocTest {
    private val CP_COMMAND = "cp \${source} \${dest}"
    private val tasksDir = "tasks"

    @Before
    fun createDir() {
        File(this.tasksDir).mkdir()
    }

    @Test
    fun basicCompile() {
        val source = Paths.get("src", "test", "resources", "example.Rmd").toString()
        val outputName = Files.getNameWithoutExtension(source) + ".tex"
        val outputFile = Paths.get(tasksDir).resolve(outputName).toFile()

        val publisher = Mockito.mock(Publisher::class.java)
        val markdownReceiver = MarkdownTaskReceiver(null, tasksDir, publisher)

        val mockConfig = ConfigFactory
                .empty()
                .withValue("pandoc.compile.command", ConfigValueFactory.fromAnyRef(CP_COMMAND))
        val cpArguments = mapOf("source" to source, "dest" to outputFile.toString())
        val exitCode = markdownReceiver.convertMarkdown(cpArguments, mockConfig)

        assertEquals(0, exitCode)
        assertTrue(outputFile.exists())
    }

    @After
    fun deleteDir() {
        File(this.tasksDir).deleteRecursively()
    }
}