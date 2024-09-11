package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken // Redirect to @hentai20
@MangaSourceParser("MANGA18H", "Manga18h", "en", ContentType.HENTAI)
internal class Manga18h(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGA18H, "manga18h.xyz", 20)
