package org.koitharu.kotatsu.parsers.site.ar

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TEAMXNOVEL", "TeamXNovel", "ar")
internal class TeamXNovel(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.TEAMXNOVEL, 10) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)
	override val availableStates: Set<MangaState> =
		EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED)

	override val configKeyDomain = ConfigKey.Domain("teamoney.site")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val isMultipleTagsSupported = false

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					append("/?search=")
					append(filter.query.urlEncoded())
					if (page > 1) {
						append("&page=")
						append(page.toString())
					}
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty()) {
						val tag = filter.tags.oneOrThrowIfMany()
						append("/series?genre=")
						append(tag?.key.orEmpty())
						if (page > 1) {
							append("&page=")
							append(page.toString())
						}
						append("&")
					} else {
						when (filter.sortOrder) {
							SortOrder.POPULARITY -> append("/series")
							SortOrder.UPDATED -> append("/")
							else -> append("/")
						}
						if (page > 1) {
							append("?page=")
							append(page.toString())
							append("&")
						} else {
							append("?")
						}
					}

					if (filter.sortOrder == SortOrder.POPULARITY || filter.tags.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append("status=")
							append(
								when (it) {
									MangaState.ONGOING -> "مستمرة"
									MangaState.FINISHED -> "مكتمل"
									MangaState.ABANDONED -> "متوقف"
									else -> "مستمرة"
								},
							)
						}
					}
				}

				null -> append("/?page=$page")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.listupd .bs .bsx").ifEmpty {
			doc.select("div.post-body .box")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(".tt, h3").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirstOrThrow("img").src()?.replace("thumbnail_", "").orEmpty(),
				tags = emptySet(),
				state = when (div.selectFirst(".status")?.text()) {
					"مستمرة" -> MangaState.ONGOING
					"مكتمل" -> MangaState.FINISHED
					"متوقف" -> MangaState.ABANDONED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/series").parseHtml()
		return doc.requireElementById("select_genre").select("option").mapNotNullToSet {
			MangaTag(
				key = it.attr("value"),
				title = it.text().toTitleCase(sourceLocale),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val mangaUrl = manga.url.toAbsoluteUrl(domain)
		val maxPageChapterSelect = doc.select(".pagination .page-item a")
		var maxPageChapter = 1
		if (!maxPageChapterSelect.isNullOrEmpty()) {
			maxPageChapterSelect.map {
				val i = it.attr("href").substringAfterLast("=").toInt()
				if (i > maxPageChapter) {
					maxPageChapter = i
				}
			}
		}
		return manga.copy(
			altTitle = null,
			state = when (doc.selectFirstOrThrow(".full-list-info:contains(الحالة:) a").text()) {
				"مستمرة" -> MangaState.ONGOING
				"مكتمل" -> MangaState.FINISHED
				"متوقف" -> MangaState.ABANDONED
				else -> null
			},
			tags = doc.select(".review-author-info a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("="),
					title = a.text(),
					source = source,
				)
			},
			author = null,
			description = doc.selectFirstOrThrow(".review-content").text(),
			chapters = run {
				if (maxPageChapter == 1) {
					parseChapters(doc)
				} else {
					coroutineScope {
						val result = ArrayList(parseChapters(doc))
						result.ensureCapacity(result.size * maxPageChapter)
						(2..maxPageChapter).map { i ->
							async {
								loadChapters(mangaUrl, i)
							}
						}.awaitAll()
							.flattenTo(result)
						result
					}
				}
			}.reversed(),
		)
	}

	private suspend fun loadChapters(baseUrl: String, page: Int): List<MangaChapter> {
		return parseChapters(webClient.httpGet("$baseUrl?page=$page").parseHtml().body())
	}

	private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", sourceLocale)

	private fun parseChapters(root: Element): List<MangaChapter> {
		return root.requireElementById("chapter-contact").select(".eplister ul li")
			.map { li ->
				val url = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = li.selectFirstOrThrow(".epl-title").text(),
					number = url.substringAfterLast('/').toFloatOrNull() ?: 0f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(li.selectFirstOrThrow(".epl-date").text()),
					branch = null,
					source = source,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".image_list img").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
