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

import org.junit.Assert.assertEquals
import org.junit.Test

class PubsubTest {
    @Test
    fun testSimpleMessage() {
        val message = "hello"
        val sum = "b1946ac92492d2347c6235b4d2611184  -\n"

        val testCallback = { acceptedMessage: String, acceptedMd5sum: String? ->
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)
        }

        val manager = SubscribeManager("", testCallback)
        manager.pushMessage(message)
    }

    @Test
    fun testMultipleMessages() {
        val message = "hello"
        val sum = "b1946ac92492d2347c6235b4d2611184  -\n"

        var messagesCount = 0
        val testCallback = { acceptedMessage: String, acceptedMd5sum: String? ->
            messagesCount++
            assertEquals(message, acceptedMessage)
            assertEquals(sum, acceptedMd5sum)
        }

        val manager = SubscribeManager("", testCallback)
        manager.pushMessage(message)
        manager.pushMessage(message)
        manager.pushMessage(message)
        assertEquals(3, messagesCount)
    }
}