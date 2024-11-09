package org.koitharu.kotatsu.parsers.site.all

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NINENINENINEHENTAI", "AnimeH", type = ContentType.HENTAI)
internal class NineNineNineHentaiParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.NINENINENINEHENTAI, PAGE_SIZE), Interceptor {

	override val configKeyDomain = ConfigKey.Domain("animeh.to")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: EnumSet<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableLocales = setOf(
			Locale.ENGLISH,
			Locale.CHINESE,
			Locale.JAPANESE,
			Locale("es"),
		),
	)

	private fun Locale?.getSiteLang(): String {
		if (this == null) return "all"

		return when {
			equals(Locale.ENGLISH) -> "en"
			equals(Locale.CHINESE) -> "cn"
			equals(Locale.JAPANESE) -> "jp"
			equals(Locale("es")) -> "es"
			else -> "all"
		}
	}

	// Need for disable encoding (with encoding not working)
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = if (request.header("Content-Encoding") != null) {
			request.newBuilder().removeHeader("Content-Encoding").build()
		} else {
			request
		}
		return chain.proceed(newRequest)
	}

	private val cdnHost = suspendLazy(initializer = ::getUpdatedCdnHost)

	private suspend fun getUpdatedCdnHost(): String {
		val url = "https://$domain/manga-home"
		val response = webClient.httpGet(url).parseHtml()
		val cdn = response.selectFirst("img.v-thumbnail")?.attr("data-src")
		return cdn?.toHttpUrlOrNull()?.host ?: "edge.fast4speed.rsvp"
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val query = """
			queryTags(
				search: {format:"tagchapter",sortBy:Popular}
				page: 1
				limit: 100
			) {
				edges {
					name
				}
			}
		""".trimIndent()

		val tags = apiCall(query)
			.getJSONObject("queryTags")
			.getJSONArray("edges")

		return tags.mapJSONToSet {
			val name = it.getString("name")
			MangaTag(
				title = name.toCamelCase(),
				key = name,
				source = source,
			)
		}
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return if (filter.query.isNullOrEmpty()) {
			if (filter.tags.isEmpty() && order == SortOrder.POPULARITY) {
				getPopularList(page, filter.locale)
			} else {
				getSearchList(page, null, filter.locale, filter.tags, order)
			}
		} else {
			getSearchList(page, filter.query, null, null, order)
		}
	}

	private suspend fun getPopularList(
		page: Int,
		locale: Locale?,
	): List<Manga> {
		val query = """
			queryPopularChapters(
				size: $PAGE_SIZE
				language: "${locale.getSiteLang()}"
				dateRange: 1
				page: $page
			) {
				edges {
					_id
					name
					firstPics
				}
			}
		""".trimIndent()

		return apiCall(query)
			.getJSONObject("queryPopularChapters")
			.getJSONArray("edges")
			.toMangaList()
	}

	private suspend fun getSearchList(
		page: Int,
		search: String?,
		locale: Locale?,
		tags: Set<MangaTag>?,
		sort: SortOrder?,
	): List<Manga> {
		val searchPayload = buildString {
			if (!search.isNullOrEmpty()) {
				append("query:\"$search\",")
			}
			append("language:\"${locale.getSiteLang()}\"")
			if (sort == SortOrder.POPULARITY) {
				append(",sortBy:Popular")
			}
			if (!tags.isNullOrEmpty()) {
				val tag = tags.oneOrThrowIfMany()!!.key
				append(",tags:[\"$tag\"]")
			}
		}
		val query = """
			queryChapters(
				limit: $PAGE_SIZE
				search: {$searchPayload}
				page: $page
			) {
				edges {
					_id
					name
					firstPics
				}
			}
		""".trimIndent()

		return apiCall(query)
			.getJSONObject("queryChapters")
			.getJSONArray("edges")
			.toMangaList()
	}

	private suspend fun JSONArray.toMangaList(): List<Manga> = mapJSON { entry ->
		val id = entry.getString("_id")
		val name = entry.getString("name")
		val cover = runCatching {
			entry.getJSONArray("firstPics")
				.getJSONObject(0)
				.getString("url")
		}.getOrNull()

		Manga(
			id = generateUid(id),
			title = name.replace(shortenTitleRegex, "").trim(),
			altTitle = name,
			coverUrl = when {
				cover?.startsWith("http") == true -> cover
				cover == null -> ""
				else -> "https://${cdnHost.get()}/$cover"
			},
			author = null,
			isNsfw = true,
			url = id,
			publicUrl = "/hchapter/$id".toAbsoluteUrl(domain),
			tags = emptySet(),
			source = source,
			state = null,
			rating = RATING_UNKNOWN,
		)
	}


	override suspend fun getDetails(manga: Manga): Manga {
		val query = """
			queryChapter(
				chapterId: "${manga.url}"
			) {
                _id
                name
                uploadDate
                format
                description
                language
                pages
                tags
				pictureUrls {
					picCdn
					pics
					picsS
				}
            }
		""".trimIndent()

		val entry = apiCall(query)
			.getJSONObject("queryChapter")

		val id = entry.getString("_id")
		val name = entry.getString("name")
		val cover = entry.getJSONArray("pictureUrls")
			.getJSONObject(0)
			.let { pics ->
				val cdn = pics.getString("picCdn").let {
					if (it.startsWith("http")) {
						"$it/"
					} else {
						"https://${cdnHost.get()}/$it/"
					}
				}
				val img = pics.getJSONArray("pics").getJSONObject(0).getString("url")
				val imgS = pics.getJSONArray("picsS").getJSONObject(0).getString("url")
				Pair(cdn + imgS, cdn + img)
			}
		val tags = entry.optJSONArray("tags")?.mapJSON {
			SiteTag(
				name = it.getString("tagName"),
				type = it.getStringOrNull("tagType"),
			)
		}
		return manga.copy(
			title = name.replace(shortenTitleRegex, "").trim(),
			altTitle = name,
			coverUrl = cover.first,
			largeCoverUrl = cover.second,
			author = tags?.filter { it.type == "artist" }?.joinToString { it.name.toCamelCase() },
			isNsfw = true,
			tags = tags?.mapToSet {
				MangaTag(
					title = it.name.toCamelCase(),
					key = it.name,
					source = source,
				)
			}.orEmpty(),
			state = null,
			description = entry.getStringOrNull("description"),
			chapters = listOf(
				MangaChapter(
					id = generateUid(id),
					name = name,
					number = 1f,
					volume = 0,
					url = id,
					uploadDate = runCatching {
						dateFormat.parse(entry.getString("uploadDate"))!!.time
					}.getOrDefault(0L),
					branch = entry.getStringOrNull("language")?.let {
						val locale = when (it) {
							"en" -> Locale.ENGLISH
							"jp" -> Locale.JAPANESE
							"cn" -> Locale.CHINESE
							"es" -> Locale("es")
							else -> Locale.ROOT
						}

						return@let locale.getDisplayLanguage(locale)
					},
					scanlator = when (entry.getStringOrNull("format")) {
						"artistcg" -> "ArtistCG"
						"gamecg" -> "GameCG"
						"imageset" -> "ImageSet"
						else -> entry.getStringOrNull("format")?.toCamelCase()
					},
					source = source,
				),
			),
		)
	}

	private data class SiteTag(
		val name: String,
		val type: String?,
	)

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val query = """
			queryRecommendations(
				type: "chapter"
				_id: "${seed.url}"
				search: {sortBy:Popular}
				page: 1
				size: $PAGE_SIZE
			) {
				chapters {
					_id
					name
					firstPics
				}
			}
		""".trimIndent()

		return apiCall(query)
			.getJSONObject("queryRecommendations")
			.getJSONArray("chapters")
			.toMangaList()
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val query = """
			queryChapter(
				chapterId: "${chapter.url}"
			) {
				pictureUrls {
					picCdn
					pics
					picsS
				}
			}
		""".trimIndent()

		val pages = apiCall(query)
			.getJSONObject("queryChapter")
			.getJSONArray("pictureUrls")
			.getJSONObject(0)

		val cdn = pages.getString("picCdn").let {
			if (it.startsWith("http")) {
				"$it/"
			} else {
				"https://${cdnHost.get()}/$it/"
			}
		}

		val pics = pages.getJSONArray("pics").asTypedList<JSONObject>()
		val picsS = pages.getJSONArray("picsS").asTypedList<JSONObject>()

		return pics.zip(picsS).map {
			val img = it.first.getString("url")
			val imgS = it.second.getString("url")
			MangaPage(
				id = generateUid(img),
				url = cdn + img,
				preview = cdn + imgS,
				source = source,
			)
		}
	}

	private suspend fun apiCall(query: String): JSONObject {
		return webClient.graphQLQuery("https://api.$domain/api", query).getJSONObject("data")
	}

	companion object {
		private const val PAGE_SIZE = 20
		private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
		private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
	}
}
