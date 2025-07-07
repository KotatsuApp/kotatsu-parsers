package org.koitharu.kotatsu.parsers.site.ru.rulib

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@MangaSourceParser("MANGALIB", "MangaLib", "ru")
internal class MangaLibParser(
	context: MangaLoaderContext,
) : LibSocialParser(
	context = context,
	source = MangaParserSource.MANGALIB,
	siteId = 1,
	siteDomains = arrayOf("mangalib.me"),
) {

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = try {
		super.getPages(chapter)
	} catch (e: NotFoundException) {
		throw AuthRequiredException(source, e)
	}
}
