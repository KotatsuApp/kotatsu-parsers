package org.koitharu.kotatsu.parsers.site.pt

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
@MangaSourceParser("BRMANGAS", "BrMangas", "pt")
internal class BrMangas(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.BRMANGAS, 25) {

	override val configKeyDomain = ConfigKey.Domain("www.brmangas.net")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY, SortOrder.UPDATED)

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
			append('/')
			when {
				!filter.query.isNullOrEmpty() -> {
					if (page > 1) {
						append("/page/$page/")
					}
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				else -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("category/")
							append(it.key)
							if (page > 1) {
								append("/page/$page/")
							}
						}
					} else {
						when (order) {
							SortOrder.POPULARITY -> append("/")
							SortOrder.UPDATED -> append("manga/")
							else -> append("manga/")
						}
						if (page > 1) {
							append("page/$page/")
						}
					}

				}
			}

		}

		val doc = webClient.httpGet(url).parseHtml()

		val item = when {

			!filter.query.isNullOrEmpty() -> {
				doc.select("div.listagem div.item")
			}

			else -> {
				if (order == SortOrder.POPULARITY && filter.tags.isEmpty()) {
					doc.select("div.listagem")[1].select("div.item") // To remove the 6 mangas updated on the home page
				} else {
					doc.select("div.listagem div.item")
				}
			}
		}
		return item.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.selectFirstOrThrow("h2").text(),
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

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/lista-de-generos-de-manga/").parseHtml()
		return doc.select(".genres_page a").mapToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			altTitle = null,
			state = null,
			tags = doc.select("div.serie-infos li:contains(Categorias:) a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			author = doc.select("div.serie-infos li:contains(Autor:)").text().replace("Autor:", ""),
			description = doc.select(".serie-texto p").text(),
			isNsfw = doc.select("div.serie-infos li:contains(Categorias:)").text().contains("Hentai"),
			chapters = doc.select(".capitulos li a")
				.mapChapters { i, a ->
					val url = a.attrAsRelativeUrl("href")
					val name = a.text()
					MangaChapter(
						id = generateUid(url),
						name = name,
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
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val scriptData =
			doc.selectFirstOrThrow("script:containsData(imageArray)").data().substringAfter('[').substringBefore(']')
				.split(",")
		return scriptData.map { data ->
			val url = data.replace("\\\"", "").replace("\\/", "/")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
