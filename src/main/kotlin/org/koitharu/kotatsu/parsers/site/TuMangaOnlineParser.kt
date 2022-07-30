package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TUMANGAONLINE", "TuMangaOnline", "es")
class TuMangaOnlineParser(override val context: MangaLoaderContext) : PagedMangaParser(
	source = MangaSource.TUMANGAONLINE,
	pageSize = 24,
) {

	override val configKeyDomain = ConfigKey.Domain("lectortmo.com", null)

	override val sortOrders: EnumSet<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.UPDATED,
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 0
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = (
				"/library" +
						"?order_item=likes_count" +
						"&order_dir=desc" +
						"&filter_by=title" +
						"&_pg=1" +
						"&page=$page"
				).toAbsoluteUrl(getDomain())
		val doc = context.httpGet(url).parseHtml()
		val items = doc.body().select("div.element")
		return items.mapNotNull { item ->
			val href = item.selectFirst("a")?.attrAsRelativeUrl("href")?.substringAfter(" ") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = item.selectFirst("h4.text-truncate")?.text() ?: return@mapNotNull null,
				coverUrl = item.select("style").toString().substringAfter("('").substringBeforeLast("')"),
				altTitle = null,
				author = null,
				rating = item.selectFirst("span.score")
					?.text()
					?.toFloatOrNull()
					?.div(10F) ?: RATING_UNKNOWN,
				url = href,
				isNsfw = item.select("i").hasClass("fas fa-heartbeat fa-2x"),
				tags = emptySet(),
				state = null,
				publicUrl = href.toAbsoluteUrl(doc.host ?: getDomain()),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml()
		val contents = doc.body().selectFirstOrThrow("section.element-header-content")
		return manga.copy(
			description = contents.selectFirst("p.element-description")?.html(),
			largeCoverUrl = contents.selectFirst(".book-thumbnail")?.attrAsAbsoluteUrlOrNull("src"),
			state = parseStatus(contents.select("span.book-status").text().orEmpty()),
			author = contents.selectFirst("h5.card-title")?.attr("title")?.substringAfter(", "),
			chapters = doc.body().select("div.chapters > ul.list-group li.p-0.list-group-item").asReversed()
				.mapChapters { i, li ->
					val href = li.selectFirst("div.row > .text-right > a")
						?.attrAsRelativeUrl("href") ?: li.parseFailed()
					MangaChapter(
						id = generateUid(href),
						name = li.select("div.col-10.text-truncate").text().substringAfter(": ").trim(),
						number = i + 1,
						url = href,
						scanlator = li.select("div.col-md-6.text-truncate").text(),
						branch = null,
						uploadDate = li.select("span.badge.badge-primary.p-2").first()?.text()
							?.let { parseChapterDate(it) }
							?: 0,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		TODO("Not yet implemented")
	}

	override suspend fun getTags(): Set<MangaTag> {
		TODO("Not yet implemented")
	}

	private fun parseStatus(status: String) = when {
		status.contains("Publicándose") -> MangaState.ONGOING
		status.contains("Finalizado") -> MangaState.FINISHED
		else -> null
	}

	private fun parseChapterDate(date: String): Long =
		SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time ?: 0

}