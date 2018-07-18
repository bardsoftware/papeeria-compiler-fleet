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
import org.apache.commons.lang.text.StrSubstitutor
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger("Pandoc")
const val PANDOC_DEFAULT_FONT = "DejaVu Sans"
val DEFAULT_CONFIG = ConfigFactory.load()
val pandocArguments = listOf("projectRootAbsPath", "workingDirRelPath",
        "inputFileName", "outputFileName", "mainFont")

fun compile(config: Config, substituteArguments: List<String> = pandocArguments, vararg args: String) {
    val compileCommand= config.getString("pandoc.compile.command")
    val substitutions = (substituteArguments zip args).map { it.first to it.second }.toMap()
    val substitutor = StrSubstitutor(substitutions)
    val commandLine = substitutor.replace(compileCommand)

    runCommandLine(commandLine)
}

private fun runCommandLine(commandLine: String): Int {
    LOGGER.debug("Running command line: {}", commandLine)
    val processBuilder = ProcessBuilder().command("/bin/bash", "-c", commandLine)

    val exitCode = processBuilder.start().waitFor()
    LOGGER.debug("Process completed, exit code={}", exitCode)

    return exitCode
}