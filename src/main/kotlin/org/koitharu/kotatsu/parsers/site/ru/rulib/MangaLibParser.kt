package org.koitharu.kotatsu.parsers.site.ru.rulib

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("MANGALIB", "MangaLib", "ru")
internal class MangaLibParser(
	context: MangaLoaderContext,
) : LibSocialParser(
	context = context,
	source = MangaSource.MANGALIB,
	siteId = 1,
	siteDomain = "test-front.mangalib.me",
)
