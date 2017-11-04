import kotlinx.cinterop.*
import platform.posix.*
import konan.worker.*
import kotlin.*

val response =  "HTTP/1.0 200\r\n" +
        "OK\r\n" +
        "content-type:text/html; charset=UTF-8\r\n" +
        "content-length: 9\r\n" +
        "my-stupid-header: Some header\r\n\r\n" +
        "Welcome!\n"

val responseBuffer = kotlin.text.toUtf8Array(response, 0, response.length)

data class ConnectionInfo(val fd: Int)
data class ConnectionResult(val fd: Int)

fun handleConnection(commFd: Int): ConnectionResult {
    val buffer = ByteArray(1024)

    try {
        println("$commFd - handleConnection")
        buffer.usePinned { pinned ->
            while (true) {
                println("$commFd - Read")
                val length = recv(commFd, pinned.addressOf(0), buffer.size.signExtend(), 0).toInt()
                        .ensureUnixCallResult("read") { it >= 0 }

                if (length == 0) {
                    break
                }

                println("TEST")
                println("$commFd - Received ($length): ")
                println(kotlin.text.fromUtf8Array(buffer, 0, length))

                println("$commFd - Write")
                send(commFd, responseBuffer.refTo(0), responseBuffer.size.signExtend(), 0)
                        .ensureUnixCallResult("write") { it >= 0 }

            }
        }
    } catch (e: Error) {
        println("Error in handleConnection: ${e.message}")
        e.printStackTrace()
    }

    println("$commFd - Done")

    return ConnectionResult(commFd)
}

fun main(args: Array<String>) {
    val port = 4567.toShort()

    init_sockets()

    memScoped {

        val serverAddr = alloc<sockaddr_in>()

        try {
            val listenFd = socket(AF_INET, SOCK_STREAM, 0)
                    .ensureUnixCallResult("socket") { it >= 0 }

            with(serverAddr) {
                memset(this.ptr, 0, sockaddr_in.size)
                sin_family = AF_INET.narrow()
                sin_port = posix_htons(port)
            }

            bind(listenFd, serverAddr.ptr.reinterpret(), sockaddr_in.size.toInt())
                    .ensureUnixCallResult("bind") { it == 0 }

            listen(listenFd, 10)
                    .ensureUnixCallResult("listen") { it == 0 }

            println("Starting server on port $port")

            while (true) {
                val commFd = accept(listenFd, null, null)
                        .ensureUnixCallResult("accept") { it >= 0 }

                val worker = startWorker()

                val future = worker.schedule(TransferMode.CHECKED, {
                    ConnectionInfo(commFd)
                }, { ci ->
                    handleConnection(ci.fd)
                })

                future.consume { result ->
                    println("${result.fd} - Consuming")
                    worker.requestTermination().consume { _ -> }
                }
            }
        } catch (e: Error) {
            println(e.message)
            e.printStackTrace()
        }
    }
}

inline fun Int.ensureUnixCallResult(op: String, predicate: (Int) -> Boolean): Int {
    if (!predicate(this)) {
        throw Error("$op: ${strerror(posix_errno())!!.toKString()}")
    }
    return this
}

inline fun Long.ensureUnixCallResult(op: String, predicate: (Long) -> Boolean): Long {
    if (!predicate(this)) {
        throw Error("$op: ${strerror(posix_errno())!!.toKString()}")
    }
    return this
}
