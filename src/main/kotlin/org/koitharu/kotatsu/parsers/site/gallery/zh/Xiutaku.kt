package org.koitharu.kotatsu.parsers.site.gallery.zh

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.site.gallery.GalleryParser

@MangaSourceParser("XIUTAKU", "Xiutaku", "zh", type = ContentType.OTHER)
internal class Xiutaku(context: MangaLoaderContext) :
    GalleryParser(context, MangaParserSource.XIUTAKU, "xiutaku.com") {
    
    override suspend fun getFilterOptions():
        MangaListFilterOptions = MangaListFilterOptions(availableTags = fetchTags())

}