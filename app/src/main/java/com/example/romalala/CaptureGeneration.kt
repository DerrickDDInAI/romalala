package com.example.romalala

import java.util.concurrent.atomic.AtomicLong

/** Invalidates OCR and queued UI work after resize, visibility changes, or shutdown. */
internal class CaptureGeneration {
    private val value = AtomicLong(0)
    fun snapshot(): Long = value.get()
    fun accepts(generation: Long): Boolean = generation >= 0 && value.get() == generation
    fun invalidate() { value.updateAndGet { if (it < 0) it else it + 1 } }
    fun close() { value.set(-1) }
}
