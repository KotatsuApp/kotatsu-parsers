package org.koitharu.kotatsu.parsers.site.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRUYENQQ", "TruyenQQ", "vi")
internal class TruyenQQ(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.TRUYENQQ, 42) {

	override val configKeyDomain = ConfigKey.Domain("truyenqqto.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.UPDATED_ASC,
			SortOrder.POPULARITY,
			SortOrder.POPULARITY_ASC,
			SortOrder.NEWEST,
			SortOrder.NEWEST_ASC,
		)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.COMICS,
			ContentType.OTHER,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = when {
			!filter.query.isNullOrEmpty() -> {
				buildString {
					append("https://")
					append(domain)
					append("/tim-kiem/trang-$page.html")
					append("?q=")
					append(filter.query.urlEncoded())
				}
			}

			else -> {
				buildString {
					append("https://")
					append(domain)
					append("/tim-kiem-nang-cao/trang-")
					append(page.toString())
					append(".html?country=")

					if (filter.types.isNotEmpty()) {
						filter.types.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									ContentType.MANHUA -> '1'
									ContentType.OTHER -> '2' // Việt Nam
									ContentType.MANHWA -> '3'
									ContentType.MANGA -> '4'
									ContentType.COMICS -> '5'
									else -> '0' // all
								},
							)
						}
					} else append('0')


					append("&sort=")
					when (order) {
						SortOrder.NEWEST -> append('0')
						SortOrder.NEWEST_ASC -> append('1')
						SortOrder.UPDATED -> append('2')
						SortOrder.UPDATED_ASC -> append('3')
						SortOrder.POPULARITY -> append('4')
						SortOrder.POPULARITY_ASC -> append('5')
						else -> append('2')
					}

					append("&status=")
					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {

							append(
								when (it) {
									MangaState.ONGOING -> '0'
									MangaState.FINISHED -> '1'
									else -> "-1"
								},
							)
						}
					} else {
						append("-1")
					}

					append("&category=")
					append(filter.tags.joinToString(separator = ",") { it.key })

					append("&notcategory=")
					append(filter.tagsExclude.joinToString(separator = ",") { it.key })

					append("&minchapter=0")
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.requireElementById("main_homepage").select("li").map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = li.selectFirst(".book_name")?.text().orEmpty(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = li.selectFirst("img")?.src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem-nang-cao.html").parseHtml()
		return doc.select(".advsearch-form div.genre-item").mapToSet {
			MangaTag(
				key = it.selectFirstOrThrow("span").attr("data-id"),
				title = it.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
		return manga.copy(
			altTitle = doc.selectFirst("h2.other-name")?.text(),
			tags = doc.select("ul.list01 li").mapToSet {
				val key = it.attr("href").substringAfterLast("-").substringBeforeLast(".")
				MangaTag(
					key = key,
					title = it.text(),
					source = source,
				)
			},
			state = when (doc.selectFirst(".status p.col-xs-9")?.text()) {
				"Đang Cập Nhật" -> MangaState.ONGOING
				"Hoàn Thành" -> MangaState.FINISHED
				else -> null
			},
			author = doc.selectFirst("li.author a")?.text(),
			description = doc.selectFirst(".story-detail-info")?.html(),
			chapters = doc.select("div.list_chapter div.works-chapter-item").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val name = a.text()
				val dateText = div.selectFirst(".time-chap")?.text()
				MangaChapter(
					id = generateUid(href),
					name = name,
					number = i + 1f,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(dateText),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirstOrThrow(".chapter_content")
		return root.select("div.page-chapter").map { div ->
			val img = div.selectFirstOrThrow("img")
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
