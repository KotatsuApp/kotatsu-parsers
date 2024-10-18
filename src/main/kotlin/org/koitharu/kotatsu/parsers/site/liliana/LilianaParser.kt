package org.koitharu.kotatsu.parsers.site.liliana

import androidx.collection.scatterSetOf
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getBooleanOrDefault
import java.util.*

internal abstract class LilianaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 24,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_MONTH,
		SortOrder.POPULARITY_WEEK,
		SortOrder.POPULARITY_TODAY,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.RATING,
	)

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions(
		availableTags = getAvailableTags(),
		availableStates = setOf(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED),
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/search/")
					append(page)
					append("/?keyword=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append("/filter/")
					append(page)

					append("/?sort=")
					when (order) {
						SortOrder.UPDATED -> append("latest-updated")
						SortOrder.POPULARITY -> append("views")
						SortOrder.POPULARITY_MONTH -> append("views_month")
						SortOrder.POPULARITY_WEEK -> append("views_week")
						SortOrder.POPULARITY_TODAY -> append("views_day")
						SortOrder.ALPHABETICAL -> append("az")
						SortOrder.ALPHABETICAL_DESC -> append("za")
						SortOrder.NEWEST -> append("new")
						SortOrder.NEWEST_ASC -> append("old")
						SortOrder.RATING -> append("score")
						else -> append("latest-updated")
					}

					append("&genres=")
					filter.tags.joinTo(this, ",") { it.key }

					append("&notGenres=")
					filter.tagsExclude.joinTo(this, ",") { it.key }

					if (filter.states.isNotEmpty()) {
						append("&status=")
						append(
							when (filter.states.first()) {
								MangaState.ONGOING -> "on-going"
								MangaState.FINISHED -> "completed"
								MangaState.PAUSED -> "on-hold"
								MangaState.ABANDONED -> "canceled"
								else -> "all"
							},
						)
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div#main div.grid > div").map { parseSearchManga(it) }
	}

	private fun parseSearchManga(element: Element): Manga {
		val href = element.selectFirstOrThrow("a").attrAsRelativeUrl("href")
		return Manga(
			id = generateUid(href),
			url = href,
			publicUrl = href.toAbsoluteUrl(domain),
			coverUrl = element.selectFirst("img")?.src().orEmpty(),
			title = element.selectFirst(".text-center a")?.text().orEmpty(),
			altTitle = null,
			rating = RATING_UNKNOWN,
			tags = emptySet(),
			author = null,
			state = null,
			source = source,
			isNsfw = isNsfwSource,
		)
	}

	@JvmField
	protected val ongoing = scatterSetOf(
		"on-going", "đang tiến hành", "進行中",
	)

	@JvmField
	protected val finished = scatterSetOf(
		"completed", "hoàn thành", "完了",
	)

	@JvmField
	protected val abandoned = scatterSetOf(
		"canceled", "đã huỷ bỏ", "キャンセル",
	)

	@JvmField
	protected val paused = scatterSetOf(
		"on-hold", "tạm dừng", "一時停止",
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			description = doc.selectFirst("div#syn-target")?.html(),
			largeCoverUrl = doc.selectFirst(".a1 > figure img")?.src(),
			tags = doc.select(".a2 div > a[rel='tag'].label").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/'),
					title = a.text().toTitleCase(sourceLocale),
					source = source,
				)
			},
			author = doc.selectFirst("div.y6x11p i.fas.fa-user + span.dt")?.textOrNull()?.takeUnless {
				it.equals("updating", true)
			},
			state = when (doc.selectFirst("div.y6x11p i.fas.fa-rss + span.dt")?.text()?.lowercase().orEmpty()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in paused -> MangaState.PAUSED
				in abandoned -> MangaState.ABANDONED
				else -> null
			},
			chapters = doc.selectOrThrow("ul > li.chapter").mapChapters(reversed = true) { i, element ->
				val href = element.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(href),
					name = element.selectFirst("a")?.text() ?: "Chapter : ${i + 1f}",
					number = i + 1f,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = element.selectFirst("time[datetime]")?.attr("datetime")?.toLongOrNull()?.times(1000)
						?: 0L,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val script = doc.selectFirstOrThrow("script:containsData(const CHAPTER_ID)").data()

		val chapterId = script.substringAfter("const CHAPTER_ID = ", "").substringBefore(';', "")
		check(chapterId.isNotEmpty()) { "Failed to get chapter id" }

		val ajaxUrl = buildString {
			append("https://")
			append(domain)
			append("/ajax/image/list/chap/")
			append(chapterId)
		}

		val responseJson = webClient.httpGet(ajaxUrl).parseJson()

		check(responseJson.getBooleanOrDefault("status", false)) { responseJson.getString("msg") }

		val pageListDoc = Jsoup.parse(responseJson.getString("html"))

		return pageListDoc.selectOrThrow("div").map {
			val url = it.selectFirstOrThrow("img").attr("src")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	protected open suspend fun getAvailableTags(): Set<MangaTag> = coroutineScope {
		val doc = webClient.httpGet("https://$domain/filter").parseHtml()
		doc.select("div.advanced-genres > div > .advance-item").mapToSet { element ->
			MangaTag(
				key = element.selectFirstOrThrow("span[data-genre]").attr("data-genre"),
				title = element.text().toTitleCase(sourceLocale),
				source = source,
			)
		}
	}
}
