package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("CM_READER", "Cm Reader", "en")
internal class CmReader(context: MangaLoaderContext) : MadaraParser(context, MangaSource.CM_READER, "cmreader.info")
