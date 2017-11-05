package nl.astraeus.konan.server.buffer

import kotlin.IllegalStateException

val BUFFER_COUNT: Int = 1024
val BUFFER_SIZE: Int = 1024
val MINIMAL_REMAINING: Int = 64

class BufferBlock(
  var claimIndex: Int = 0,
  var data: ByteArray = ByteArray(0),
  var index: Int = 0,
  var length: Int = 0
) {
    fun canRead() = index < length

    fun canWrite() = data.isEmpty() || length < data.size

    fun remaining() = data.size - length

    fun reset() {
        index = 0
        length = 0
    }

    fun bytesRead(number: Int) {
        length += number

        if (length > data.size) {
            println("Buffer size: ${data.size}, remaining(): ${remaining()}, bytes read: $number, length: $length")
            throw IllegalStateException("To many bytes where read!?")
        }
    }

    fun allocate() {
        if (data.isEmpty()) {
            println("Allocating buffer!")
            data = ByteArray(BUFFER_SIZE)
        }
    }

    fun free() {
        if (length == 0) {
            data = ByteArray(0)
        }
    }

    fun read(): Int {
        if (canRead()) {
            return data[index++].toInt() and 0xff
        } else {
            return -1
        }
    }

    fun write(value: Int): Boolean {
        allocate()

        if (canWrite()) {
            data[length++] = (value and 0xff).toByte()
            return true
        } else {
            return false
        }
    }

    fun bytesConsumed(length: Int) {
        index += length
    }
}

object Buffers {
    val claimList: Array<BufferBlock?> = Array(BUFFER_COUNT, { null })
    var claimCount: Int = 0
    val freeList: Array<BufferBlock?> = Array(BUFFER_COUNT, { null })
    var freeCount: Int = BUFFER_COUNT

    fun canClaim() = true

    fun claim(): BufferBlock {
        return synchronized(this) {
            if (freeCount == 0) {
                val size = claimList.size
                val newSize = size * 2

                val newClaimList = Array<BufferBlock?>(newSize, { null })
                val newFreeList = Array<BufferBlock?>(newSize, { null })

                TODO("Grow buffer list when full.")
            }

            val buffer = freeList[--freeCount] ?: BufferBlock()
            buffer.claimIndex = claimCount
            claimList[claimCount++] = buffer

            println("CLAIM: claimed: $claimCount, free: $freeCount, claimIndex: ${buffer.claimIndex}")

            buffer.reset()
            buffer
        }
    }

    fun free(bufferBlock: BufferBlock) {
        synchronized(this) {
            freeList[freeCount++] = claimList[bufferBlock.claimIndex]
            claimList[bufferBlock.claimIndex] = claimList[claimCount - 1]
            claimCount--
            bufferBlock.reset()

            println("FREE: claimed: $claimCount, free: $freeCount, claimIndex: ${bufferBlock.claimIndex}")
        }
    }
}

data class ConsumableBuffer(
        val data: ByteArray,
        val offset: Int,
        val length: Int
)

class Buffer  {
    val blocks: MutableList<BufferBlock> = ArrayList()
    var currentReadBlock: Int = -1
    var currentWriteBlock: Int = -1
    var length: Int = 0

    fun reset() {
        for (block in blocks) {
            Buffers.free(block)
        }
        blocks.clear()
        currentReadBlock = -1
        currentWriteBlock = -1
        length = 0
    }

    fun currentReadBlock(): BufferBlock {
        if (currentWriteBlock == -1) {
            blocks.add(Buffers.claim())
            currentWriteBlock = 0
            currentReadBlock = 0
        }

        return blocks[currentReadBlock]
    }

    fun currentWriteBlock(allocate: Boolean = false): BufferBlock {
        if (currentWriteBlock == -1) {
            blocks.add(Buffers.claim())
            currentWriteBlock = 0
            currentReadBlock = 0
        } else if (!canWrite() || blocks[currentWriteBlock].remaining() < MINIMAL_REMAINING) {
            blocks.add(Buffers.claim())
            currentWriteBlock++
        }

        if (allocate) {
            blocks[currentWriteBlock].allocate()
        }

        return blocks[currentWriteBlock]
    }

    fun canRead(): Boolean {
        while (!currentReadBlock().canRead() && currentReadBlock < blocks.size - 1) {
            currentReadBlock++
        }

        return currentReadBlock().canRead()
    }

    fun canWrite() = currentWriteBlock == -1 || blocks[currentWriteBlock].canWrite() || Buffers.canClaim()

    fun read(): Int {
        if (canRead()) {
            return currentReadBlock().read()
        } else {
            return -1
        }
    }

    fun consume(): ConsumableBuffer {
        return if (canRead()) {
            val crb = currentReadBlock()
            val result = ConsumableBuffer(crb.data, crb.index, crb.length - crb.index)
            crb.bytesConsumed(crb.length - crb.index)
            result
        } else {
            ConsumableBuffer(ByteArray(0), 0, 0)
        }
    }

    fun write(value: Byte) {
        if (currentWriteBlock == -1) {
            blocks.add(Buffers.claim())
            currentWriteBlock = 0
            currentReadBlock = 0
        }

        if (!currentWriteBlock().canWrite()) {
            blocks.add(Buffers.claim())
            currentWriteBlock++
        }

        if (currentWriteBlock().canWrite()) {
            currentWriteBlock().write(value.toInt() and 0xff)
            length++
        } else {
            throw IllegalStateException("Unable to write to buffer!")
        }
    }

    fun write(bytes: ByteArray) {
        for (byte in bytes) {
            write(byte)
        }
    }

    fun write(str: String) {
        val array = kotlin.text.toUtf8Array(str, 0, str.length)

        for (value in array) {
            write(value)
        }
    }

}
