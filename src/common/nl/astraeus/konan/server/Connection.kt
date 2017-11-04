package nl.astraeus.konan.server

class Connection(
        val id: Int,
        val server: Server
) {
    val request: Request = Request()
    val response: Response = Response()

    fun handleRead() {
        request.handleRead()

        if (request.status == RequestStatus.DONE) {
            println("Request status is DONE:")
            println("Request method: ${request.method}")
            println("Request uri: ${request.uri}")
            println("Request http version: ${request.httpVersion}")
            for((name, value) in request.headers) {
                println("Header [$name] -> [$value]")
            }

            // match uri
            // call handler
            // write response
            when(request.method.toString()) {
                "GET" -> {
                    val routing = Routing()
                    server.routing.invoke(routing)

                    val handler = routing.getGetHandler(request.uri.toString())

                    handler(request, response)
                }
                else -> {
                    throw IllegalStateException("Don't know how to handle request method: ${request.method}")
                }
            }

            response.generateHeaders()
            response.status = ResponseStatus.WRITING_HEADERS
        }
    }

    fun reset() {
        request.reset()
        response.reset()
    }
}
