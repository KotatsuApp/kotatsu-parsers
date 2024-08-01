package org.koitharu.kotatsu.parsers.site.mangabox.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAKAKALOT", "Mangakakalot.com", "en")
internal class Mangakakalot(context: MangaLoaderContext) :
	MangaboxParser(context, MangaParserSource.MANGAKAKALOT) {
	override val configKeyDomain = ConfigKey.Domain("mangakakalot.com", "chapmanganato.com")
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)
	override val isTagsExclusionSupported = false
	override val isMultipleTagsSupported = false
	override val otherDomain = "chapmanganato.com"
	override val listUrl = "/manga_list"

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					append(searchUrl)
					val regex = Regex("[^A-Za-z0-9 ]")
					val q = regex.replace(filter.query, "")
					append(q.replace(" ", "_"))
					append("?page=")
				}

				is MangaListFilter.Advanced -> {
					append(listUrl)
					append("?type=")
					when (filter.sortOrder) {
						SortOrder.POPULARITY -> append("topview")
						SortOrder.UPDATED -> append("latest")
						SortOrder.NEWEST -> append("newest")
						else -> append("latest")
					}
					if (filter.tags.isNotEmpty()) {
						append("&category=")
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&state=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "completed"
								else -> "all"
							},
						)
					}

					append("&page=")
				}

				null -> {
					append(listUrl)
					append("?type=latest&page=")
				}
			}
			append(page.toString())
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.list-truyen-item-wrap").ifEmpty {
			doc.select("div.story_item")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		val tags = doc.select("ul.tag li a").drop(1)
		return tags.mapNotNullToSet { a ->
			val key = a.attr("href").substringAfterLast("category=").substringBefore("&")
			val name = a.attr("title").replace(" Manga", "")
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.select(selectDate).last()?.text() ?: "0"
			val dateFormat = if (dateText.contains("-")) {
				SimpleDateFormat("MMM-dd-yy", sourceLocale)
			} else {
				SimpleDateFormat(datePattern, sourceLocale)
			}

			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}
