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

import org.apache.commons.io.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class DockerTest {
    private val dockerProcessor = DockerProcessor()

    @Test
    fun testMessageSum() {
        val file = createTempFile("test", ".txt")

        FileUtils.writeStringToFile(file, "hello message", Charset.defaultCharset())

        val actualSum = dockerProcessor.getMd5Sum(file)
        assertEquals("387d1f75a179d782a473cf21fb893e33  -\n", actualSum)
    }
}