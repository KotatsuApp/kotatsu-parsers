package org.koitharu.kotatsu.parsers.site.ru.rulib

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@Broken
@MangaSourceParser("HENTAILIB", "HentaiLib", "ru", type = ContentType.HENTAI)
internal class HentaiLibParser(context: MangaLoaderContext) : LibSocialParser(
	context = context,
	source = MangaParserSource.HENTAILIB,
	siteId = 4,
	siteDomain = "hentailib.me",
)
