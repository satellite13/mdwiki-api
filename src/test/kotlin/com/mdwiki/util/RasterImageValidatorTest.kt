package com.mdwiki.util

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.http.HttpStatus

class RasterImageValidatorTest {
    @Test
    fun `accepts supported raster magic signatures`() {
        val png = MockMultipartFile("file", "x.png", "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        val jpeg = MockMultipartFile("file", "x.jpg", "image/jpeg",
            byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00))
        val gif = MockMultipartFile("file", "x.gif", "image/gif", "GIF89a".toByteArray())
        val webp = MockMultipartFile("file", "x.webp", "image/webp",
            "RIFF1234WEBP".toByteArray())

        listOf(png, jpeg, gif, webp).forEach {
            assertThatCode { RasterImageValidator.validate(it) }.doesNotThrowAnyException()
        }
    }

    @Test
    fun `rejects unsupported mime and mismatched magic`() {
        val text = MockMultipartFile("file", "x.txt", "text/plain", "hello".toByteArray())
        val fakePng = MockMultipartFile("file", "x.png", "image/png", "not-png".toByteArray())

        val unsupported = org.junit.jupiter.api.assertThrows<RasterImageValidator.UnsupportedImageType> {
            RasterImageValidator.validate(text)
        }
        org.assertj.core.api.Assertions.assertThat(unsupported.status).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        assertThatThrownBy { RasterImageValidator.validate(fakePng) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
