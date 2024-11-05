package org.koitharu.kotatsu.parsers.site.madara.es

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.ArrayList

@MangaSourceParser("MANHWALATINO", "ManhwaLatino", "es")
internal class ManhwaLatino(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHWALATINO, "manhwa-latino.com", 10) {
	override val datePattern = "MM/dd"
	override val selectPage = "div.page-break img.wp-manga-chapter-img"
	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val maxPageChapterSelect = doc.select("div.pagination .page a")
		var maxPageChapter = 1
		if (!maxPageChapterSelect.isNullOrEmpty()) {
			maxPageChapterSelect.map {
				val i = it.attr("href").substringAfterLast("=").toInt()
				if (i > maxPageChapter) {
					maxPageChapter = i
				}
			}
		}
		if (maxPageChapter == 3) {
			maxPageChapter = 4 // fix some mangas
		}
		val url = manga.url.toAbsoluteUrl(domain)
		return run {
			if (maxPageChapter == 1) {
				parseChapters(doc)
			} else {
				coroutineScope {
					val result = ArrayList(parseChapters(doc))
					result.ensureCapacity(result.size * maxPageChapter)
					(2..maxPageChapter).map { i ->
						async {
							loadChapters(url, i)
						}
					}.awaitAll()
						.flattenTo(result)
					result
				}
			}
		}.reversed()
	}

	private suspend fun loadChapters(url: String, page: Int): List<MangaChapter> {
		return parseChapters(webClient.httpGet("$url/?t=$page").parseHtml().body())
	}

	private fun parseChapters(doc: Element): List<MangaChapter> {
		val root2 = doc.selectFirstOrThrow("div.content-area")
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return root2.select(selectChapter).mapChapters { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val dateText2 = if (dateText == "¡Recién publicado!") {
				"1 mins ago"
			} else {
				dateText
			}
			val name = li.selectFirst("a:contains(Capitulo)")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				name = name,
				number = i + 1f,
				volume = 0,
				url = link,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText2,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}
