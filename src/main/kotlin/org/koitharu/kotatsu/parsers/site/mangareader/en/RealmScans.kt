package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import java.util.EnumSet

@MangaSourceParser("REALMSCANS", "RealmScans", "en")
internal class RealmScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.REALMSCANS, "realmscans.to", pageSize = 52, searchPageSize = 50) {

	override val listUrl = "/series"
	override val datePattern = "dd MMM yyyy"

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.NEWEST)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			return emptyList() // to do
		}

		if (!tags.isNullOrEmpty()) {
			if (page > 1) {
				return emptyList()
			}
			val tag = tags.oneOrThrowIfMany()
			val url = buildString {
				append("https://")
				append(domain)
				append("genre/")
				append(tag?.key.orEmpty())
			}
			return parseMangaList(webClient.httpGet(url).parseHtml())
		}

		if (page > 1) {
			return emptyList()
		}
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val tagMap = getOrCreateTagMap()
		val selectTag = docs.select(".wd-full .mgen > a")
		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }
		val mangaState = docs.selectFirst(".bs-status")?.let {
			when (it.text().lowercase()) {
				"ongoing" -> MangaState.ONGOING
				"completed" -> MangaState.FINISHED
				else -> null
			}
		}
		val author = docs.selectFirst(".tsinfo div:contains(Author)")?.lastElementChild()?.text()
		val nsfw = docs.selectFirst(".restrictcontainer") != null
			|| docs.selectFirst(".info-right .alr") != null
			|| docs.selectFirst(".postbody .alr") != null

		// Description in markdown renders it unattractive and unclear on the synopsis
		// val desc = docs.selectFirstOrThrow("script:containsData(var description)").data().substringAfter("var description = \"").substringBefore("\";")

		return manga.copy(
			description = null,
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw || nsfw,
			tags = tags,
			chapters = chapters,
		)
	}
}
