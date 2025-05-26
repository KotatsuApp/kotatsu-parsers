package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow

@MangaSourceParser("ULASCOMIC", "UlasComic", "id")
internal class UlasComic(context: MangaLoaderContext):
	ZeistMangaParser(context, MangaParserSource.ULASCOMIC, "www.ulascomic00.xyz") {
	
	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.selectFirstOrThrow("script:containsData(config['chapterImage'])")
			.data()
			.substringAfter("config['chapterImage'] = [")
			.substringBefore("];")
			.split("\",")
			.map { url ->
				val cleanUrl = url.trim().replace("\"", "")
				MangaPage(
					id = generateUid(cleanUrl),
					url = cleanUrl,
					preview = null,
					source = source,
				)
			}
	}
}
