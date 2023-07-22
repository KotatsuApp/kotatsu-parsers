package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import java.text.SimpleDateFormat

@MangaSourceParser("BABELTOON", "BabelToon", "en")
internal class BabelToon(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.BABELTOON, "babeltoon.com", pageSize = 20, searchPageSize = 10) {

	override val listUrl = "/series"
	override val selectMangaliste = ".postbody .listupd .maindet .inmain"

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select(".eplister > ul > li").mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".epl-num")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1,
				scanlator = null,
				uploadDate = dateFormat.tryParse(element.selectFirst(".epl-date")?.text()),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()

		return docs.select("div.epcontent img").map { img ->
			val url = img.imageUrl()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}

