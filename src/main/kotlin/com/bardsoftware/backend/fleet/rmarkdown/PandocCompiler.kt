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

import org.slf4j.LoggerFactory
import java.nio.file.Path

val PANDOC_DEFAULT_VALUE = "pandoc"

private val LOGGER = LoggerFactory.getLogger("Pandoc")
val PANDOC_DEFAULT_FONT = "DejaVu Sans"

fun compile(mainFile: Path, outputFileName: Path, tasksDir: Path, font: String = PANDOC_DEFAULT_FONT) {
    val pandocCompileCommand = config.getString("pandoc.compile.command")

    val commandLine = if (pandocCompileCommand == PANDOC_DEFAULT_VALUE) {
        "$PANDOC_DEFAULT_VALUE $mainFile -o $outputFileName --pdf-engine xelatex -s -V mainfont='$font' "
    } else {
        "$pandocCompileCommand \"\" $tasksDir $mainFile $outputFileName $font"
    }

    runCommandLine(commandLine)
}

private fun runCommandLine(commandLine: String): Int {
    LOGGER.debug("Running command line: {}", commandLine)
    val processBuilder = ProcessBuilder().command("/bin/bash", "-c", commandLine)

    processBuilder.start().let { p ->
        val exitCode = p.waitFor()
        LOGGER.debug("Process completed, exit code={}", exitCode)
        return exitCode
    }
}