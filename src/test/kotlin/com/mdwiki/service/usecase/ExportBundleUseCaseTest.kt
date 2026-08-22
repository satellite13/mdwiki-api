package com.mdwiki.service.usecase

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mdwiki.dto.BundleExportRequest
import com.mdwiki.dto.BundleManifest
import com.mdwiki.dto.CollectedBundle
import com.mdwiki.dto.CollectedBundleAttachment
import com.mdwiki.dto.CollectedBundleFolder
import com.mdwiki.dto.CollectedBundlePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

@ExtendWith(MockitoExtension::class)
class ExportBundleUseCaseTest {

    @Mock private lateinit var collect: CollectBundleSelectionUseCase

    @TempDir lateinit var tempDir: Path

    private val mapper = jacksonObjectMapper()

    @Test
    fun `writes manifest pages and attachments into zip`() {
        val pic = tempDir.resolve("pic.png")
        Files.write(pic, byteArrayOf(1, 2, 3))
        whenever(collect.execute(any())).thenReturn(
            CollectedBundle(
                folders = listOf(CollectedBundleFolder(listOf("Book"), "Book")),
                pages = listOf(
                    CollectedBundlePage(
                        slug = "intro",
                        title = "Intro",
                        contentMd = "hi ![](/api/uploads/pic.png)",
                        folderPath = listOf("Book"),
                        attachmentRefs = listOf("pic.png")
                    )
                ),
                attachments = listOf(
                    CollectedBundleAttachment(
                        storedName = "pic.png",
                        originalName = "diagram.png",
                        contentType = "image/png",
                        sizeBytes = 3,
                        diskPath = pic,
                        referencedBy = listOf("intro")
                    )
                ),
                warnings = emptyList()
            )
        )

        val out = ByteArrayOutputStream()
        ExportBundleUseCase(collect, mapper).execute(BundleExportRequest(pageSlugs = listOf("intro")), out)

        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readAllBytes()
                entry = zip.nextEntry
            }
        }

        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("pages/intro.md"))
        assertTrue(entries.containsKey("attachments/pic.png"))
        assertEquals("hi ![](/api/uploads/pic.png)", entries["pages/intro.md"]!!.toString(Charsets.UTF_8))
        assertEquals(listOf<Byte>(1, 2, 3), entries["attachments/pic.png"]!!.toList())

        val manifest = mapper.readValue(entries["manifest.json"], BundleManifest::class.java)
        assertEquals(BundleManifest.FORMAT, manifest.format)
        assertEquals(1, manifest.version)
        assertEquals("intro", manifest.pages.single().slug)
        assertEquals("pic.png", manifest.attachments.single().storedName)
    }
}
