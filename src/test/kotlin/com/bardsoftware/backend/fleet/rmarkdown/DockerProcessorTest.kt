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

        (answers zip messages).forEach {
            assertEquals(it.first, dockerProcessor.getMd5Sum(it.second))
        }
    }
}