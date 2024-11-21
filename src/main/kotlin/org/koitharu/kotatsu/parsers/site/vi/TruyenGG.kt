package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.arraySetOf
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRUYENGG", "TruyenGG", "vi")
internal class TruyenGG(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.TRUYENGG, 42) {

	override val configKeyDomain = ConfigKey.Domain("truyengg.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
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
									ContentType.OTHER -> '4' // Việt Nam
									ContentType.MANHWA -> '2'
									ContentType.MANGA -> '3'
									ContentType.COMICS -> '5'
									else -> '0' // all
								},
							)
						}
					} else append('0')

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
					filter.tags.joinTo(this, separator = ",") { it.key }

					append("&notcategory=")
					filter.tagsExclude.joinTo(this, separator = ",") { it.key }

					append("&minchapter=0")

					append("&sort=")
					append(
						when (order) {
							SortOrder.NEWEST -> "0"
							SortOrder.NEWEST_ASC -> "1"
							SortOrder.UPDATED -> "2"
							SortOrder.UPDATED_ASC -> "3"
							SortOrder.POPULARITY -> "4"
							SortOrder.POPULARITY_ASC -> "5"
							else -> "2"
						},
					)
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".list_item_home .item_home").map { div ->
			val href = div.selectFirstOrThrow("a.book_name").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select("a.book_name").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = div.selectFirst(".image-cover img")?.attr("data-src").orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

		return manga.copy(
			altTitle = doc.selectFirst("h2.other-name")?.text(),
			author = doc.select("p:contains(Tác Giả) + p").joinToString { it.text() }.takeUnless { it.isEmpty() },
			tags = doc.select("a.clblue").mapToSet {
				MangaTag(
					key = it.attr("href").substringAfterLast('-').substringBeforeLast('.'),
					title = it.text(),
					source = source,
				)
			},
			description = doc.select("div.story-detail-info").text(),
			state = when (doc.select("p:contains(Trạng Thái) + p").text()) {
				"Đang Cập Nhật" -> MangaState.ONGOING
				"Hoàn Thành" -> MangaState.FINISHED
				else -> null
			},
			chapters = doc.select("ul.list_chap > li.item_chap").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val name = a.text()
				val dateText = div.select("span.cl99").text()
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
		return doc.select(".content_detail img").map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
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
} 
