package org.koitharu.kotatsu.parsers.site.fr

import org.json.JSONArray
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getFloatOrDefault
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("LUGNICASCANS", "LugnicaScans", "fr")
internal class LugnicaScans(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.LUGNICASCANS, 10) {

	override val configKeyDomain = ConfigKey.Domain("lugnica-scans.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED),
	)

	init {
		context.cookieJar.insertCookies(
			domain,
			"reader_render=continue;",
		)
	}

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon("https://$domain/favicon/favicon-32x32.png", 32, null),
			),
			domain,
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		when {
			!filter.query.isNullOrEmpty() -> {
				throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED)
			}

			else -> {

				if (order == SortOrder.ALPHABETICAL) {
					if (page > 1) {
						return emptyList()
					}
					val url = buildString {
						append("https://")
						append(domain)
						append("/api/get/catalog?page=0&filter=")
						filter.states.oneOrThrowIfMany()?.let {
							when (it) {
								MangaState.ONGOING -> append("0")
								MangaState.FINISHED -> append("1")
								MangaState.PAUSED -> append("4")
								MangaState.ABANDONED -> append("3")
								else -> append("")
							}
						}


					}
					return parseMangaListAlpha(webClient.httpGet(url).parseJsonArray())
				} else {
					val url = buildString {
						append("https://")
						append(domain)
						append("/api/get/homegrid/")
						append(page)
					}
					return parseMangaList(webClient.httpGet(url).parseJsonArray())
				}
			}
		}
	}

	private fun parseMangaList(json: JSONArray): List<Manga> {
		return json.mapJSON { j ->
			val urlManga = "https://$domain/api/get/card/${j.getString("manga_slug")}"
			val img = "https://$domain/upload/min_cover/${j.getString("manga_image")}"
			Manga(
				id = generateUid(urlManga),
				title = j.getString("manga_title"),
				altTitle = null,
				url = urlManga.toRelativeUrl(domain),
				publicUrl = urlManga.toAbsoluteUrl(domain),
				rating = j.getFloatOrDefault("manga_rate", RATING_UNKNOWN).div(5f),
				isNsfw = false,
				coverUrl = img,
				tags = setOf(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	private fun parseMangaListAlpha(json: JSONArray): List<Manga> {
		return json.mapJSON { j ->
			val urlManga = "https://$domain/api/get/card/${j.getString("slug")}"
			val img = "https://$domain/upload/min_cover/${j.getString("image")}"
			Manga(
				id = generateUid(urlManga),
				title = j.getString("title"),
				altTitle = null,
				url = urlManga.toRelativeUrl(domain),
				publicUrl = urlManga.toAbsoluteUrl(domain),
				rating = j.getString("rate").toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = img,
				tags = setOf(),
				state = when (j.getString("status")) {
					"0" -> MangaState.ONGOING
					"1" -> MangaState.FINISHED
					"3" -> MangaState.ABANDONED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseJson()
		val jsonManga = json.getJSONObject("manga")
		val chaptersJson = json.getJSONObject("chapters")
		val chapters = mutableListOf<String>()
		chaptersJson.keys().forEach { key ->
			val chapterArray = chaptersJson.getJSONArray(key)
			for (i in chapterArray.length() - 1 downTo 0) {
				chapters.add(chapterArray.getJSONObject(i).toString())

			}
		}
		val slug = manga.url.substringAfterLast("/")
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)
		return manga.copy(
			altTitle = null,
			state = when (jsonManga.getString("status")) {
				"0" -> MangaState.ONGOING
				"1" -> MangaState.FINISHED
				"3" -> MangaState.ABANDONED
				else -> null
			},
			author = jsonManga.getString("author"),
			description = jsonManga.getString("description"),
			chapters = chapters.mapChapters { i, it ->
				val id = it.substringAfter("\"chapter\":").substringBefore(",")
				val url = "https://$domain/api/get/chapter/$slug/$id"
				val date = getDateString(
					it.substringAfter("\"date\":\"").substringBefore("\",").toLong(),
				)
				MangaChapter(
					id = generateUid(url),
					name = "Chapitre : $id",
					number = i.toFloat(),
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(date),
					branch = null,
					source = source,
				)
			},
		)
	}

	private val simpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)
	private fun getDateString(time: Long): String = simpleDateFormat.format(time * 1000L)

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val jsonPage = webClient.httpGet(fullUrl).parseJson()
		val idManga = jsonPage.getJSONObject("manga").getInt("id")
		val slug = jsonPage.getJSONObject("chapter").getInt("chapter")
		val jsonPages = jsonPage.getJSONObject("chapter").getJSONArray("files")
		val pages = ArrayList<MangaPage>(jsonPages.length())
		for (i in 0 until jsonPages.length()) {
			val url = "https://$domain/upload/chapters/$idManga/$slug/${jsonPages.getString(i)}"
			pages.add(
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
