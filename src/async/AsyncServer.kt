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

import nl.astraeus.konan.server.Server
import nl.astraeus.konan.server.Request
import nl.astraeus.konan.server.Response

class Controller {

    fun getData(request: Request, response: Response) {

    }
}

fun postData(request: Request, response: Response) {

}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ./echo_server <port>")
        return
    }

    val port = args[0].toShort()
    val controller = Controller()

    val server = Server(port) {
        get("/") { _, response ->
            response.write("Hello world!")
        }

        post("/data", ::postData)

        post("/post", controller::getData)

        ws("/ws") {
            onMessage { msg ->
                println("Websocker received: $msg")
            }
        }
    }

    server.start()
}
