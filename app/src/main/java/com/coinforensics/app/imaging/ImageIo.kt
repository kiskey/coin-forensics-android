package com.coinforensics.app.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ImageIo {
    fun decode(context: Context, uri: Uri, maxDimension: Int = 3200): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val largest = maxOf(w, h)
            if (largest > maxDimension) {
                val scale = maxDimension.toDouble() / largest
                decoder.setTargetSize(
                    maxOf(1, (w * scale).toInt()),
                    maxOf(1, (h * scale).toInt())
                )
            }
        }
    }

    fun createCaptureUri(context: Context): Uri {
        val file = File.createTempFile("coin_capture_", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
