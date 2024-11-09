package org.koitharu.kotatsu.parsers.site.animebootstrap.fr

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.animebootstrap.AnimeBootstrapParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@Broken
@MangaSourceParser("PAPSCAN", "PapScan", "fr")
internal class PapScan(context: MangaLoaderContext) :
	AnimeBootstrapParser(context, MangaParserSource.PAPSCAN, "papscan.com") {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "/liste-manga"
	override val selectState = "div.anime__details__widget li:contains(En cours)"
	override val selectTag = "div.anime__details__widget li:contains(Genre) a"
	override val selectChapter = "ul.chapters li"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/filterList")
			append("?page=")
			append(page.toString())
			when {
				!filter.query.isNullOrEmpty() -> {
					append("&alpha=")
					append(filter.query.urlEncoded())
				}

				else -> {

					filter.tags.oneOrThrowIfMany()?.let {
						append("&cat=")
						append(it.key)
					}

					append("&sortBy=")
					when (order) {
						SortOrder.POPULARITY -> append("views")
						SortOrder.ALPHABETICAL_DESC -> append("name&asc=false")
						SortOrder.ALPHABETICAL -> append("name&asc=true")
						else -> append("updated")
					}

				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.product__item").map { div ->
			val href = div.selectFirstOrThrow("h5 a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirstOrThrow("div.product__item__pic").attr("data-setbg").orEmpty(),
				title = div.selectFirstOrThrow("div.product__item__text h5").text().orEmpty(),
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

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain$listUrl").parseHtml()
		return doc.select("a.category ").mapToSet { a ->
			val key = a.attr("href").substringAfterLast('=')
			val name = a.text()
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirstOrThrow(selectDesc).html()
		val state = if (doc.select(selectState).isNullOrEmpty()) {
			MangaState.FINISHED
		} else {
			MangaState.ONGOING
		}
		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val href = li.selectFirstOrThrow("a").attr("href")
			val dateText = li.selectFirst("span.date-chapter-title-rtl")?.text()
			MangaChapter(
				id = generateUid(href),
				name = li.selectFirstOrThrow("span em").text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = dateFormat.tryParse(dateText),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}
