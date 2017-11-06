/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.signExtend
import nl.astraeus.konan.server.Server
import nl.astraeus.konan.server.Request
import nl.astraeus.konan.server.Response
import nl.astraeus.konan.server.buffer.Buffers
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.nrand48

class Controller {

    fun getData(request: Request, response: Response) {

    }
}

fun postData(request: Request, response: Response) {

}

fun fileHandler(request: Request, response: Response) {
    val filename = "web${request.uri}"
    println("reading file: $filename")

    val file = fopen(filename, "r")

    if (file == null) {
        response.sendError(404, "File not found")
    } else {
        val block = Buffers.claim()
        val pinned = block.data.pin()
        try {
            var nr: Long

            do {
                nr = fread(pinned.addressOf(0), block.data.size.signExtend(), 1, file)

                response.write(block.data, 0, nr.toInt())
            } while (nr > 0L)
        } finally {
            pinned.unpin()
            Buffers.free(block)
            fclose(file)
        }
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ./echo_server <port>")
        return
    }

    var port = args[0].toShort()
    val controller = Controller()
    var started = false

    while (!started) {
        try {
            val server = Server(port) {
                get("/hello") { _, response ->
                    response.write("<html><body><h1>Hello wörld!</h1></body></html>")
                }

                get("/pipo") { _, response ->
                    response.setHeader("content-type", "text/plain; charset=UTF-8")
                    response.write("Hello pipö!")
                }

                get("/*", ::fileHandler)

                post("/data", ::postData)

                post("/post", controller::getData)

                ws("/ws") {
                    onMessage { msg ->
                        println("Websocker received: $msg")
                    }
                }
            }

            println("Starting server on port: $port")
            server.start()
            started = true
        } catch (e: Error) {
            println("Failed to start server on port: $port")
            port++
        }
    }
}
