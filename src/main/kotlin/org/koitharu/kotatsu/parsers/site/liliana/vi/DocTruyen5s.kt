package org.koitharu.kotatsu.parsers.site.liliana.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.liliana.LilianaParser

@MangaSourceParser("DOCTRUYEN5S", "DocTruyen5s", "vi")
internal class DocTruyen5s(context: MangaLoaderContext) :
    LilianaParser(context, MangaParserSource.DOCTRUYEN5S, "dongmoe.com") {

    override suspend fun getAvailableTags() = super.getAvailableTags()
    override suspend fun getFilterOptions() = super.getFilterOptions()
    
}
