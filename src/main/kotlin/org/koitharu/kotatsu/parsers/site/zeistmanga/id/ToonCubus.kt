package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("TOONCUBUS", "ToonCubus", "id", ContentType.HENTAI)
internal class ToonCubus(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.TOONCUBUS, "www.tooncubus.top") {

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/p/genre-list.html").parseHtml()
		return doc.select(".dzdes-genre ul li a").mapToSet {
			MangaTag(
				key = it.attr("href").removeSuffix("/").substringAfterLast("/"),
				title = it.selectFirstOrThrow("span").text(),
				source = source,
			)
		}
	}

	override suspend fun loadChapters(mangaUrl: String, doc: Document): List<MangaChapter> {
		return doc.selectFirstOrThrow("ul.series-chapterlist").select("div.flexch-infoz")
			.mapChapters(reversed = true) { i, div ->
				val url = div.selectFirstOrThrow("a").attr("href")
				val name = div.selectFirstOrThrow("a span").text()
				MangaChapter(
					id = generateUid(url),
					url = url,
					name = name,
					number = i + 1f,
					volume = 0,
					branch = null,
					uploadDate = 0,
					scanlator = null,
					source = source,
				)
			}
	}
}
