package org.koitharu.kotatsu.parsers.site.tr

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAAY", "MangaAy", "tr")
internal class MangaAy(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MANGAAY, 45) {

	override val configKeyDomain = ConfigKey.Domain("manga-ay.com")

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
		availableTags = getOrCreateTagMap().values.toSet(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		when {
			!filter.query.isNullOrEmpty() -> {
				if (page > 1) {
					return emptyList()
				}
				return parseMangaListQueryOrTags(
					webClient.httpPost(
						"https://$domain/arama",
						mapOf("title" to filter.query.urlEncoded(), "genres" to ""),
					).parseHtml(),
				)
			}

			else -> {

				if (filter.tags.isNotEmpty()) {
					filter.tags.oneOrThrowIfMany()?.let {
						if (page > 1) {
							return emptyList()
						}
						return parseMangaListQueryOrTags(
							webClient.httpPost(
								"https://$domain/arama",
								mapOf("title" to "", "genres" to it.key),
							).parseHtml(),
						)
					}
				} else {
					val url = buildString {
						append("https://")
						append(domain)
						append("/seriler")
						if (page > 1) {
							append('/')
							append(page)
						}
					}
					return parseMangaList(webClient.httpGet(url).parseHtml())
				}

			}
		}

		return emptyList()
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.requireElementById("ecommerce-products").select(".card").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				title = div.selectLast(".item-name")?.text().orEmpty(),
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

	private fun parseMangaListQueryOrTags(doc: Document): List<Manga> {
		return doc.select(".table tr").map { tr ->
			val a = tr.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				title = a.text(),
				coverUrl = "",
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

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val tagElements = webClient.httpGet("https://$domain/arama").parseHtml()
			.requireElementById("genres").select("option")
		for (option in tagElements) {
			if (option.text().isEmpty()) continue
			tagMap[option.text()] = MangaTag(
				key = option.attr("value"),
				title = option.text().toTitleCase(sourceLocale),
				source = source,
			)
		}
		tagCache = tagMap
		return@withLock tagMap
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT)
		val tagMap = getOrCreateTagMap()
		val tags = doc.select("P.card-text .bg-success").mapNotNullToSet { tagMap[it.text()] }
		return manga.copy(
			description = doc.selectFirst("p.card-text")?.html()?.substringAfterLast("<br>"),
			coverUrl = doc.selectFirst("div.align-items-center div.align-items-center img")?.src().orEmpty(),
			tags = tags,
			chapters = doc.requireElementById("sonyuklemeler").select("tbody tr")
				.mapChapters(reversed = true) { i, tr ->
					val a = tr.selectFirstOrThrow("a")
					val href = a.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(tr.selectFirstOrThrow("time").attr("datetime")),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.mt-2 img").map { img ->
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
