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

import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class PandocTest {

    @Test
    fun substituteTest() {
        val args = PandocArguments(
                Paths.get("tasks-dir"),
                Paths.get("working-dir"),
                Paths.get("input"),
                Paths.get("output"),
                "font")

        assertEquals("launch-pandoc \\tasks-dir \\working-dir \\input \\output \\font",
                args.getCommandLine(DEFAULT_CONFIG))
    }
}