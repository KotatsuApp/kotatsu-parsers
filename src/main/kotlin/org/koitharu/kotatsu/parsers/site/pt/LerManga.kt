package org.koitharu.kotatsu.parsers.site.pt

import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@Broken
@MangaSourceParser("LERMANGA", "LerManga", "pt")
class LerManga(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.LERMANGA, 24) {

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.POPULARITY,
			SortOrder.ALPHABETICAL,
			SortOrder.ALPHABETICAL_DESC,
			SortOrder.RATING,
		)

	override val configKeyDomain = ConfigKey.Domain("lermanga.org")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val isMultipleTagsSupported = false
	override val isSearchSupported = false

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			append("/mangas")

			if (page > 1) {
				append("/page/")
				append(page.toString())
			}

			when (filter) {

				is MangaListFilter.Search -> {
					throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED)
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/genero/")
							append(it.key)
						}
					}

					append("/?orderby=")
					append(
						when (filter.sortOrder) {
							SortOrder.UPDATED -> "modified&order=desc"
							SortOrder.POPULARITY -> "views&order=desc"
							SortOrder.ALPHABETICAL -> "title&order=asc"
							SortOrder.ALPHABETICAL_DESC -> "title&order=desc"
							SortOrder.RATING -> "rating&order=desc"
							else -> "modified&order=desc"
						},
					)
				}

				null -> append("/?orderby=modified&order=desc")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".tab-content .flw-item").map { div ->
			val a = div.selectFirstOrThrow("a.film-poster-ahref")
			val href = a.attrAsAbsoluteUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				title = div.selectLastOrThrow("h3.film-name").text(),
				coverUrl = div.selectFirst("img.film-poster-img")?.src().orEmpty(),
				altTitle = null,
				rating = div.selectFirst(".item__rating")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				description = null,
				state = null,
				author = null,
				isNsfw = div.selectFirst(".tick-itemadult") != null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml().requireElementById("menu-header")
		return doc.select("#menu-item:contains(GÊNERO) ul li a").mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix("/").substringAfterLast("/"),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", sourceLocale)
		return manga.copy(
			description = doc.selectFirstOrThrow("div.boxAnimeSobreLast p").html(),
			tags = doc.selectFirst("ul.genre-list")?.select("li a")?.mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast("/"),
					title = a.text(),
					source = source,
				)
			}.orEmpty(),
			isNsfw = doc.select("ul.genre-list li").text().contains("Adulto"),
			chapters = doc.select("div.manga-chapters div.single-chapter").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsAbsoluteUrl("href")
				MangaChapter(
					id = generateUid(href),
					name = a.text(),
					number = i + 1f,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(div.selectFirstOrThrow("small small").text()),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val script = doc.selectFirstOrThrow(".heading-header + script").attr("src")
		val data = Base64.getDecoder().decode(script.replace("data:text/javascript;base64,", "")).decodeToString()
		val images =
			data.substringAfter("var imagens_cap=[").substringBeforeLast("]").replace("\\", "").replace("\"", "")
				.split(",")
		return images.map { img ->
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}
}
