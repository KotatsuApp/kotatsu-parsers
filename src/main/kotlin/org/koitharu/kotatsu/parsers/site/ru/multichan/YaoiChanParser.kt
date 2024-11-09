package org.koitharu.kotatsu.parsers.site.ru.multichan

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("YAOICHAN", "Яой-тян", "ru")
internal class YaoiChanParser(context: MangaLoaderContext) : ChanParser(context, MangaParserSource.YAOICHAN) {

	override val configKeyDomain = ConfigKey.Domain(
		"v3.yaoi-chan.me",
		"v2.yaoi-chan.me",
		"v1.yaoi-chan.me",
		"yaoi-chan.me",
	)

	override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.NEWEST)

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("dle-content")
		return manga.copy(
			description = root.getElementById("description")?.html()?.substringBeforeLast("<div"),
			largeCoverUrl = root.getElementById("cover")?.absUrl("src"),
			chapters = root.select("table.table_cha").flatMap { table ->
				table.select("div.manga")
			}.mapNotNull { it.selectFirst("a") }.mapChapters(reversed = true) { i, a ->
				val href = a.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(href),
					name = a.text(),
					number = i + 1f,
					volume = 0,
					url = href,
					uploadDate = 0L,
					source = source,
					scanlator = null,
					branch = null,
				)
			},
		)
	}
}
