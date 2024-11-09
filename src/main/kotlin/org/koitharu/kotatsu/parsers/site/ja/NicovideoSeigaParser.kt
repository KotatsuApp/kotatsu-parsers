package org.koitharu.kotatsu.parsers.site.ja

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

private const val STATUS_ONGOING = "連載"
private const val STATUS_FINISHED = "完結"

@MangaSourceParser("NICOVIDEO_SEIGA", "NicoVideo Seiga", "ja")
internal class NicovideoSeigaParser(context: MangaLoaderContext) :
	MangaParser(context, MangaParserSource.NICOVIDEO_SEIGA),
	MangaParserAuthProvider {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("nicovideo.jp")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override val authUrl: String
		get() = "https://${getDomain("account")}/login?site=seiga"

	override val isAuthorized: Boolean
		get() = context.cookieJar.getCookies(getDomain("seiga")).any {
			it.name == "user_session"
		}

	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${getDomain("app")}/my/apps").parseHtml().body()
		return body.selectFirst("#userinfo > div > div > strong")?.text() ?: throw AuthRequiredException(source)
	}

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val page = (offset / 20f).toIntUp().inc()
		val domain = getDomain("seiga")
		val url = when {
			!filter.query.isNullOrEmpty() -> {
				return if (offset == 0) getSearchList(filter.query, page) else emptyList()
			}

			else -> {

				if (filter.tags.isNotEmpty()) {
					filter.tags.oneOrThrowIfMany().let {
						"https://$domain/manga/list?category=${it?.key}&page=$page&sort=${getSortKey(order)}"
					}

				} else {
					"https://$domain/manga/list?page=$page&sort=${getSortKey(order)}"
				}

			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		val comicList = doc.body().select("#comic_list > ul > li") ?: doc.parseFailed("Container not found")
		val items = comicList.select("div > .description > div > div")
		return items.mapNotNull { item ->
			val href =
				item.selectFirst(".comic_icon > div > a")?.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
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
						title = a.ownText(),
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
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(getDomain("seiga"))).parseHtml()
		val contents = doc.body().requireElementById("contents")
		val statusText = contents
			.select("div.mg_work_detail > div > div:nth-child(2) > div.tip.content_status.status_series > span")
			.text()
		return manga.copy(
			description = contents.selectFirst("div.mg_work_detail > div > div.row > div.description_text")?.html(),
			largeCoverUrl = contents.selectFirst("div.primaries > div.main_visual > a > img")
				?.attrAsAbsoluteUrlOrNull("src"),
			state = when (statusText) {
				STATUS_ONGOING -> MangaState.ONGOING
				STATUS_FINISHED -> MangaState.FINISHED
				else -> null
			},
			isNsfw = contents.select(".icon_adult").isNotEmpty(),
			chapters = contents.select("#episode_list > ul > li").mapChapters { i, li ->
				val href = li.selectFirst("div > div.description > div.title > a")
					?.attrAsRelativeUrl("href") ?: li.parseFailed()
				MangaChapter(
					id = generateUid(href),
					name = li.select("div > div.description > div.title > a").text(),
					number = i + 1f,
					volume = 0,
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
		val doc = webClient.httpGet(fullUrl).parseHtml()
		if (!doc.select("#login_manga").isEmpty())
			throw AuthRequiredException(source)
		val root = doc.body().select("#page_contents > li")
		return root.map { li ->
			val url = li.select("div > img").attr("data-original")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${getDomain("seiga")}/manga/list").parseHtml()
		val root = doc.body().selectOrThrow("#mg_category_list > ul > li").drop(1)
		return root.mapToSet { li ->
			val a = li.selectFirstOrThrow("a")
			MangaTag(
				title = a.text(),
				key = a.attrAsRelativeUrl("href").substringAfter("category=").substringBefore("&"),
				source = source,
			)
		}
	}

	private suspend fun getSearchList(query: String, page: Int): List<Manga> {
		val domain = getDomain("seiga")
		val doc = webClient.httpGet("https://$domain/manga/search/?q=$query&page=$page&sort=score").parseHtml()
		val root = doc.body().select(".search_result__item")
		return root.mapNotNull { item ->
			val href = item.selectFirst(".search_result__item__thumbnail > a")
				?.attrAsRelativeUrl("href") ?: doc.parseFailed()
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(item.host ?: domain),
				title = item.selectFirst(".search_result__item__info > .search_result__item__info--title > a")
					?.textOrNull() ?: return@mapNotNull null,
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
