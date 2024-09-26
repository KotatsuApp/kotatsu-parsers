package org.koitharu.kotatsu.parsers.site.scan

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.unescapeJson
import java.text.SimpleDateFormat
import java.util.*

internal abstract class ScanParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 0,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = getOrCreateTagMap().values.toSet(),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.ALPHABETICAL, SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.RATING)

	protected open val listUrl = "/manga"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		var query = false

		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/search?q=")
					append(filter.query.urlEncoded())
					query = true
				}

				else -> {

					append(listUrl)
					append("?q=")
					append(
						when (order) {
							SortOrder.UPDATED -> "u"
							SortOrder.ALPHABETICAL -> "a"
							SortOrder.POPULARITY -> "p"
							SortOrder.RATING -> "r"
							else -> "u"
						},
					)

					filter.tags.forEach {
						append("&search[tags][]=")
						append(it.key)
					}

					append("&page=")
					append(page.toString())
				}
			}
		}

		val doc = if (query) {
			val raw = webClient.httpGet(url).parseRaw()
			Jsoup.parseBodyFragment(
				raw.unescapeJson(),
				domain,
			)

		} else {
			webClient.httpGet(url).parseHtml()
		}

		return doc.select(".series, .series-paginated .grid-item-series").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.attr("data-src")?.replace("\t", "").orEmpty(),
				title = div.selectFirst(".link-series h3, .item-title")?.text().orEmpty(),
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

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()

	protected suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val tagElements = webClient.httpGet("https://$domain$listUrl").parseHtml()
			.requireElementById("filter-wrapper")
			.select(".form-filters div.form-check, .form-filters div.custom-control")
		for (el in tagElements) {
			val name = el.selectFirstOrThrow("label").text()
			if (name.isEmpty()) continue
			tagMap[name] = MangaTag(
				key = el.selectFirstOrThrow("input").attr("value"),
				title = name,
				source = source,
			)
		}
		tagCache = tagMap
		return@withLock tagMap
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("MM-dd-yyyy", sourceLocale)
		val tagMap = getOrCreateTagMap()
		val selectTag =
			doc.select(".card-series-detail .col-6:contains(Categorie) div, .card-series-about .mb-3:contains(Categorie) a, .card-series-about .mb-3:contains(Categorias) a")
		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }
		return manga.copy(
			rating = doc.selectFirst(".card-series-detail .rate-value span, .card-series-about .rate-value span")
				?.ownText()?.toFloatOrNull()?.div(5f)
				?: RATING_UNKNOWN,
			tags = tags,
			author = doc.selectFirst(".card-series-detail .col-6:contains(Autore) div, .card-series-about .mb-3:contains(Autore) a")
				?.text(),
			altTitle = doc.selectFirst(".card div.col-12.mb-4 h2, .card-series-about .h6")?.text().orEmpty(),
			description = doc.selectFirst(".card div.col-12.mb-4 p, .card-series-desc .mb-4 p")?.html().orEmpty(),
			chapters = doc.select(".chapters-list .col-chapter, .card-list-chapter .col-chapter")
				.mapChapters(reversed = true) { i, div ->
					val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = div.selectFirst("h5")?.html()?.substringBefore("<div")?.substringAfter("</span>")
							.orEmpty(),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(doc.selectFirst("h5 div")?.text()),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val pages = ArrayList<MangaPage>()
		var n = 0
		while (true) {
			++n
			val img = webClient.httpGet("$fullUrl/$n").parseHtml().selectFirst(".book-page .img-fluid")?.src() ?: break
			pages.add(
				MangaPage(
					id = generateUid(img),
					url = img,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
