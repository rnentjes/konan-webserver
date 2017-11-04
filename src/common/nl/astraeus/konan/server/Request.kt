package nl.astraeus.konan.server

import nl.astraeus.konan.server.buffer.Buffer

class NewlineBuffer {
    val buffer: ByteArray = ByteArray(4, { 0 })
    var writePointer: Int = 0

    fun write(value: Byte) {
        buffer[writePointer++] = value
        writePointer %= 4
    }

    fun hasSemiColon() = buffer[(writePointer - 1 + 4) % 4] == ':'.toByte()

    fun hasWhiteSpace() = buffer[(writePointer - 1 + 4) % 4] == ' '.toByte() ||
            buffer[(writePointer - 1 + 4) % 4] == '\t'.toByte()

    fun hasNewline() = buffer[(writePointer - 1 + 4) % 4] == '\n'.toByte() &&
            buffer[(writePointer - 2 + 4) % 4] == '\r'.toByte()

    fun hasDoubleNewline() = buffer[(writePointer - 1 + 4) % 4] == '\n'.toByte() &&
            buffer[(writePointer - 2 + 4) % 4] == '\r'.toByte() &&
            buffer[(writePointer - 3 + 4) % 4] == '\n'.toByte() &&
            buffer[(writePointer - 4 + 4) % 4] == '\r'.toByte()

}

enum class RequestStatus {
    READ_METHOD,
    READ_METHOD_URI,
    READ_METHOD_HTTP,
    READ_HEADER_NAME,
    READ_HEADER_VALUE,
    READ_BODY,
    READ_MULTI_FORM,
    DONE
}

class Request(
        var status: RequestStatus = RequestStatus.READ_METHOD
) {
    val buffer = Buffer()
    val newlineBuffer = NewlineBuffer()
    val headers: MutableMap<String, String> = HashMap()
    val method = StringBuilder()
    val uri = StringBuilder()
    val httpVersion = StringBuilder()
    var headerName = StringBuilder()
    var headerValue = StringBuilder()

    fun getRequestBlock() = buffer.currentWriteBlock(true)

    fun reset() {
        status = RequestStatus.READ_METHOD
        buffer.clear()
    }

    fun handleRead() {
        while (buffer.canRead() && status != RequestStatus.DONE) {
            val value = buffer.read()

            newlineBuffer.write(value.toByte())

            when (status) {
                RequestStatus.READ_METHOD -> {
                    when {
                        newlineBuffer.hasNewline() -> {
                            throw IllegalStateException("Unable to read method of request, no uri after the method.")
                        }
                        newlineBuffer.hasWhiteSpace() -> status = RequestStatus.READ_METHOD_URI
                        else -> method.append(value.toChar())
                    }
                }
                RequestStatus.READ_METHOD_URI -> {
                    when {
                        newlineBuffer.hasNewline() -> {
                            throw IllegalStateException("Unable to read uri of request, no uri found.")
                        }
                        newlineBuffer.hasWhiteSpace() -> {
                            if (uri.isEmpty()) {
                                throw IllegalStateException("Unable to read uri of request, no uri found.")
                            } else {
                                status = RequestStatus.READ_METHOD_HTTP
                            }
                        }
                        else -> uri.append(value.toChar())
                    }
                }
                RequestStatus.READ_METHOD_HTTP -> {
                    when {
                        newlineBuffer.hasNewline() -> {
                            if (httpVersion.isEmpty()) {
                                throw IllegalStateException("Unable to read http version of request, no version found.")
                            } else if (newlineBuffer.hasDoubleNewline()) {
                                status = RequestStatus.DONE
                            } else {
                                status = RequestStatus.READ_HEADER_NAME
                            }
                        }
                        else -> httpVersion.append(value.toChar())
                    }
                }
                RequestStatus.READ_HEADER_NAME -> {
                    when {
                        newlineBuffer.hasNewline() -> {
                            if (headerName.trim().isEmpty() && newlineBuffer.hasDoubleNewline()) {
                                status = RequestStatus.DONE
                            } else {
                                throw IllegalStateException("Unable to read http header, newline found before value [$headerName]-[${value}].")
                            }
                        }
                        newlineBuffer.hasSemiColon() -> {
                            status = RequestStatus.READ_HEADER_VALUE
                        }
                        else -> headerName.append(value.toChar())
                    }
                }
                RequestStatus.READ_HEADER_VALUE -> {
                    when {
                        newlineBuffer.hasNewline() -> {
                            headers[headerName.trim().toString()] = headerValue.trim().toString()
                            headerName = StringBuilder()
                            headerValue = StringBuilder()

                            status = RequestStatus.READ_HEADER_NAME
                        }
                        else -> headerValue.append(value.toChar())
                    }
                }
                RequestStatus.DONE -> {
                    println("DONE READING REQUEST HEADERS!")
                }
            }
        }
    }
}

