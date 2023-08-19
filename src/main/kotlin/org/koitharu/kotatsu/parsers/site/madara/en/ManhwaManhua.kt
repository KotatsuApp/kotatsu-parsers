package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWAMANHUA", "Manhwa Manhua", "en")
internal class ManhwaManhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAMANHUA, "manhwamanhua.com")
