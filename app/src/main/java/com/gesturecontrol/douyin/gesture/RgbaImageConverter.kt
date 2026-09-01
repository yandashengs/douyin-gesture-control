package com.gesturecontrol.douyin.gesture

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

internal object RgbaImageConverter {

    fun copyPackedRgba(
        source: ByteBuffer,
        width: Int,
        height: Int,
        pixelStride: Int,
        rowStride: Int,
    ): ByteArray {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(pixelStride == RGBA_BYTES_PER_PIXEL) {
            "Expected RGBA pixel stride $RGBA_BYTES_PER_PIXEL, got $pixelStride"
        }

        val packedRowSize = width * RGBA_BYTES_PER_PIXEL
        require(rowStride >= packedRowSize) {
            "Row stride $rowStride is smaller than packed row size $packedRowSize"
        }

        val input = source.duplicate().apply { rewind() }
        val requiredBytes = (height - 1L) * rowStride + packedRowSize
        require(requiredBytes <= input.limit().toLong()) {
            "RGBA plane has ${input.limit()} bytes, requires $requiredBytes"
        }

        return ByteArray(packedRowSize * height).also { output ->
            repeat(height) { row ->
                input.position(row * rowStride)
                input.get(output, row * packedRowSize, packedRowSize)
            }
        }
    }

    fun toUprightBitmap(imageProxy: ImageProxy): Bitmap {
        val plane = imageProxy.planes.singleOrNull()
            ?: error("Expected one RGBA plane, got ${imageProxy.planes.size}")
        val packed = copyPackedRgba(
            source = plane.buffer,
            width = imageProxy.width,
            height = imageProxy.height,
            pixelStride = plane.pixelStride,
            rowStride = plane.rowStride,
        )
        val bitmap = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888,
        ).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(packed))
        }

        val rotation = imageProxy.imageInfo.rotationDegrees % 360
        if (rotation == 0) return bitmap

        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private const val RGBA_BYTES_PER_PIXEL = 4
}
