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
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

class PandocTest {
    private val tasksDir = Paths.get("tasks")
    private var rootFileName = "example.Rmd"

    @Before
    fun createDir() {
        this.tasksDir.toFile().mkdir()
    }

    @Test
    fun basicCompile() {
        val markdown = Paths.get("src","test","resources",rootFileName)
        val outputName = Files.getNameWithoutExtension(markdown.toString()) + ".tex"

        compile(markdown, tasksDir.resolve(outputName), tasksDir, CP_VALUE)
        assertTrue(tasksDir.resolve(outputName).toFile().exists())
    }

    @After
    fun deleteDir() {
        this.tasksDir.toFile().deleteRecursively()
    }
}