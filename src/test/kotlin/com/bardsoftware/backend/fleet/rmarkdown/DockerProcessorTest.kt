package com.bardsoftware.backend.fleet.rmarkdown

import org.junit.Assert.assertEquals
import org.junit.Test

class DockerTest {
    private val dockerProcessor = DockerProcessor()

    @Test
    fun testMessageSum() {
        val messages = listOf("test", "hello", "", "0123")
        val answers = listOf(
                "d8e8fca2dc0f896fd7cb4cb0031ba249  -\n",
                "b1946ac92492d2347c6235b4d2611184  -\n",
                "68b329da9893e34099c7d8ad5cb9c940  -\n",
                "e5870c1091c20ed693976546d23b4841  -\n")

        for ((index, message) in messages.withIndex()) {
            assertEquals(answers[index], dockerProcessor.getMd5Sum(messages[index]))
        }
    }
}