package com.gesturecontrol.douyin.gesture

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.ByteBuffer

class RgbaImageConverterTest {

    @Test
    fun copiesTightlyPackedRows() {
        val source = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            RgbaImageConverter.copyPackedRgba(source, 2, 1, 4, 8),
        )
    }

    @Test
    fun removesRowPadding() {
        val source = ByteBuffer.wrap(
            byteArrayOf(
                1, 2, 3, 4, 9, 9, 9, 9,
                5, 6, 7, 8, 9, 9, 9, 9,
            )
        )

        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            RgbaImageConverter.copyPackedRgba(source, 1, 2, 4, 8),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedPixelStride() {
        RgbaImageConverter.copyPackedRgba(ByteBuffer.allocate(4), 1, 1, 2, 4)
    }
}
