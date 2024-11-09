package org.koitharu.kotatsu.parsers.site.fr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@Broken
@MangaSourceParser("LIRESCAN", "LireScan", "fr")
internal class LireScan(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.LIRESCAN, 20) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("lire-scan.me")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val doc = when {
			!filter.query.isNullOrEmpty() -> {
				if (page > 1) {
					return emptyList()
				}
				val q = filter.query.urlEncoded().replace("%20", "+")
				val post = "do=search&subaction=search&search_start=0&full_search=0&result_from=1&story=$q"
				webClient.httpPost("https://$domain/index.php?do=search", post).parseHtml()
			}

			else -> {
				val url = buildString {
					append("https://")
					append(domain)

					filter.tags.oneOrThrowIfMany()?.let {
						append("/manga/")
						append(it.key)
					}

					if (page > 1) {
						append("/page/")
						append(page)
						append('/')
					}
				}
				webClient.httpGet(url).parseHtml()
			}
		}

		return doc.select("div.sect__content.grid-items div.item-poster").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(".item-poster__title").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = div.selectFirstOrThrow(".item__rating").ownText().toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
				tags = setOf(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)
		return manga.copy(
			altTitle = root.select("ul.pmovie__list li:contains(Nom Alternatif:)").text()
				.replace("Nom Alternatif:", ""),
			state = when (root.select("ul.pmovie__list li:contains(Status:)").text()) {
				"Status: OnGoing", "Status: En cours" -> MangaState.ONGOING
				"Status: Fini" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("ul.pmovie__list li:contains(Genre:)").text()
				.replace("Genre:", "").split(" / ").mapToSet { tag ->
					MangaTag(
						key = tag.lowercase(),
						title = tag,
						source = source,
					)
				},
			author = root.select("ul.pmovie__list li:contains(Artist(s):)").text().replace("Artist(s):", ""),
			description = root.selectFirst("div.pmovie__text")?.html(),
			chapters = root.select("ul li div.chapter")
				.mapChapters(reversed = true) { i, div ->
					val a = div.selectFirstOrThrow("a")
					val href = a.attrAsRelativeUrl("href")
					val name = a.text()
					val dateText = div.select("p").last()?.text()
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i.toFloat(),
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

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val pages = doc.selectFirstOrThrow("script:containsData(const manga = )").data()
			.substringAfter("chapter1: [\"").substringBefore("\"]")
			.split("\",\"")
		return pages.map { img ->
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		return doc.select(".nav-menu li a").mapToSet { a ->
			val key = a.attr("href").removeSuffix('/').substringAfterLast("manga/", "")
			MangaTag(
				key = key,
				title = a.text(),
				source = source,
			)
		}
	}
}
