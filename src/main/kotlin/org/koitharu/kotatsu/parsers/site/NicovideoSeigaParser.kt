package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

private const val STATUS_ONGOING = "連載"
private const val STATUS_FINISHED = "完結"

@MangaSourceParser("NICOVIDEO_SEIGA", "Nicovideo Seiga", "ja")
class NicovideoSeigaParser(override val context: MangaLoaderContext) :
	MangaParser(MangaSource.NICOVIDEO_SEIGA),
	MangaParserAuthProvider {

	override val authUrl: String
		get() = "https://${getDomain("account")}/login?site=seiga"

	override val isAuthorized: Boolean
		get() = context.cookieJar.getCookies(getDomain("seiga")).any {
			it.name == "user_session"
		}

	override suspend fun getUsername(): String {
		val body = context.httpGet("https://${getDomain("app")}/my/apps").parseHtml().body()
		return body.selectFirst("#userinfo > div > div > strong")?.text() ?: throw AuthRequiredException(source)
	}

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("nicovideo.jp", null)

	@InternalParsersApi
	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val page = (offset / 20f).toIntUp().inc()
		val domain = getDomain("seiga")
		val url = when {
			!query.isNullOrEmpty() -> return if (offset == 0) getSearchList(query, page) else emptyList()
			tags.isNullOrEmpty() -> "https://$domain/manga/list?page=$page&sort=${getSortKey(sortOrder)}"
			tags.size == 1 -> "https://$domain/manga/list?category=${tags.first().key}&page=$page&sort=${getSortKey(sortOrder)}"
			tags.size > 1 -> throw IllegalArgumentException("This source supports only 1 category")
			else -> "https://$domain/manga/list?page=$page&sort=${getSortKey(sortOrder)}"
		}
		val doc = context.httpGet(url).parseHtml()
		val comicList = doc.body().select("#comic_list > ul > li") ?: parseFailed("Container not found")
		val items = comicList.select("div > .description > div > div")
		return items.mapNotNull { item ->
			val href = item.selectFirst(".comic_icon > div > a")?.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
			val statusText = item.selectFirst(".mg_description_header > .mg_icon > .content_status > span")?.text()
			Manga(
				id = generateUid(href),
				title = item.selectFirst(".mg_body > .title > a")?.text() ?: return@mapNotNull null,
				coverUrl = item.selectFirst(".comic_icon > div > a > img")?.attrAsAbsoluteUrl("src").orEmpty(),
				altTitle = null,
				author = item.selectFirst(".mg_description_header > .mg_author > a")?.text(),
				rating = RATING_UNKNOWN,
				url = href,
				isNsfw = false,
				tags = item.getElementsByAttributeValueContaining("href", "?category=").mapToSet { a ->
					MangaTag(
						key = a.attr("href").substringAfterLast('='),
						title = a.ownText().trim(),
						source = source,
					)
				},
				state = when (statusText) {
					STATUS_ONGOING -> MangaState.ONGOING
					STATUS_FINISHED -> MangaState.FINISHED
					else -> null
				},
				publicUrl = href.toAbsoluteUrl(item.host ?: getDomain("seiga")),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.toAbsoluteUrl(getDomain("seiga"))).parseHtml()
		val contents = doc.body().selectFirst("#contents") ?: parseFailed("Cannot find root")
		val statusText = contents
			.select("div.mg_work_detail > div > div:nth-child(2) > div.tip.content_status.status_series > span")
			.text()
		return manga.copy(
			description = contents.selectFirst("div.mg_work_detail > div > div.row > div.description_text")?.html(),
			largeCoverUrl = contents.selectFirst("div.primaries > div.main_visual > a > img")?.attrAsAbsoluteUrlOrNull("src"),
			state = when (statusText) {
				STATUS_ONGOING -> MangaState.ONGOING
				STATUS_FINISHED -> MangaState.FINISHED
				else -> null
			},
			isNsfw = contents.select(".icon_adult").isNotEmpty(),
			chapters = contents.select("#episode_list > ul > li").mapIndexedNotNull { i, li ->
				val href = li.selectFirst("div > div.description > div.title > a")?.attrAsRelativeUrl("href") ?: parseFailed()
				MangaChapter(
					id = generateUid(href),
					name = li.select("div > div.description > div.title > a").text(),
					number = i + 1,
					url = href,
					scanlator = null,
					branch = null,
					uploadDate = 0,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(getDomain("seiga"))
		val doc = context.httpGet(fullUrl).parseHtml()
		if (!doc.select("#login_manga").isEmpty())
			throw AuthRequiredException(source)
		val root = doc.body().select("#page_contents > li")
		return root.map { li ->
			val url = li.select("div > img").attr("data-original")
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
		val doc = context.httpGet("https://${getDomain("seiga")}/manga/list").parseHtml()
		val root = doc.body().select("#mg_category_list > ul > li") ?: parseFailed("Cannot find tags")
		return root.mapToSet { li ->
			val a = li.selectFirst("a") ?: parseFailed("a is null")
			MangaTag(
				title = a.text(),
				key = a.attrAsRelativeUrlOrNull("href").orEmpty(),
				source = source,
			)
		}
	}

	private suspend fun getSearchList(query: String, page: Int): List<Manga> {
		val domain = getDomain("seiga")
		val doc = context.httpGet("https://$domain/manga/search/?q=$query&page=$page&sort=score").parseHtml()
		val root = doc.body().select(".search_result__item")
		return root.mapNotNull { item ->
			val href = item.selectFirst(".search_result__item__thumbnail > a")?.attrAsRelativeUrl("href") ?: parseFailed()
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(item.host ?: domain),
				title = item.selectFirst(".search_result__item__info > .search_result__item__info--title > a")
					?.text()?.trim() ?: return@mapNotNull null,
				altTitle = null,
				author = null,
				tags = emptySet(),
				rating = RATING_UNKNOWN,
				state = null,
				isNsfw = false,
				source = source,
				coverUrl = item.selectFirst(".search_result__item__thumbnail > a > img")
					?.attrAsAbsoluteUrl("data-original").orEmpty(),
			)
		}
	}

	private fun getSortKey(sortOrder: SortOrder) = when (sortOrder) {
		SortOrder.POPULARITY -> "manga_view"
		SortOrder.UPDATED -> "manga_updated"
		else -> "manga_view"
	}
}