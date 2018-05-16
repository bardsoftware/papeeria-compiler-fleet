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

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.ContainerCreation
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Matchers.any
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.runners.MockitoJUnitRunner
import java.io.File


class DockerTest {

    @Test
    fun testDocker() {
        val docker = mock(DockerClient::class.java)
        val dockerProcessor = DockerProcessor(docker)

        val file = mock(File::class.java)
        `when`(file.name).thenReturn("name")
        `when`(file.path).thenReturn("path")
        `when`(file.parentFile).thenReturn(file)
        `when`(file.absolutePath).thenReturn("path")

        val creation = mock(ContainerCreation::class.java)
        `when`(creation.id()).thenReturn("id")


        `when`(docker.createContainer(any(ContainerConfig::class.java))).thenReturn(creation)

        dockerProcessor.compileRmdToPdf(file)

        verify(docker, times(1)).startContainer("id")
        verify(docker, times(1)).waitContainer("id")
    }
}
