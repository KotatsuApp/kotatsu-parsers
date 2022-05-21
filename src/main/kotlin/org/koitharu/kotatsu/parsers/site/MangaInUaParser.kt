package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

private const val DEF_BRANCH_NAME = "Основний переклад"

@MangaSourceParser("MANGAINUA", "MANGA/in/UA", "uk")
class MangaInUaParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.MANGAINUA) {

	override val sortOrders: Set<SortOrder>
		get() = Collections.singleton(SortOrder.UPDATED)

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manga.in.ua", null)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val page = (offset / 24f).toIntUp().inc()
		val searchPage = (offset / 10f).toIntUp().inc()
		val url = when {
			!query.isNullOrEmpty() -> (
				"/index.php?do=search" +
					"&subaction=search" +
					"&search_start=$searchPage" +
					"&full_search=1" +
					"&story=$query" +
					"&titleonly=3"
				).withDomain()
			tags.isNullOrEmpty() -> "/mangas/page/$page".withDomain()
			tags.size == 1 -> "${tags.first().key}/page/$page"
			tags.size > 1 -> throw IllegalArgumentException("This source supports only 1 genre")
			else -> "/mangas/page/$page".withDomain()
		}
		val doc = context.httpGet(url).parseHtml()
		val container = doc.body().getElementById("dle-content") ?: parseFailed("Container not found")
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
				publicUrl = href.toAbsoluteUrl(container.host ?: getDomain()),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.withDomain()).parseHtml()
		val root =
			doc.body().getElementById("dle-content") ?: parseFailed("Cannot find root")
		val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
		val chaptersRoot = root.selectFirst(".linkstocomics")
		var prevChapterName: String? = null
		var i = 0
		return manga.copy(
			description = root.selectFirst("div.item__full-description")?.text(),
			largeCoverUrl = root.selectFirst("div.item__full-sidebar--poster")?.selectFirst("img")
				?.attrAsAbsoluteUrlOrNull("src"),
			chapters = chaptersRoot?.select("div.ltcitems")?.mapNotNull { item ->
				val href = item?.selectFirst("a")?.attrAsRelativeUrlOrNull("href")
					?: return@mapNotNull null
				val isAlternative = item.styleValueOrNull("background") != null
				val name = item.selectFirst("a")?.text().orEmpty()
				if (!isAlternative) i++
				MangaChapter(
					id = generateUid(href),
					name = if (isAlternative) {
						prevChapterName ?: return@mapNotNull null
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
			}.orEmpty(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.withDomain()
		val doc = context.httpGet(fullUrl).parseHtml()
		val root =
			doc.body().getElementById("comics")?.selectFirst("ul.xfieldimagegallery") ?: parseFailed("Root not found")
		return root.select("li").map { ul ->
			val img = ul.selectFirst("img") ?: parseFailed("Page image not found")
			val url = img.attrAsAbsoluteUrl("data-src")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				referer = fullUrl,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val domain = getDomain()
		val doc = context.httpGet("https://$domain/mangas").parseHtml()
		val root =
			doc.body().getElementById("menu_1")?.selectFirst("div.menu__wrapper") ?: parseFailed("Cannot find root")
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