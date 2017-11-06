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
    var responseCode = 200
    var responseMessage = "OK"

    fun reset() {
        status = ResponseStatus.WAITING_FOR_REQUEST
        bodyData.reset()
        headerData.reset()
        headers.clear()
        responseCode = 200
        responseMessage = "OK"
    }

    fun setHeader(name: String, value: String) {
        headers[name.toLowerCase().trim()] = value
    }

    fun generateHeaders() = if (responseCode == 200) {
        headerData.write("HTTP/1.1 200\r\n")
        headerData.write("OK\r\n")
        headerData.write("content-type: ${headers["content-type"] ?: "text/html; charset=UTF-8"}\r\n")
        headerData.write("content-length: ${bodyData.length}\r\n")
        for ((name, value) in headers) {
            if (name != "content-type" && name != "content-length") {
                headerData.write("$name: $value\r\n")
            }
        }
        headerData.write("\r\n")
    } else {
        bodyData.reset()
        bodyData.write(responseMessage)

        headerData.write("HTTP/1.1 $responseCode $responseMessage\r\n")
        headerData.write("content-type: text/plain; charset=UTF-8\r\n")
        headerData.write("content-length: ${bodyData.length}\r\n")
        headerData.write("\r\n")
    }

    fun sendError(code: Int, msg: String) {
        responseCode = code
        responseMessage = msg
    }

    fun write(bytes: ByteArray, offset: Int, length: Int) {
        bodyData.write(bytes, offset, length)
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
