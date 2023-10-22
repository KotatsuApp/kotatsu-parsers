package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import java.lang.IllegalArgumentException
import java.util.EnumSet

@MangaSourceParser("RIZZCOMIC", "RizzComic", "en")
internal class RizzComic(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.RIZZCOMIC, "rizzcomic.com", pageSize = 50, searchPageSize = 20) {
	override val datePattern = "dd MMM yyyy"
	override val listUrl = "/series"

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.ALPHABETICAL)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		if (page > 1) {
			return emptyList()
		}
		if (!query.isNullOrEmpty()) {
			throw IllegalArgumentException("Search is not supported by this source")
		}
		val url = if (!tags.isNullOrEmpty()) {
			buildString {
				append("https://")
				append(domain)
				append("/genre/")
				append(tag?.key.orEmpty())
			}
		} else {
			"https://$domain$listUrl"
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}
}
