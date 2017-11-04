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
            when(request.method.toString()) {
                "GET" -> {
                    //val handler = server.routing.getGetHandler(request.uri)

                }
            }
            // match uri
            // call handler
            // write response
            println("Request status is DONE:")
            println("Request method: ${request.method}")
            println("Request uri: ${request.uri}")
            println("Request http version: ${request.httpVersion}")
            for((name, value) in request.headers) {
                println("Header [$name] -> [$value]")
            }
        }
    }

    fun reset() {
        request.reset()
        response.reset()
    }
}
