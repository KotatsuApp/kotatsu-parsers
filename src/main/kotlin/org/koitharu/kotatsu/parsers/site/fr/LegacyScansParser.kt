package org.koitharu.kotatsu.parsers.site.fr

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("LEGACY_SCANS", "LegacyScans", "fr")
internal class LegacyScansParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.LEGACY_SCANS, 18) {

	override val configKeyDomain = ConfigKey.Domain("legacy-scans.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY, SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED, MangaState.PAUSED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.ONE_SHOT,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val end = page * pageSize
		val start = end - (pageSize - 1)

		when {
			!filter.query.isNullOrEmpty() -> {
				if (page > 1) {
					return emptyList()
				}
				val url = buildString {
					append("https://api.$domain/misc/home/search?title=")
					append(filter.query.urlEncoded())
				}
				return parseMangaListQuery(webClient.httpGet(url).parseJson())
			}

			else -> {

				if (order == SortOrder.UPDATED) {

					if (filter.states.isNotEmpty() || filter.tags.isNotEmpty() || filter.types.isNotEmpty()) {
						throw IllegalArgumentException("La recherche part mis à jour + des filtres n'est pas supporté par cette source.")
					}

					val url = buildString {
						append("https://api.")
						append(domain)
						append("/misc/comic/home/updates?start=")
						append(start.toString())
						append("&end=")
						append(end.toString())
					}

					return parseMangaList(webClient.httpGet(url).parseJson().getJSONArray("results"))

				} else {
					val url = buildString {
						append("https://api.")
						append(domain)
						append("/misc/comic/search/query?status=")
						filter.states.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									MangaState.ONGOING -> "En cours"
									MangaState.FINISHED -> "Terminé"
									MangaState.ABANDONED -> "Abandonné"
									MangaState.PAUSED -> "En pause"
									else -> ""
								},
							)
						}

						append("&order=&genreNames=")
						append(filter.tags.joinToString(",") { it.key })

						append("&type=")
						filter.types.forEach {
							append(
								when (it) {
									ContentType.MANGA -> "Manga"
									ContentType.MANHWA -> "Manhwa"
									ContentType.MANHUA -> "Manhua"
									ContentType.ONE_SHOT -> "One shot"
									else -> ""
								},
							)
						}

						append("&start=")
						append(start.toString())
						append("&end=")
						append(end.toString())
					}

					return parseMangaList(webClient.httpGet(url).parseJson().getJSONArray("comics"))
				}


			}
		}
	}


	private fun parseMangaList(json: JSONArray): List<Manga> {
		return json.mapJSON { j ->
			val slug = j.getString("slug")
			val urlManga = "https://$domain/comics/$slug"
			Manga(
				id = generateUid(urlManga),
				title = j.getString("title"),
				altTitle = null,
				url = urlManga,
				publicUrl = urlManga,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = "https://api.$domain/" + j.getString("cover"),
				tags = setOf(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	private fun parseMangaListQuery(json: JSONObject): List<Manga> {
		return json.getJSONArray("results").mapJSON { j ->
			val slug = j.getString("slug")
			val urlManga = "https://$domain/comics/$slug"
			Manga(
				id = generateUid(urlManga),
				title = j.getString("title"),
				altTitle = null,
				url = urlManga,
				publicUrl = urlManga,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = "",
				tags = setOf(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
		return manga.copy(
			altTitle = null,
			tags = root.select("div.serieGenre span").mapToSet { span ->
				MangaTag(
					key = span.text(),
					title = span.text(),
					source = source,
				)
			},
			coverUrl = root.selectFirst("div.serieImg img")?.attr("src").orEmpty(),
			author = root.select("div.serieAdd p:contains(Auteur:) strong").text(),
			description = root.selectFirst("div.serieDescription div")?.html(),
			chapters = root.select("div.chapterList a")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirst("span")?.text()
					val dateText = a.selectLast("span")?.text() ?: "0"
					MangaChapter(
						id = generateUid(href),
						name = name ?: "Chapitre : ${i + 1f}",
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

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.readerComics img").map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comics").parseHtml()
		val script = doc.requireElementById("__NUXT_DATA__").data()
			.substringAfterLast("\"genres\"").substringBeforeLast("\"comics\"")
			.split("\",\"").drop(1)
		return script.mapToSet { tag ->
			MangaTag(
				key = tag.substringBeforeLast("\",{"),
				title = tag.substringBeforeLast("\",{"),
				source = source,
			)
		}
	}
}
