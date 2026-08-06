package com.mdwiki.service.usecase

import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.ImportMdFileInput
import com.mdwiki.dto.ImportMdItemResult
import com.mdwiki.dto.ImportMdItemStatus
import com.mdwiki.dto.ImportMdPagesResponse
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.AppException
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.WikilinkService
import com.mdwiki.util.MdImportTitleResolver
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ImportMdPagesUseCase(
    private val pageRepository: PageRepository,
    private val createPageUseCase: CreatePageUseCase,
    private val updatePageUseCase: UpdatePageUseCase,
    private val wikilinkService: WikilinkService
) {
    fun execute(
        files: List<ImportMdFileInput>,
        folderId: UUID?,
        overwrite: Boolean,
        username: String
    ): ImportMdPagesResponse {
        val results = files.map { file -> importOne(file, folderId, overwrite, username) }
        return ImportMdPagesResponse(
            results = results,
            created = results.count { it.status == ImportMdItemStatus.CREATED },
            updated = results.count { it.status == ImportMdItemStatus.UPDATED },
            skipped = results.count { it.status == ImportMdItemStatus.SKIPPED },
            errors = results.count { it.status == ImportMdItemStatus.ERROR }
        )
    }

    private fun importOne(
        file: ImportMdFileInput,
        folderId: UUID?,
        overwrite: Boolean,
        username: String
    ): ImportMdItemResult {
        val filename = file.filename.trim()
        if (filename.isBlank()) {
            return ImportMdItemResult(
                filename = file.filename,
                status = ImportMdItemStatus.ERROR,
                message = "Filename is blank"
            )
        }
        if (!MdImportTitleResolver.isMarkdownFilename(filename)) {
            return ImportMdItemResult(
                filename = filename,
                status = ImportMdItemStatus.ERROR,
                message = "Only .md or .markdown files are supported"
            )
        }

        val stem = MdImportTitleResolver.filenameStem(filename)
        val slug = wikilinkService.normalizePageSlug(stem)
        if (slug.isBlank()) {
            return ImportMdItemResult(
                filename = filename,
                status = ImportMdItemStatus.ERROR,
                message = "Cannot derive a valid slug from filename"
            )
        }

        val title = MdImportTitleResolver.resolveTitle(file.contentMd, stem.ifBlank { slug })
        val active = pageRepository.findBySlugAndDeletedAtIsNull(slug)
        val existingAny = pageRepository.findBySlug(slug)

        return try {
            when {
                active != null && !overwrite -> ImportMdItemResult(
                    filename = filename,
                    slug = slug,
                    title = title,
                    status = ImportMdItemStatus.SKIPPED,
                    message = "Page with slug '$slug' already exists"
                )

                active != null && overwrite -> {
                    val updateRequest = UpdatePageRequest(
                        title = title,
                        contentMd = file.contentMd,
                        folderId = folderId,
                        clearFolder = if (folderId == null) true else null
                    )
                    val updated = updatePageUseCase.execute(slug, updateRequest, username)
                    ImportMdItemResult(
                        filename = filename,
                        slug = updated.slug,
                        title = updated.title,
                        status = ImportMdItemStatus.UPDATED
                    )
                }

                existingAny != null && existingAny.deletedAt != null -> ImportMdItemResult(
                    filename = filename,
                    slug = slug,
                    title = title,
                    status = ImportMdItemStatus.ERROR,
                    message = "Slug '$slug' is occupied by a page in trash"
                )

                else -> {
                    val created = createPageUseCase.execute(
                        CreatePageRequest(
                            slug = slug,
                            title = title,
                            contentMd = file.contentMd,
                            folderId = folderId
                        ),
                        username
                    )
                    ImportMdItemResult(
                        filename = filename,
                        slug = created.slug,
                        title = created.title,
                        status = ImportMdItemStatus.CREATED
                    )
                }
            }
        } catch (ex: AppException) {
            ImportMdItemResult(
                filename = filename,
                slug = slug,
                title = title,
                status = ImportMdItemStatus.ERROR,
                message = ex.message
            )
        } catch (ex: Exception) {
            ImportMdItemResult(
                filename = filename,
                slug = slug,
                title = title,
                status = ImportMdItemStatus.ERROR,
                message = ex.message ?: "Import failed"
            )
        }
    }
}
