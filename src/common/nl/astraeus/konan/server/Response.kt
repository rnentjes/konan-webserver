package nl.astraeus.konan.server

import nl.astraeus.konan.server.buffer.Buffer

enum class ResponseStatus {
    PENDING,
    WRITING,
    READY,
    DONE
}

class Response(
        var status: ResponseStatus = ResponseStatus.PENDING
) {
    val buffer: Buffer = Buffer()

    fun reset() {
        status = ResponseStatus.PENDING
        buffer.clear()
    }

    fun setHeader(name: String, value: String) {

    }

    fun write(str: String) {
        val array = kotlin.text.toUtf8Array(str, 0, str.length)

        for (value in array) {
            buffer.write(value)
        }
    }
}
