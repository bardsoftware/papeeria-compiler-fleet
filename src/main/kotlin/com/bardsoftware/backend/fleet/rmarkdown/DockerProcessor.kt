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

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.nio.file.Paths


const val PDF_EXTENSION = ".pdf"

class DockerProcessor {
    private val docker: DockerClient = DefaultDockerClient.fromEnv().build()

    fun compileRmdToPdf(rootFile: File): File {
        var containerId:String? = null
        val fileName = rootFile.name
        val parentDir = rootFile.parent

        try {
            val hostConfig = HostConfig.builder()
                    .appendBinds("$parentDir:/manuscript")
                    .build()

            val containerConfig = ContainerConfig.builder()
                    .image("danielak/manuscribble:latest")
                    .cmd(fileName)
                    .hostConfig(hostConfig)
                    .build()

            val creation = this.docker.createContainer(containerConfig)
            containerId = creation.id()
            this.docker.startContainer(containerId)
            this.docker.waitContainer(containerId);

            val compiledRmd = FilenameUtils.removeExtension(fileName) + PDF_EXTENSION

            return Paths.get(parentDir).resolve(compiledRmd).toFile()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            containerId?.let {
                this.docker.stopContainer(it, 0)
                this.docker.removeContainer(it)
            }
        }

        throw DockerProcessorException()
    }
}

class DockerProcessorException: Exception()
