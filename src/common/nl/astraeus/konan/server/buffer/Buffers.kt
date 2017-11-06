package nl.astraeus.konan.server.buffer

import kotlin.IllegalStateException

val BUFFER_COUNT: Int = 1024
val BUFFER_SIZE: Int = 8192
val MINIMAL_REMAINING: Int = 64

class BufferBlock(
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
            // println("Buffer size: ${data.size}, remaining(): ${remaining()}, bytes read: $number, length: $length")
            throw IllegalStateException("To many bytes where read!?")
        }
    }

    fun allocate() {
        if (data.isEmpty()) {
            // println("Allocating buffer!")
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

    fun write(array: ByteArray, offset: Int, length: Int): Int {
        allocate()

        if (remaining() == 0) {
            return 0
        } else {
            val remaining = remaining()
            if (length <= remaining) {
                array.copyRangeTo(data, offset, offset + length, this.length)
                this.length += length

                return length
            } else {
                array.copyRangeTo(data, offset, offset + remaining, this.length)
                this.length += remaining

                return remaining
            }
        }
    }

    fun bytesConsumed(length: Int) {
        index += length
    }
}

object Buffers {
    val freeList: Array<BufferBlock?> = Array(BUFFER_COUNT, { null })
    var freeCount: Int = BUFFER_COUNT

    fun canClaim() = true

    fun claim(): BufferBlock {
        return synchronized(this) {
            if (freeCount == 0) {
                val size = freeList.size
                val newSize = size * 2

                val newFreeList = Array<BufferBlock?>(newSize, { null })

                TODO("Grow buffer list when full.")
            }

            val bufferBlock = freeList[--freeCount] ?: BufferBlock()
            bufferBlock.reset()

            println("CLAIM: free: $freeCount")

            bufferBlock
        }
    }

    fun free(bufferBlock: BufferBlock) {
        synchronized(this) {
            freeList[freeCount++] = bufferBlock
            bufferBlock.reset()

            println("FREE: free: $freeCount")
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
        var result = ConsumableBuffer(ByteArray(0), 0, 0)

        synchronized(this) {
            if (canRead()) {
                val crb = currentReadBlock()
                result = ConsumableBuffer(crb.data, crb.index, crb.length - crb.index)
                crb.bytesConsumed(crb.length - crb.index)
            }
        }

        return result
    }

    fun write(value: Byte) {
        val currentBlock = currentWriteBlock(true)

        if (currentBlock.canWrite()) {
            currentBlock.write(value.toInt() and 0xff)
            length++
        } else {
            throw IllegalStateException("Unable to write to buffer!")
        }
    }

    fun write(bytes: ByteArray, offset: Int, length: Int) {
        var remaining = length

        while(remaining > 0) {
            val currentBlock = currentWriteBlock(true)

            val written = currentBlock.write(bytes, offset, length)
            this.length += written
            remaining -= written
        }

    }

    fun write(bytes: ByteArray) {
        write(bytes, 0, bytes.size)
    }

    fun write(str: String) {
        val array = kotlin.text.toUtf8Array(str, 0, str.length)

        for (value in array) {
            write(value)
        }
    }

}
