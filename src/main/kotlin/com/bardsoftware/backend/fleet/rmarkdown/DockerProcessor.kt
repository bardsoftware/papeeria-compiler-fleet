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

class DockerProcessor {
    private val docker: DockerClient = DefaultDockerClient.fromEnv().build()

    fun getMd5Sum(message: String): String? {
        var containerId: String? = null

        try {
            val quotedMessage = "\"$message\""

            val containerConfig = ContainerConfig.builder()
                    .image("busybox")
                    .cmd("sh", "-c", "md5sum", "<<<", quotedMessage)
                    .build()

            val creation = docker.createContainer(containerConfig)
            containerId = creation.id()
            docker.startContainer(containerId)

            return docker.logs(containerId, DockerClient.LogsParam.stdout()).readFully()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            docker.stopContainer(containerId, 0)
            docker.removeContainer(containerId)
        }

        return null
    }
}