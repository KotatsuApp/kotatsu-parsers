package org.koitharu.kotatsu.parsers.site.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRUYENQQ", "Truyenqq", "vi")
internal class Truyenqq(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.TRUYENQQ, 42) {

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST)

	override val availableStates: Set<MangaState> = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)

	override val configKeyDomain = ConfigKey.Domain("truyenqqto.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url =
			when (filter) {
				is MangaListFilter.Search -> {
					buildString {
						append("https://")
						append(domain)
						append("/tim-kiem/trang-$page.html")
						append("?q=")
						append(filter.query.urlEncoded())
					}
				}

				is MangaListFilter.Advanced -> {
					buildString {
						append("https://")
						append(domain)
						append("/tim-kiem-nang-cao/trang-")
						append(page.toString())
						append(".html?country=0&sort=")
						when (filter.sortOrder) {
							SortOrder.POPULARITY -> append("4")
							SortOrder.UPDATED -> append("2")
							SortOrder.NEWEST -> append("0")
							else -> append("2")
						}
						if (filter.states.isNotEmpty()) {
							filter.states.oneOrThrowIfMany()?.let {
								append("&status=")
								append(
									when (it) {
										MangaState.ONGOING -> "0"
										MangaState.FINISHED -> "1"
										else -> "-1"
									},
								)
							}
						} else {
							append("&status=-1")
						}

						append("&category=")
						append(filter.tags.joinToString(separator = ",") { it.key })
						append("&notcategory=&minchapter=0")
					}
				}

				null -> {
					buildString {
						append("https://")
						append(domain)
						append("/tim-kiem-nang-cao/trang-")
						append(page.toString())
						append(".html?status=-1&country=0&sort=2&category=&notcategory=&minchapter=0")
					}

				}
			}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.requireElementById("main_homepage").select("li").map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = li.selectFirstOrThrow(".book_name").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = li.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem-nang-cao.html").parseHtml()
		return doc.select(".advsearch-form div.genre-item").mapNotNullToSet {
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
			tags = doc.select("ul.list01 li").mapNotNullToSet {
				val key = it.attr("href").substringAfterLast("-").substringBeforeLast(".")
				MangaTag(
					key = key,
					title = it.text(),
					source = source,
				)
			},
			state = when (doc.selectFirstOrThrow(".status p.col-xs-9").text()) {
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
				val dateText = div.selectFirstOrThrow(".time-chap").text()
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
			val url = img.src()?.toRelativeUrl(domain) ?: div.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
