package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.Locale

@MangaSourceParser("COSMIC_SCANS", "CosmicScans.id", "id")
internal class CosmicScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.COSMIC_SCANS, "cosmicscans.id", pageSize = 30, searchPageSize = 30) {

	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "/semua-komik"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			if (page > lastSearchPage) {
				return emptyList()
			}
			val url = buildString {
				append("https://")
				append(domain)
				append("/page/")
				append(page)
				append("/?s=")
				append(query.urlEncoded())
			}
			val docs = webClient.httpGet(url).parseHtml()
			lastSearchPage = docs.selectFirst(".pagination .next")
				?.previousElementSibling()
				?.text()?.toIntOrNull() ?: 1
			return parseMangaList(docs)
		}
		if (page > 1) {
			return emptyList()
		}
		val sortQuery = when (sortOrder) {
			SortOrder.ALPHABETICAL -> "title"
			SortOrder.NEWEST -> "latest"
			SortOrder.POPULARITY -> "popular"
			SortOrder.UPDATED -> "update"
			else -> ""
		}
		val tagKey = "genre[]".urlEncoded()
		val tagQuery =
			if (tags.isNullOrEmpty()) "" else tags.joinToString(separator = "&", prefix = "&") { "$tagKey=${it.key}" }
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("/?order=")
			append(sortQuery)
			append(tagQuery)
			append("&page=")
			append(page)
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}
}
