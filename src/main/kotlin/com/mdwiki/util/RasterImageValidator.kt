package com.mdwiki.util

import com.mdwiki.error.AppException
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile

object RasterImageValidator {
    class UnsupportedImageType(message: String) :
        AppException("UNSUPPORTED_IMAGE_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, message)

    private val supported = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

    fun validate(file: MultipartFile) {
        val mime = file.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (mime !in supported) throw UnsupportedImageType("Only PNG, JPEG, GIF and WebP images are supported")
        val bytes = file.inputStream.use { it.readNBytes(12) }
        val matches = when (mime) {
            "image/png" -> bytes.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            "image/jpeg" -> bytes.startsWith(0xff, 0xd8, 0xff)
            "image/gif" -> String(bytes.take(6).toByteArray(), Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
            "image/webp" -> String(bytes.take(4).toByteArray(), Charsets.US_ASCII) == "RIFF" &&
                String(bytes.drop(8).take(4).toByteArray(), Charsets.US_ASCII) == "WEBP"
            else -> false
        }
        require(matches) { "Image content does not match declared media type" }
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { this[it].toInt() and 0xff == expected[it] }
}
