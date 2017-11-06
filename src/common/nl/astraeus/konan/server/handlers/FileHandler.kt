package nl.astraeus.konan.server.handlers

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.signExtend
import nl.astraeus.konan.server.Request
import nl.astraeus.konan.server.Response
import nl.astraeus.konan.server.buffer.Buffers
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

private val mimeTypes = mapOf(
        "txt" to "text/plain",
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "eot" to "application/vnd.ms-fontobject",
        "otf" to "font/otf",
        "ttf" to "font/ttf",
        "ico" to "image/x-icon",
        "gif" to "image/gif",
        "jpeg" to "image/jpeg",
        "jpg" to "image/jpg",
        "png" to "image/png",
        "pdf" to "application/pdf",
        "svg" to "image/svg+xml",
        "tif" to "image/tiff",
        "tiff" to "image/tiff",
        "js" to "application/javascript",
        "ts" to "application/typescript",
        "json" to "application/json",
        "woff" to "font/woff",
        "woff2" to "font/woff2"
)

class FileHandler(val directory: String) {

    fun getExtension(txt: String): String {
        val index = txt.lastIndexOf('.')

        return if (index >= 0) {
            txt.substring(index+1)
        } else {
            ""
        }
    }

    fun handle(request: Request, response: Response) {
        val filename = "$directory${request.uri}"
        println("reading file: $filename")

        val file = fopen(filename, "r")

        if (file == null) {
            response.sendError(404, "File not found")
        } else {
            response.setHeader("content-type", "${mimeTypes[getExtension(filename)] ?: "text/plain"}; charset=UTF-8")

            try {
                var nr: Long

                do {
                    val bodyData = response.bodyData
                    val block = bodyData.currentWriteBlock(true)
                    val pinned = block.data.pin()

                    nr = fread(pinned.addressOf(block.index), 1, block.remaining().signExtend(), file)
                    block.bytesRead(nr.toInt())
                    bodyData.length += nr.toInt()

                    pinned.unpin()
                    //response.write(block.data, 0, nr.toInt())
                } while (nr > 0L)
            } finally {
                fclose(file)
            }
        }
    }

}

