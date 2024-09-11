package org.koitharu.kotatsu.parsers.site.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("YAOIFLIX", "YaoiFlix", "tr", ContentType.HENTAI)
class YaoiFlix(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.YAOIFLIX, 8) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("www.yaoiflix.dev")

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
					if (page > 1) {
						append("/page/")
						append(page.toString())
					}
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/dizi-kategori/")
							append(it.key)
							append("/")
							if (page > 1) {
								append("page/")
								append(page.toString())
								append('/')
							}
						}
					} else {
						append("/tum-seriler/")
						if (page > 1) {
							append("page/")
							append(page.toString())
							append('/')
						}
					}
				}

				null -> {
					append("/tum-seriler/")
					if (page > 1) {
						append("page/")
						append(page.toString())
						append('/')
					}
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".list_items .series-box").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				title = div.selectLastOrThrow(".name").text(),
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		return doc.select(".tags .cat-item a").mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd MMM", sourceLocale)
		return manga.copy(
			description = doc.selectFirst(".description")?.html()?.substringAfterLast("<br>"),
			tags = doc.select(".category a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			chapters = doc.select(".serie-content .ep-box")
				.mapChapters { i, div ->
					val a = div.selectFirstOrThrow("a")
					val href = a.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = div.selectFirstOrThrow(".name").text(),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(div.selectFirstOrThrow(".date").text()),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".video-content img").map { img ->
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
