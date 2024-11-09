package org.koitharu.kotatsu.parsers.site.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("SCANTRADUNION", "ScantradUnion", "fr")
internal class ScantradUnion(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.SCANTRADUNION, 10) {

	override val configKeyDomain = ConfigKey.Domain("scantrad-union.com")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
	)

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
					append("/page/")
					append(page.toString())
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				else -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/tag/")
							append(it.key)
							append("/page/")
							append(page.toString())
							append("/")
						}
					} else {
						if (order == SortOrder.ALPHABETICAL) {
							append("/manga/page/")
							append(page.toString())
							append("/")
						}

						if (order == SortOrder.UPDATED && page > 1) {
							return emptyList()
						}

					}

				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		if (doc.getElementById("dernierschapitres") != null) {
			val root = doc.requireElementById("dernierschapitres")
			return root.select("div.colonne")
				.map { article ->
					val href = article.selectFirstOrThrow("a.index-top4-a").attrAsRelativeUrl("href")
					Manga(
						id = generateUid(href),
						title = article.select(".carteinfos a").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = false,
						coverUrl = article.selectFirst("img.attachment-thumbnail")?.attrAsAbsoluteUrl("src").orEmpty(),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		} else {
			val root = doc.requireElementById("main")
			return root.select("article.post-outer")
				.map { article ->
					val href = article.selectFirstOrThrow("a.thumb-link").attrAsRelativeUrl("href")
					Manga(
						id = generateUid(href),
						title = article.select(".index-post-header a").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = false,
						coverUrl = article.selectFirst("img")?.attrAsAbsoluteUrl("src").orEmpty(),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("main")
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)

		return manga.copy(
			altTitle = root.select(".divider2:contains(Noms associés :)").firstOrNull()?.text(),
			state = when (root.select(".label.label-primary")[2].text()) {
				"En cours" -> MangaState.ONGOING
				"Terminé", "Abondonné", "One Shot" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("div.project-details a[href*=tag]").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = root.select("div.project-details a[href*=auteur]").text(),
			description = root.selectFirst("p.sContent")?.html(),
			chapters = root.select("div.chapter-list li")
				.mapChapters(reversed = true) { i, li ->

					val href = li.getElementsByTag("a").firstNotNullOf { a ->
						a.attrAsAbsoluteUrlOrNull("href")?.takeIf { it.contains("https://$domain/read/") }
					}

					val name = if (li.select(".chapter-name").isNullOrEmpty()) {
						li.select(".chapter-name").text()
					} else {
						"Chapter $i"
					}
					val date = li.select(".name-chapter").first()?.children()?.elementAt(2)?.text()
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i.toFloat(),
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(date),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("#webtoon")
			?: throw ParseException("Root not found", fullUrl)
		return root.select("img").map { img ->
			val url = img.attrAsRelativeUrlOrNull("data-src") ?: img.attrAsRelativeUrlOrNull("src")
			?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val body = doc.body()
		val list = body.select(".asp_gochosen")[1].select("option").orEmpty()
		return list.mapToSet { li ->
			MangaTag(
				key = li.text().lowercase().replace(" ", "-"),
				title = li.text(),
				source = source,
			)
		}
	}
}
