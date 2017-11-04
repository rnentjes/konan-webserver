package nl.astraeus.konan.server

import nl.astraeus.konan.server.buffer.Buffer

class Request(
  val buffer: Buffer = Buffer()
) {
    val headers: MutableMap<String, String> = HashMap()


}