package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("BEETOON", "BeeToon.net", "en")
internal class BeeToon(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.BEETOON, pageSize = 30) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override val configKeyDomain = ConfigKey.Domain("manhwafull.net")

	override val isMultipleTagsSupported = false

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					if (page > 1) {
						return emptyList()
					}
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					if (filter.tags.isNotEmpty()) {
						val tag = filter.tags.oneOrThrowIfMany()
						append("/genre/")
						append(tag?.key.orEmpty())
						append("/page-")
						append(page)
						append("/")
					} else {
						when (filter.sortOrder) {
							SortOrder.UPDATED -> append("/latest-update/")
							SortOrder.POPULARITY -> append("/popular-manga/")
							else -> append("/latest-update/")
						}
						append("page-")
						append(page)
						append("/")
					}
				}

				null -> append("/latest-update/page-$page/")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".comics-grid .entry").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst(".name")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst(".counter")?.text()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = when (div.selectLastOrThrow(".status span").text()) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		return doc.requireElementById("menu-item-3").select("ul.sub-menu li a").mapNotNullToSet {
			MangaTag(
				key = it.attr("href").removeSuffix('/').substringAfterLast('/'),
				title = it.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			description = doc.getElementById("desc")?.text().orEmpty(),
			rating = doc.selectFirst(".counter")?.text()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			tags = doc.body().select(".info .genre a").mapNotNullToSet {
				MangaTag(
					key = it.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = it.text(),
					source = source,
				)
			},
			author = doc.selectFirst(".info .author a")?.text(),
			chapters = doc.select(".items-chapters  a").mapChapters(reversed = true) { i, a ->
				val url = a.attrAsRelativeUrl("href").toAbsoluteUrl(domain)
				MangaChapter(
					id = generateUid(url),
					name = a.selectFirstOrThrow(".chap").text(),
					number = i + 1,
					url = url,
					scanlator = null,
					uploadDate = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.ENGLISH)
						.tryParse(a.selectFirst(".chapter-date")?.attr("title") ?: "0"),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select(".chapter-content-inner center img").map { img ->
			val urlPage = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(urlPage),
				url = urlPage,
				preview = null,
				source = source,
			)
		}
	}
}
