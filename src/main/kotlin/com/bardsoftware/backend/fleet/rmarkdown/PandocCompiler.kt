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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.commons.text.StringSubstitutor
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val LOGGER = LoggerFactory.getLogger("Pandoc")
const val PANDOC_DEFAULT_FONT = "DejaVu Sans"
const val COMPILE_COMMAND_KEY = "pandoc.compile.command"
val DEFAULT_CONFIG = ConfigFactory.load()

private val pandocArguments = listOf("projectRootAbsPath", "workingDirRelPath",
        "inputFileName", "outputFileName", "mainFont")

class PandocArguments(
        projectRootAbsPath: Path,
        projTasks: Path,
        mainFile: Path,
        outputFile: Path,
        font: String = PANDOC_DEFAULT_FONT) {

    private val substitutor: StringSubstitutor

    init {
        val args = listOf(projectRootAbsPath.toString(), projTasks.toString(),
                mainFile.toString(), outputFile.toString(), font)
        val substitutions = (pandocArguments zip args).map { it.first to it.second }.toMap()
        substitutor = StringSubstitutor(substitutions)
    }

    fun getCommandLine(config: Config): String {
        val compileCommand = config.getString(COMPILE_COMMAND_KEY)

        return substitutor.replace(compileCommand)
    }

}

fun runCommandLine(commandLine: String): Int {
    LOGGER.debug("Running command line: {}", commandLine)
    val processBuilder = ProcessBuilder().command("/bin/bash", "-c", commandLine)

    val exitCode = processBuilder.start().waitFor()
    LOGGER.debug("Process completed, exit code={}", exitCode)

    return exitCode
}