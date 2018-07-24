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

import com.bardsoftware.papeeria.backend.tex.CompileRequest
import com.bardsoftware.papeeria.backend.tex.CompileResponse
import com.bardsoftware.papeeria.backend.tex.TexbeGrpc
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.SubscriptionName
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.CompletableFuture


class SubscriberArgs(parser: ArgParser) {
    val subscription by parser.storing(
            "--sub",
            help = "subscription topic name")

    val resultTopic by parser.storing(
            "-r", "--result-topic",
            help = "result topic name"
    )

    val tasksDir by parser.storing(
            "--tasks-dir",
            help = "root of tasks content"
    )

    val texbeAddress by parser.storing(
            "--texbe-addr",
            help = "texbe address and port"
    )
}

enum class StatusCode {
    SUCCESS, FAILURE;
}

private val PROJECT_ID = ServiceOptions.getDefaultProjectId()

fun subscribe(subscription: String, receiver: CompilerFleetMessageReceiver) {
    val subscriptionName = SubscriptionName.of(PROJECT_ID, subscription)
    val subscriber = Subscriber.newBuilder(subscriptionName, receiver).build()
    val onShutdown = CompletableFuture<Any>()
    Runtime.getRuntime().addShutdownHook(Thread(Runnable {
        onShutdown.complete(null)
    }))

    try {
        subscriber.startAsync().awaitRunning()
        onShutdown.get()
    } finally {
        subscriber.stopAsync()
    }
}

interface CompilerApi {
    fun compile(request: CompileRequest): CompileResponse
}

class Compiler(address: String) : CompilerApi {
    val texbeCompilerStub: TexbeGrpc.TexbeBlockingStub

    init {
        val channel = ManagedChannelBuilder
                .forTarget(address)
                .usePlaintext(true)
                .build()
        texbeCompilerStub = TexbeGrpc.newBlockingStub(channel)
    }

    override fun compile(request: CompileRequest): CompileResponse {
        return texbeCompilerStub.compile(request)
    }

}

fun main(args: Array<String>) = mainBody {
    val parsedArgs = ArgParser(args).parseInto(::SubscriberArgs)
    val subscriptionId = parsedArgs.subscription
    val tasksDir = parsedArgs.tasksDir
    val resultTopic = parsedArgs.resultTopic

    val publisher = Publisher(resultTopic)
    val compiler = Compiler(parsedArgs.texbeAddress)
    val taskReceiver = MarkdownTaskReceiver(compiler, tasksDir, publisher)
    subscribe(subscriptionId, taskReceiver)
}