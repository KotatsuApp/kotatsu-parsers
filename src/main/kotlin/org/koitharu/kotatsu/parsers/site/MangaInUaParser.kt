package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.styleValueOrNull
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale

private const val DEF_BRANCH_NAME = "Основний переклад"

@MangaSourceParser("MANGAINUA", "MANGA/in/UA", "uk")
class MangaInUaParser(context: MangaLoaderContext) : PagedMangaParser(
	context = context,
	source = MangaSource.MANGAINUA,
	pageSize = 24,
	searchPageSize = 10,
) {

	override val sortOrders: Set<SortOrder>
		get() = Collections.singleton(SortOrder.UPDATED)

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manga.in.ua")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = when {
			!query.isNullOrEmpty() -> (
					"/index.php?do=search" +
							"&subaction=search" +
							"&search_start=$page" +
							"&full_search=1" +
							"&story=$query" +
							"&titleonly=3"
					).toAbsoluteUrl(domain)

			tags.isNullOrEmpty() -> "/mangas/page/$page".toAbsoluteUrl(domain)
			tags.size == 1 -> "${tags.first().key}/page/$page"
			tags.size > 1 -> throw IllegalArgumentException("This source supports only 1 genre")
			else -> "/mangas/page/$page".toAbsoluteUrl(domain)
		}
		val doc = webClient.httpGet(url).parseHtml()
		val container = doc.body().requireElementById("site-content")
		val items = container.select("div.col-6")
		return items.mapNotNull { item ->
			val href = item.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = item.selectFirst("h3.card__title")?.text() ?: return@mapNotNull null,
				coverUrl = item.selectFirst("header.card__cover")?.selectFirst("img")?.run {
					attrAsAbsoluteUrlOrNull("data-src") ?: attrAsAbsoluteUrlOrNull("src")
				}.orEmpty(),
				altTitle = null,
				author = null,
				rating = item.selectFirst("div.card__short-rate--num")
					?.text()
					?.toFloatOrNull()
					?.div(10F) ?: RATING_UNKNOWN,
				url = href,
				isNsfw = item.selectFirst("ul.card__list")?.select("li")?.lastOrNull()?.text() == "18+",
				tags = runCatching {
					item.selectFirst("div.card__category")?.select("a")?.mapToSet {
						MangaTag(
							title = it.ownText(),
							key = it.attr("href").removeSuffix("/"),
							source = source,
						)
					}
				}.getOrNull().orEmpty(),
				state = null,
				publicUrl = href.toAbsoluteUrl(container.host ?: domain),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("site-content")
		val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
		val chapterNodes = root.selectFirstOrThrow(".linkstocomics").select(".ltcitems")
		var prevChapterName: String? = null
		var i = 0
		return manga.copy(
			description = root.selectFirst("div.item__full-description")?.text(),
			largeCoverUrl = root.selectFirst("div.item__full-sidebar--poster")?.selectFirst("img")
				?.attrAsAbsoluteUrlOrNull("src"),
			chapters = chapterNodes.mapChapters { _, item ->
				val href = item?.selectFirst("a")?.attrAsRelativeUrlOrNull("href")
					?: return@mapChapters null
				val isAlternative = item.styleValueOrNull("background") != null
				val name = item.selectFirst("a")?.text().orEmpty()
				if (!isAlternative) i++
				MangaChapter(
					id = generateUid(href),
					name = if (isAlternative) {
						prevChapterName ?: return@mapChapters null
					} else {
						prevChapterName = name
						name
					},
					number = i,
					url = href,
					scanlator = null,
					branch = if (isAlternative) {
						name.substringAfterLast(':').trim()
					} else {
						DEF_BRANCH_NAME
					},
					uploadDate = dateFormat.tryParse(item.selectFirst("div.ltcright")?.text()),
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().requireElementById("comics").selectFirstOrThrow("ul.xfieldimagegallery")
		return root.select("li").map { ul ->
			val img = ul.selectFirstOrThrow("img")
			val url = img.attrAsAbsoluteUrl("data-src")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val domain = domain
		val doc = webClient.httpGet("https://$domain/mangas").parseHtml()
		val root = doc.body().requireElementById("menu_1").selectFirstOrThrow("div.menu__wrapper")
		return root.select("li").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			MangaTag(
				title = a.ownText(),
				key = a.attr("href").removeSuffix("/"),
				source = source,
			)
		}
	}
}
