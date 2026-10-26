package com.example.romalala

import android.graphics.Bitmap
import java.nio.ByteBuffer

/** Copies visible RGBA pixels, without requiring trailing padding on the last row. */
internal object FrameBitmap {
    fun copyRgba(source: ByteBuffer, width: Int, height: Int, pixelStride: Int,
                 rowStride: Int, top: Int = 0, bottom: Int = height): Bitmap {
        require(width > 0 && height > 0 && top in 0 until bottom && bottom <= height)
        require(pixelStride == 4) { "Expected RGBA pixels" }
        val rowBytes = Math.multiplyExact(width, pixelStride)
        require(rowStride >= rowBytes)
        val offset = source.position()
        val end = offset.toLong() + (bottom - 1).toLong() * rowStride + rowBytes
        require(end <= source.limit()) { "Incomplete image plane" }
        val packed = ByteBuffer.allocateDirect(Math.multiplyExact(rowBytes, bottom - top))
        val input = source.duplicate()
        for (row in top until bottom) {
            input.limit(source.limit())
            input.position(offset + row * rowStride)
            input.limit(input.position() + rowBytes)
            packed.put(input)
        }
        packed.flip()
        val bitmap = Bitmap.createBitmap(width, bottom - top, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(packed)
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }
}
