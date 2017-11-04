package nl.astraeus.konan.server

import nl.astraeus.konan.server.buffer.Buffer
import nl.astraeus.konan.server.buffer.ConsumableBuffer

enum class ResponseStatus {
    WAITING_FOR_REQUEST,
    WRITING_HEADERS,
    WRITING_BODY,
    DONE
}

class Response(
        var status: ResponseStatus = ResponseStatus.WAITING_FOR_REQUEST
) {
    val bodyData: Buffer = Buffer()
    val headerData: Buffer = Buffer()
    val headers: MutableMap<String, String> = HashMap()

    fun reset() {
        status = ResponseStatus.WAITING_FOR_REQUEST
        bodyData.reset()
    }

    fun setHeader(name: String, value: String) {

    }

    fun generateHeaders() {
        headerData.write("HTTP/1.1 200\r\n")
        headerData.write("OK\r\n")
        headerData.write("content-type:text/html; charset=UTF-8\r\n")
        headerData.write("content-length: ${bodyData.length}\r\n")
        headerData.write("\r\n")
    }

    fun sendError(code: Int, msg: String) {
        bodyData.reset()
        headerData.reset()

        // write error data
    }

    fun write(bytes: ByteArray) {
        bodyData.write(bytes)
    }

    fun write(str: String) {
        bodyData.write(str)
    }

    fun consumeHeaders(): ConsumableBuffer {
        val result = headerData.consume()

        if (result.length == 0) {
            status = ResponseStatus.WRITING_BODY
        }

        return result
    }

    fun consumeBody(): ConsumableBuffer {
        val result = bodyData.consume()

        if (result.length == 0) {
            status = ResponseStatus.DONE
        }

        return result
    }
}
