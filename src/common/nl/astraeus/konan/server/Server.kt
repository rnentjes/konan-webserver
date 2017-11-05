package nl.astraeus.konan.server

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn
import kotlin.coroutines.experimental.startCoroutine

interface Websocket {

    fun onOpen(method: () -> Unit)

    fun onMessage(method: (String) -> Unit)

    fun onBinaryMessage(method: (ByteArray) -> Unit)

    fun onError(method: (Int) -> Unit)

    fun onClose(method: () -> Unit)
}

class WebsocketDSLWrapper {
    var onOpen: () -> Unit = {}
    var onMessage: (String) -> Unit = {}
    var onBinaryMessage: (ByteArray) -> Unit = {}
    var onError: (Int) -> Unit = {}
    var onClose: () -> Unit = {}

    fun onOpen(method: () -> Unit) {
        onOpen = method
    }

    fun onMessage(method: (String) -> Unit) {
        onMessage = method
    }

    fun onBinaryMessage(method: (ByteArray) -> Unit) {
        onBinaryMessage = method
    }

    fun onError(method: (Int) -> Unit) {
        onError = method
    }

    fun onClose(method: () -> Unit) {
        onClose = method
    }
}

class Routing {
    val getHandlers: MutableMap<String, (Request, Response) -> Unit> = HashMap()
    val postHandlers: MutableMap<String, (Request, Response) -> Unit> = HashMap()
    val wsWrappers: MutableMap<String, WebsocketDSLWrapper.() -> Unit> = HashMap()
    val wsHandlers: MutableMap<String, Websocket> = HashMap()

    fun get(uri: String, handler: (request: Request, response: Response) -> Unit) {
        getHandlers[uri] = handler
    }

    fun post(uri: String, handler: (request: Request, response: Response) -> Unit) {
        postHandlers[uri] = handler
    }

    fun ws(uri: String, ws: WebsocketDSLWrapper.() -> Unit) {
        wsWrappers[uri] = ws
    }

    fun ws(uri: String, ws: Websocket) {
        wsHandlers[uri] = ws
    }

    fun getGetHandler(uri: String): (Request, Response) -> Unit {
        return getHandlers[uri] ?: { _, response ->
            response.sendError(404, "Page not found")
        }
    }
}

class Server(
        val port: Short,
        val routing: Routing.() -> Unit
) {

    fun start() {
        memScoped {
            val serverAddr = alloc<sockaddr_in>()

            val listenFd = socket(AF_INET, SOCK_STREAM, 0)
                    .ensureUnixCallResult { it >= 0 }

            with(serverAddr) {
                memset(this.ptr, 0, sockaddr_in.size)
                sin_family = AF_INET.narrow()
                sin_addr.s_addr = posix_htons(0).toInt()
                sin_port = posix_htons(port)
            }

            bind(listenFd, serverAddr.ptr.reinterpret(), sockaddr_in.size.toInt())
                    .ensureUnixCallResult { it == 0 }

            fcntl(listenFd, F_SETFL, O_NONBLOCK)
                    .ensureUnixCallResult { it == 0 }

            listen(listenFd, 10)
                    .ensureUnixCallResult { it == 0 }

            var connectionId = 0
            acceptClientsAndRun(listenFd) {
                val connectionIdString = "#${++connectionId} - request handled.".cstr
                val pinnedBytes = connectionIdString.getBytes().pin()

                val connection = Connection(connectionId, this@Server)

                try {
                    while (true) {
                        if (connection.request.status != RequestStatus.DONE) {
                            val currentBlock = connection.request.getRequestBlock()
                            println("#$connectionId remaining in current block: ${currentBlock.remaining()}")

                            val bufferTest = currentBlock.data
                            val pinned = bufferTest.pin()

                            try {
                                val length = read(pinned.addressOf(currentBlock.index), currentBlock.remaining().signExtend())
                                currentBlock.bytesRead(length.toInt())

                                if (length == 0L) {
                                    connection.reset()
                                    break
                                }

                                connection.handleRead()
                            } finally {
                                pinned.unpin()
                            }
                        }

                        if (connection.response.status == ResponseStatus.WRITING_HEADERS) {
                            val data = connection.response.consumeHeaders()

                            if (data.length > 0) {
                                val pinnedData = data.data.pin()

                                try {
                                    write(pinnedData.addressOf(data.offset), data.length.signExtend())
                                } finally {
                                    pinnedData.unpin()
                                }
                            }
                        } else if (connection.response.status == ResponseStatus.WRITING_BODY) {
                            val data = connection.response.consumeBody()

                            if (data.length > 0) {
                                val pinnedData = data.data.pin()

                                try {
                                    write(pinnedData.addressOf(data.offset), data.length.signExtend())
                                } finally {
                                    pinnedData.unpin()
                                }
                            }
                        }

                        if (connection.response.status == ResponseStatus.DONE) {
                            connection.reset()
                        }
                    }
                } catch (e: IOException) {
                    println("I/O error occured: ${e.message}")
                } finally {
                    pinnedBytes.unpin()

                    connection.reset()
                }
            }
        }
    }

    fun acceptClientsAndRun(
            serverFd: Int,
            block: suspend Client.() -> Unit
    ) {
        memScoped {
            val waitingList = mutableMapOf<Int, WaitingFor>(serverFd to WaitingFor.Accept())
            val readfds = alloc<fd_set>()
            val writefds = alloc<fd_set>()
            val errorfds = alloc<fd_set>()
            var maxfd = serverFd
            while (true) {
                posix_FD_ZERO(readfds.ptr)
                posix_FD_ZERO(writefds.ptr)
                posix_FD_ZERO(errorfds.ptr)
                for ((socketFd, watingFor) in waitingList) {
                    when (watingFor) {
                        is WaitingFor.Accept -> posix_FD_SET(socketFd, readfds.ptr)
                        is WaitingFor.Read   -> posix_FD_SET(socketFd, readfds.ptr)
                        is WaitingFor.Write  -> posix_FD_SET(socketFd, writefds.ptr)
                    }
                    posix_FD_SET(socketFd, errorfds.ptr)
                }
                pselect(maxfd + 1, readfds.ptr, writefds.ptr, errorfds.ptr, null, null)
                        .ensureUnixCallResult { it >= 0 }
                loop@for (socketFd in 0..maxfd) {
                    val waitingFor = waitingList[socketFd]
                    val errorOccured = posix_FD_ISSET(socketFd, errorfds.ptr) != 0
                    if (posix_FD_ISSET(socketFd, readfds.ptr) != 0
                            || posix_FD_ISSET(socketFd, writefds.ptr) != 0
                            || errorOccured) {
                        when (waitingFor) {
                            is WaitingFor.Accept -> {
                                if (errorOccured)
                                    throw Error("Socket has been closed externally")

                                // Accept new client.
                                val clientFd = accept(serverFd, null, null)
                                if (clientFd < 0) {
                                    if (posix_errno() != EWOULDBLOCK)
                                        throw Error(getUnixError())
                                    break@loop
                                }
                                fcntl(clientFd, F_SETFL, O_NONBLOCK)
                                        .ensureUnixCallResult { it == 0 }
                                if (maxfd < clientFd)
                                    maxfd = clientFd
                                block.startCoroutine(Client(clientFd, waitingList), EmptyContinuation)
                            }
                            is WaitingFor.Read -> {
                                if (errorOccured)
                                    waitingFor.continuation.resumeWithException(IOException("Connection was closed by peer"))

                                // Resume reading operation.
                                waitingList.remove(socketFd)
                                val length = read(socketFd, waitingFor.data, waitingFor.length)
                                if (length < 0) // Read error.
                                    waitingFor.continuation.resumeWithException(IOException(getUnixError()))
                                waitingFor.continuation.resume(length)
                            }
                            is WaitingFor.Write -> {
                                if (errorOccured)
                                    waitingFor.continuation.resumeWithException(IOException("Connection was closed by peer"))

                                // Resume writing operation.
                                waitingList.remove(socketFd)
                                val written = write(socketFd, waitingFor.data, waitingFor.length)
                                if (written < 0) // Write error.
                                    waitingFor.continuation.resumeWithException(IOException(getUnixError()))
                                waitingFor.continuation.resume(Unit)
                            }
                        }
                    }
                }
            }
        }
    }

}

sealed class WaitingFor {
    class Accept : WaitingFor()

    class Read(val data: CArrayPointer<ByteVar>,
               val length: Long,
               val continuation: Continuation<Long>) : WaitingFor()

    class Write(val data: CArrayPointer<ByteVar>,
                val length: Long,
                val continuation: Continuation<Unit>) : WaitingFor()
}

class Client(
        val clientFd: Int,
        val waitingList: MutableMap<Int, WaitingFor>
) {
    suspend fun read(data: CArrayPointer<ByteVar>, dataLength: Long): Long {
        val length = read(clientFd, data, dataLength)
        if (length >= 0)
            return length
        if (posix_errno() != EWOULDBLOCK)
            throw IOException(getUnixError())
        // Save continuation and suspend.
        return suspendCoroutineOrReturn { continuation ->
            waitingList.put(clientFd, WaitingFor.Read(data, dataLength, continuation))
            COROUTINE_SUSPENDED
        }
    }

    suspend fun write(data: CArrayPointer<ByteVar>, length: Long) {
        val written = write(clientFd, data, length)
        if (written >= 0)
            return
        if (posix_errno() != EWOULDBLOCK)
            throw IOException(getUnixError())
        // Save continuation and suspend.
        return suspendCoroutineOrReturn { continuation ->
            waitingList.put(clientFd, WaitingFor.Write(data, length, continuation))
            COROUTINE_SUSPENDED
        }
    }
}

open class EmptyContinuation(override val context: CoroutineContext = EmptyCoroutineContext) : Continuation<Any?> {
    companion object : EmptyContinuation()
    override fun resume(value: Any?) {}
    override fun resumeWithException(exception: Throwable) { throw exception }
}

class IOException(message: String): RuntimeException(message)

fun getUnixError() = strerror(posix_errno())!!.toKString()

inline fun Int.ensureUnixCallResult(predicate: (Int) -> Boolean): Int {
    if (!predicate(this)) {
        throw Error(getUnixError())
    }
    return this
}

inline fun Long.ensureUnixCallResult(predicate: (Long) -> Boolean): Long {
    if (!predicate(this)) {
        throw Error(getUnixError())
    }
    return this
}
