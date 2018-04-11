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
import com.spotify.docker.client.DockerClient.LogsParam.stderr
import com.spotify.docker.client.DockerClient.LogsParam.stdout
import com.spotify.docker.client.messages.ContainerConfig
import java.nio.file.Path

class DockerProcessor {
    private val docker: DockerClient = DefaultDockerClient.fromEnv().build()

    fun getMd5Sum(filePath: Path): String {
        val dockerFilePath = "copiedFile"
        var containerId: String? = null

        try {
            val containerConfig = ContainerConfig.builder()
                    .image("busybox")
                    .cmd("sh", "-c", "md5sum", dockerFilePath)
                    .build()

            val creation = this.docker.createContainer(containerConfig)
            containerId = creation.id()
            this.docker.copyToContainer(filePath, containerId, dockerFilePath)
            this.docker.startContainer(containerId)

            return this.docker.logs(containerId, stdout(), stderr()).use({
                stream ->stream.readFully() ?: ""
            })
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