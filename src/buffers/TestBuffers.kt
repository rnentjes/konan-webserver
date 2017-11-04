import nl.astraeus.konan.server.buffer.Buffer

/**
 * User: rnentjes
 * Date: 1-11-17
 * Time: 12:59
 */

fun main(args: Array<String>) {
    println("Testing buffers....")

    val buffer1 = Buffer()
    val buffer2 = Buffer()

    buffer1.write(1)
    buffer2.write(2)
    buffer1.write(3)
    buffer2.write(4)
    buffer1.write(5)
    buffer2.write(6)
    buffer1.write(7)
    buffer2.write(8)
    buffer1.write(9)
    buffer2.write(10)
    buffer1.write(11)
    buffer2.write(12)
    buffer1.write(-1)
    buffer2.write(-2)
    buffer1.write(-3)
    buffer2.write(-4)
    buffer1.write(-5)
    buffer2.write(-6)
    buffer1.write(-7)
    buffer2.write(-8)

    while(buffer1.canRead()) {
        println("read buffer1 ${buffer1.read()}")
    }

    while(buffer2.canRead()) {
        println("read buffer2 ${buffer2.read()}")
    }
}
