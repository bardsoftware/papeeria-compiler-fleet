package com.bardsoftware.backend.fleet.rmarkdown

import org.junit.Assert.assertEquals
import org.junit.Test

class PubsubTest {
    private val publisher = Publisher("")
    private val SubscribeManager = SubscribeManager("")

    @Test
    fun testSimpleMessage() {
        val message = "hello"
        val sum = "b1946ac92492d2347c6235b4d2611184  -\n"

        val testCallback = { acceptedMessage: String, acceptedMd5sum: String? ->
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)
        }

        SubscribeManager.pushMessage(message)
        SubscribeManager.processMessage(testCallback)
    }

    @Test
    fun testMultipleMessages() {
        val message = "hello"
        val sum = "b1946ac92492d2347c6235b4d2611184  -\n"

        var messagesCount = 0
        val testCallback = { acceptedMessage: String, acceptedMd5sum: String? ->
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)

            messagesCount++
            if (messagesCount == 3) {
                SubscribeManager.shutdown()
            }
        }

        SubscribeManager.pushMessage(message)
        SubscribeManager.processMessage(testCallback)
    }
}