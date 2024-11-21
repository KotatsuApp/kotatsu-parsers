package org.koitharu.kotatsu.parsers.site.ar

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@Broken
@MangaSourceParser("MANGASTORM", "MangaStorm", "ar")
internal class MangaStorm(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MANGASTORM, 30) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY, SortOrder.UPDATED)
	override val configKeyDomain = ConfigKey.Domain("mangastorm.org")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/mangas?page=")
					append(page)
					append("&query=")
					append(filter.query.urlEncoded())
				}

				else -> {

					if (filter.tags.isNotEmpty()) {
						val tag = filter.tags.oneOrThrowIfMany()
						append("/categories/")
						append(tag?.key.orEmpty())
						append("?page=")
						append(page)
					} else {
						if (order == SortOrder.POPULARITY) {
							append("/mangas?page=")
							append(page)
						} else {
							if (page > 1) {
								return emptyList()
							}
						}
					}
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.row div.col").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(".manga-ct-title").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.selectFirstOrThrow(".card-body .col-lg-9")
		return manga.copy(
			altTitle = null,
			state = null,
			tags = root.select(".flex-wrap a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			author = null,
			description = root.selectFirstOrThrow(".card-text").text(),
			chapters = doc.select(".card-body a.btn-fixed-width").mapChapters(reversed = true) { i, a ->
				val url = a.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = a.text(),
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml().requireElementById("content")
		return doc.select("div.text-center .img-fluid").map { img ->
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
