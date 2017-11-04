package nl.astraeus.konan.server.buffer

import kotlin.IllegalStateException

val BUFFER_COUNT: Int = 256
val BUFFER_SIZE: Int = 8192

class BufferBlock(
  var claimIndex: Int = 0,
  var data: ByteArray = ByteArray(0),
  var index: Int = 0,
  var length: Int = 0
) {
    fun canRead() = index < length

    fun canWrite() = data.isEmpty() || length < data.size

    fun isFull() = !data.isEmpty() && length == data.size

    fun read(): Int {
        if (canRead()) {
            return data[index++].toInt() and 0xff
        } else {
            return -1
        }
    }

    fun write(value: Int): Boolean {
        if (data.isEmpty()) {
            println("Allocating buffer!")
            data = ByteArray(BUFFER_SIZE)
        }

        if (canWrite()) {
            data[length++] = (value and 0xff).toByte()
            return true
        } else {
            return false
        }
    }
}

object Buffers {
    val claimList: Array<BufferBlock> = Array(BUFFER_COUNT, { BufferBlock() })
    var claimCount: Int = 0
    val freeList: Array<BufferBlock> = Array(BUFFER_COUNT, { BufferBlock() })
    var freeCount: Int = BUFFER_COUNT

    fun canClaim() = true

    fun claim(): BufferBlock {
        if (freeCount == 0) {
            val size = claimList.size
            val newSize = size * 2

            val newClaimList = Array(newSize, { BufferBlock() })
            val newFreeList = Array(newSize, { BufferBlock() })

            TODO("Grow buffer list when full.")
        }

        val buffer = freeList[--freeCount]
        buffer.claimIndex = claimCount
        claimList[claimCount++] = buffer

        return buffer
    }

    fun free(bufferBlock: BufferBlock) {
        freeList[freeCount++] = claimList[bufferBlock.claimIndex]
        claimList[bufferBlock.claimIndex] = claimList[claimCount-1]
        claimCount--
    }
}

class Buffer  {
    val blocks: MutableList<BufferBlock> = ArrayList()
    var currentReadBlock: Int = -1
    var currentWriteBlock: Int = -1

    fun currentReadBlock() = blocks[currentReadBlock]
    fun currentWriteBlock() = blocks[currentWriteBlock]

    fun canRead() = currentReadBlock >= 0 &&
      (currentReadBlock < blocks.size - 1 || (currentReadBlock < blocks.size && currentReadBlock().canRead()))

    fun canWrite() = currentWriteBlock == -1 || currentWriteBlock().canWrite() || Buffers.canClaim()

    fun read(): Int {
        while(!currentReadBlock().canRead() && currentReadBlock < blocks.size - 1) {
            currentReadBlock++
        }

        if (canRead()) {
            return currentReadBlock().read()
        } else {
            return -1
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
        } else {
            throw IllegalStateException("Unable to write to buffer!")
        }
    }

}
