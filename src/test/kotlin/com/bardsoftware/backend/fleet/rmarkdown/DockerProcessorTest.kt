package com.bardsoftware.backend.fleet.rmarkdown

import org.junit.Assert.assertEquals
import org.junit.Test

class DockerTest {
    private val dockerProcessor = DockerProcessor()

    @Test
    fun testMessageSum() {
        val messages = listOf("test", "hello", "", "0123")
        val strangeAnswer = "d41d8cd98f00b204e9800998ecf8427e  -\n"

        for (message in messages) {
            assertEquals(strangeAnswer, dockerProcessor.getMd5Sum(message))
        }
    }
}