package com.example.romalala

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameBitmapTest {
    private val firstRow = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1)
    private val secondRow = byteArrayOf(0, 0, -1, -1, -1, -1, -1, -1)

    @Test fun unpaddedFrameStaysUsableUntilOcrFinishes() {
        val bytes = firstRow + secondRow
        val bitmap = FrameBitmap.copyRgba(ByteBuffer.wrap(bytes), 2, 2, 4, 8)
        assertFalse(bitmap.isRecycled)
        val result = ByteBuffer.allocate(16)
        bitmap.copyPixelsToBuffer(result)
        assertArrayEquals(bytes, result.array())
        bitmap.recycle()
    }

    @Test fun paddedRowsDoNotRequirePaddingAfterLastPixel() {
        val source = ByteBuffer.wrap(firstRow + byteArrayOf(9, 9, 9, 9) + secondRow)
        val bitmap = FrameBitmap.copyRgba(source, 2, 2, 4, 12)
        val result = ByteBuffer.allocate(16)
        bitmap.copyPixelsToBuffer(result)
        assertArrayEquals(firstRow + secondRow, result.array())
        assertEquals(0, source.position())
        bitmap.recycle()
    }

    @Test fun cropUsesOriginalStrideAndBufferPosition() {
        val source = ByteBuffer.wrap(byteArrayOf(42, 42) + firstRow + byteArrayOf(9, 9, 9, 9) + secondRow)
        source.position(2)
        val bitmap = FrameBitmap.copyRgba(source, 2, 2, 4, 12, top = 1, bottom = 2)
        assertEquals(1, bitmap.height)
        val result = ByteBuffer.allocate(8)
        bitmap.copyPixelsToBuffer(result)
        assertArrayEquals(secondRow, result.array())
        assertEquals(2, source.position())
        bitmap.recycle()
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteFrameIsRejected() {
        FrameBitmap.copyRgba(ByteBuffer.allocate(15), 2, 2, 4, 8)
    }
}
