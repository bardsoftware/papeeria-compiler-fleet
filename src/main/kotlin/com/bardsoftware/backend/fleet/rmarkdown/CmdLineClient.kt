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

import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.xenomachina.argparser.ArgParser
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class PublisherArgs(parser: ArgParser) {
    val directory by parser.storing(
            "--dir",
            help = "directory to be zipped")

    val rootFileName by parser.storing(
            "-r", "--root-file",
            help = "name of root rmarkdown file")

    val publishTopic by parser.storing(
            "-t", "--publish-topic",
            help = "topic where zip will be published"
    )

    val resultSubscription by parser.storing(
            "--result-sub",
            help = "subscription where pdf will be obtained"
    )
}

fun getPublishData(zipBytes: ByteArray, rootFileName: String, taskId: String): ByteString {
    return CompilerFleet.CompilerFleetRequest.newBuilder()
            .setZipBytes(ByteString.copyFrom(zipBytes))
            .setRootFileName(rootFileName)
            .setTaskId(taskId)
            .build()
            .toByteString()
}

@Throws(IOException::class)
fun directoryExistingCheck(directory: File) {
    if (!directory.exists()) {
        throw IOException("tasksDir directory(name is $directory) doesn't exists")
    }

    if (!directory.isDirectory) {
        throw IOException("tasksDir directory(name is $directory) actually isn't a directory")
    }
}

fun zipDirectory(directory: File): ByteArray {
    directoryExistingCheck(directory)

    val byteOutput = ByteArrayOutputStream()
    val output = ZipOutputStream(byteOutput)
    for (file in directory.listFiles()) {
        val entry = ZipEntry(file.name)

        output.putNextEntry(entry)
        output.write(FileUtils.readFileToByteArray(file))
        output.closeEntry()
    }

    output.close()
    return byteOutput.toByteArray()
}

fun getTaskId(data: ByteArray): String {
    val messageDigest = MessageDigest.getInstance("SHA-1")

    return String(messageDigest.digest(data))
}

private val LOGGER = LoggerFactory.getLogger("ResultReceiver")

class ResultReceiver(
        private val outputFile: File,
        private val expectedTaskId: String
) : CompilerFleetMessageReceiver() {
    override fun processMessage(message: PubsubMessage): Boolean {
        val result = CompilerFleet.CompilerFleetResult.parseFrom(message.data).identifier

        if (result.taskId != expectedTaskId) {
            LOGGER.info("Task ids don't match: \nexpected:{}, \nactual:{}", expectedTaskId, result.taskId)
            return false
        }

        FileUtils.writeByteArrayToFile(outputFile, result.taskId.toByteArray())

        LOGGER.info("Result received and written into {}", outputFile.name)
        System.exit(0)
        return true
    }
}

fun publishTask(topic: String, rootFileName: String, zippedData: ByteArray): String {
    val taskId = getTaskId(rootFileName.toByteArray())
    val publishData = getPublishData(zippedData, rootFileName, taskId)

    Publisher(topic).publish(publishData, {})
    return taskId
}

fun main(args: Array<String>) {
    val parsedArgs = ArgParser(args).parseInto(::PublisherArgs)
    val directory = Paths.get(parsedArgs.directory)
    val zippedData = zipDirectory(directory.toFile())
    val topic = parsedArgs.publishTopic
    val rootFileName = parsedArgs.rootFileName

    val outputFile = File(FilenameUtils.removeExtension(rootFileName) + PDF_EXTENSION)
    val taskId = publishTask(topic, rootFileName, zippedData)
    subscribe(parsedArgs.resultSubscription, ResultReceiver(outputFile, taskId))
}