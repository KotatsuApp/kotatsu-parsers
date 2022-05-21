package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

private const val STATUS_ONGOING = "連載"
private const val STATUS_FINISHED = "完結"

@MangaSourceParser("NICOVIDEOSEIGA", "Nicovideo Seiga", "ja")
class NicovideoSeigaParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.NICOVIDEOSEIGA),
	MangaParserAuthProvider {

	override val authUrl: String
		get() = "https://account.nicovideo.jp/login?site=seiga"

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(getDomain()).any {
				it.name == "user_session"
			}
		}

	override suspend fun getUsername(): String {
		val body = context.httpGet("https://app.nicovideo.jp/my/apps").parseHtml().body()
		return body.selectFirst("#userinfo > div > div > strong")?.text() ?: parseFailed("Cannot get username")
	}

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("seiga.nicovideo.jp", null)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder?,
	): List<Manga> {
		val page = (offset / 20f).toIntUp().inc()
		if (!query.isNullOrEmpty()) {
			return if (offset == 0) getSearchList(query, page) else emptyList()
		}
		val url = "/manga/list?page=$page&sort=${getSortKey(sortOrder)}".withDomain()
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
				tags = emptySet(),
				state = when (statusText) {
					STATUS_ONGOING -> MangaState.ONGOING
					STATUS_FINISHED -> MangaState.FINISHED
					else -> null
				},
				publicUrl = href.toAbsoluteUrl(item.host ?: getDomain()),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.withDomain()).parseHtml()
		val contents = doc.body().selectFirst("#contents") ?: parseFailed("Cannot find root")
		val statusText = contents.select("div.mg_work_detail > div > div:nth-child(2) > div.tip.content_status.status_series > span").text()
		return manga.copy(
			description = contents.selectFirst("div.mg_work_detail > div > div.row > div.description_text")?.html(),
			largeCoverUrl = contents.selectFirst("div.primaries > div.main_visual > a > img")?.attrAsAbsoluteUrlOrNull("src"),
			state = when (statusText) {
				STATUS_ONGOING -> MangaState.ONGOING
				STATUS_FINISHED -> MangaState.FINISHED
				else -> null
			},
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
		val fullUrl = chapter.url.withDomain()
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
		return emptySet()
	}

	private suspend fun getSearchList(query: String, page: Int): List<Manga> {
		val domain = getDomain()
		val doc = context.httpGet("https://$domain/manga/search/?q=$query&page=$page&sort=score").parseHtml()
		val root = doc.body().select(".search_result__item")
		return root.mapNotNull { item ->
			val href = item.selectFirst(".search_result__item__thumbnail > a")?.attrAsRelativeUrl("href") ?: parseFailed()
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(item.host ?: getDomain()),
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

	private fun getSortKey(sortOrder: SortOrder?) =
		when (sortOrder ?: sortOrders.minByOrNull { it.ordinal }) {
			SortOrder.POPULARITY -> "manga_view"
			SortOrder.UPDATED -> "manga_updated"
			else -> "manga_view"
		}
}