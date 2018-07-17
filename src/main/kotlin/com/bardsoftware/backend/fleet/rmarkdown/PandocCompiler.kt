package com.bardsoftware.backend.fleet.rmarkdown

import org.slf4j.LoggerFactory
import java.nio.file.Path

val PANDOC_DEFAULT_VALUE = "pandoc"

private val LOGGER = LoggerFactory.getLogger("Pandoc")
val PANDOC_DEFAULT_FONT = "DejaVu Sans"

fun compile(pandocCompileCommand: String, mainFile: Path,
            outputFileName: Path, tasksDir: Path, font: String = PANDOC_DEFAULT_FONT) {
    val commandLine = if (pandocCompileCommand == PANDOC_DEFAULT_VALUE) {
        "$PANDOC_DEFAULT_VALUE $mainFile -o $outputFileName --latex-engine xelatex -s -V mainfont='$font' "
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