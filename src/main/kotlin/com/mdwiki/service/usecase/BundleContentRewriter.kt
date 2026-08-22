package com.mdwiki.service.usecase

import com.mdwiki.service.WikilinkService

object BundleContentRewriter {
    fun rewriteUploads(content: String, storedNameMap: Map<String, String>): String {
        if (storedNameMap.isEmpty()) return content
        return CollectBundleSelectionUseCase.UPLOAD_REF.replace(content) { match ->
            val old = match.groupValues[1].trim().trimStart('/')
            val mapped = storedNameMap[old] ?: return@replace match.value
            "/api/uploads/$mapped"
        }
    }

    fun rewriteSlugs(content: String, slugMap: Map<String, String>, wikilinks: WikilinkService): String {
        var result = content
        for ((from, to) in slugMap) {
            if (from == to) continue
            result = wikilinks.rewriteWikilinksReferencingNormalizedSlug(result, from, to)
            result = wikilinks.rewriteInternalPageLinks(result, from, to)
        }
        return result
    }
}
