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

import junit.framework.Assert.assertEquals
import org.junit.Test
import java.io.File

class DockerTest {
    private val dockerProcessor = DockerProcessor()

    @Test
    fun testCompile() {
        val rmarkdown = DockerTest::class.java.getResource("/rmarkdown-cv.Rmd")
        val compiledPdf = dockerProcessor.compileRmdToPdf(File(rmarkdown.file))
        assertEquals(250790436864, compiledPdf.totalSpace)
        compiledPdf.deleteOnExit()
    }
}