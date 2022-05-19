package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGAINUA", "MANGA/in/UA", "uk")
class MangaInUaParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.MANGAINUA) {

	override val sortOrders: Set<SortOrder>
		get() = Collections.singleton(SortOrder.UPDATED)

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manga.in.ua", null)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder?
	): List<Manga> {
		val page = (offset / 24f).toIntUp().inc()
		val url = when {
			!query.isNullOrEmpty() -> parseFailed("Search currently unavailable") // TODO
			tags.isNullOrEmpty() -> "/mangas/page/$page".withDomain()
			tags.size == 1 -> "${tags.first().key}/page/$page"
			tags.size > 1 -> throw IllegalArgumentException("This source supports only 1 tag")
			else -> "/mangas/${page}".withDomain()
		}
		val doc = context.httpGet(url).parseHtml()
		val container = doc.body().getElementById("dle-content") ?: parseFailed("Container not found")
		val items = container.select("div.col-6")
		return items.mapNotNull { item ->
			val href = item.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = item.selectFirst("h3.card__title")?.text() ?: return@mapNotNull null,
				coverUrl = item.selectFirst("header.card__cover")?.selectFirst("img")?.attrAsAbsoluteUrl("data-src").orEmpty(),
				altTitle = null,
				author = null,
				rating = runCatching {
					item.selectFirst("div.card__short-rate--num")
						?.text()
						?.toFloatOrNull()
						?.div(10F)
				}.getOrNull() ?: RATING_UNKNOWN,
				url = href,
				isNsfw = item.selectFirst("ul.card__list")?.select("li")?.lastOrNull()?.text() == "18+",
				tags = runCatching {
					item.selectFirst("div.card__category")?.select("a")?.mapToSet {
						MangaTag(
							title = it.ownText(),
							key = it.attr("href").removeSuffix("/").substringAfterLast('/'),
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
		TODO("Not yet implemented")
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		TODO("Not yet implemented")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val domain = getDomain()
		val doc = context.httpGet("https://$domain/mangas").parseHtml()
		val root = doc.body().getElementById("menu_1")?.selectFirst("div.menu__wrapper") ?: parseFailed("Cannot find root")
		return root.select("li").mapToSet { li ->
			val a = li.selectFirst("a") ?: throw ParseException("a is null")
			MangaTag(
				title = a.ownText(),
				key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
				source = source,
			)
		}
	}
}