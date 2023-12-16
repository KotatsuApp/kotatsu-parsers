package org.koitharu.kotatsu.parsers.site.fr

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("SCANVFORG", "ScanVfOrg", "fr")
internal class ScanVfOrg(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.SCANVFORG, 0) {

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.ALPHABETICAL, SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.RATING)

	override val configKeyDomain = ConfigKey.Domain("scanvf.org")

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED) // TODO
				}

				is MangaListFilter.Advanced -> {

					append("/manga")
					append("?q=")
					append(
						when (filter.sortOrder) {
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

				null -> {
					append("/manga?page=")
					append(page.toString())
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".series-paginated .series").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow(".link-series h3").text().orEmpty(),
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return getOrCreateTagMap().values.toSet()
	}

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val tagElements = webClient.httpGet("https://$domain/manga").parseHtml()
			.requireElementById("filter-wrapper").select(".form-filters div.form-check")
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
		val selectTag = doc.select(".card-series-detail .col-6:contains(Categories) div")
		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }
		return manga.copy(
			rating = doc.selectFirst(".card-series-detail .rate-value span")?.ownText()?.toFloatOrNull()?.div(5f)
				?: RATING_UNKNOWN,
			tags = tags,
			author = doc.selectFirst(".card-series-detail .col-6:contains(Auteur) div")?.text(),
			altTitle = doc.selectFirst(".card div.col-12.mb-4 h2")?.text().orEmpty(),
			description = doc.selectFirst(".card div.col-12.mb-4 p")?.html().orEmpty(),
			chapters = doc.select(".list-books .col-chapter").mapChapters(reversed = true) { i, div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(href),
					name = div.selectFirstOrThrow("h5").html().substringBefore("<div").substringAfter("</span>"),
					number = i + 1,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(doc.selectFirstOrThrow("h5 div").text()),
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
