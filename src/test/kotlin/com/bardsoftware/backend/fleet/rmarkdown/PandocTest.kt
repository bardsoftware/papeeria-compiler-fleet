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