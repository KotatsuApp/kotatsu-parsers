package org.koitharu.kotatsu.parsers.site.gallery.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.site.gallery.GalleryParser

@MangaSourceParser("KIUTAKU", "Kiutaku", type = ContentType.OTHER)
internal class Kiutaku(context: MangaLoaderContext) :
    GalleryParser(context, MangaParserSource.KIUTAKU, "kiutaku.com")