package org.koitharu.kotatsu.parsers.site.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAONLINE", "MangaOnline.biz", "pt")
internal class MangaOnline(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MANGAONLINE, 20) {

	override val configKeyDomain = ConfigKey.Domain("mangaonline.biz")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)


	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {

				!filter.query.isNullOrEmpty() -> {
					append("/search/")
					append(filter.query.urlEncoded())
				}

				else -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/genero/")
							append(it.key)
						}
					} else {
						append("/manga")
					}
				}

			}
			if (page > 1) {
				append("/page/")
				append(page.toString())
				append('/')
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".items .item, .items2 .item").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				title = div.selectLast(".data h3")?.text().orEmpty(),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				altTitle = null,
				rating = div.selectFirst(".rating")?.ownText()?.toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				description = null,
				state = null,
				author = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/generos/").parseHtml()
		return doc.select(".wp-content p a").mapToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix("/").substringAfterLast("/", ""),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
		return manga.copy(
			description = doc.selectLast(".data p")?.html(),
			tags = doc.selectFirst(".sgeneros")?.select("a")?.mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast("/", ""),
					title = a.text(),
					source = source,
				)
			}.orEmpty(),
			chapters = doc.select(".episodios li a").mapChapters(reversed = true) { i, a ->
				val href = a.attrAsRelativeUrl("href")
				val title = a.html().substringBeforeLast("<span")
				val dateText = a.selectFirst("span.date")?.text()
				MangaChapter(
					id = generateUid(href),
					name = title,
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

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val doc =
			webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("single_relacionados")
		return doc.select(".owl-item").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				title = div.selectLast(".reltitle h3")?.text().orEmpty(),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = null,
				state = null,
				author = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".content p img").map { img ->
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
