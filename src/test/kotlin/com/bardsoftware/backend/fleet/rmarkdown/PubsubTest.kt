package com.bardsoftware.backend.fleet.rmarkdown

import org.junit.Assert.assertEquals
import org.junit.Test

class PubsubTest {
    // Maybe, it's good to create topic only for tests
    private val testTopic = "rmarkdown-tasks"

    // Same for subscription
    private val testSubscription = "rmarkdown-compiler"

    private val publisher = Publisher(testTopic)
    private val SubscribeManager = SubscribeManager(testSubscription)

    @Test
    fun testSimpleMessage() {
        val message = "hello"
        val sum = "d41d8cd98f00b204e9800998ecf8427e  -\n"

        publisher.publish(message)

        val testCallback = { acceptedMessage: String, acceptedMd5sum: String? ->
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)

            SubscribeManager.shutdown()
        }

        SubscribeManager.subscribe(testCallback)
    }

    @Test
    fun testMultipleMessages() {
        val message = "hello"
        val sum = "d41d8cd98f00b204e9800998ecf8427e  -\n"

        publisher.publish(message)
        publisher.publish(message)
        publisher.publish(message)

        var messagesCount = 0
        val testCallback = { acceptedMessage: String, acceptedMd5sum: String? ->
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)

            messagesCount++
            if (messagesCount == 3) {
                SubscribeManager.shutdown()
            }
        }

        SubscribeManager.subscribe(testCallback)
    }
}