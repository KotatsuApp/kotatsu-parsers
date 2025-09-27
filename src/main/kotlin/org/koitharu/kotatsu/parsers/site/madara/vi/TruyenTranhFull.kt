package org.koitharu.kotatsu.parsers.site.madara.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TRUYENTRANHFULL", "Truyện Tranh Full", "vi")
internal class TruyenTranhFull(context: MangaLoaderContext) :
    MadaraParser(context, MangaParserSource.TRUYENTRANHFULL, "truyentranhfull.net", 20) {
    override val listUrl = "truyen-tranh/"
    override val tagPrefix = "the-loai/"
    override val datePattern = "dd/MM/yyyy"
}
