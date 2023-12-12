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
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("NINENINENINEHENTAI", "999Hentai", type = ContentType.HENTAI)
internal class NineNineNineHentaiParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.NINENINENINEHENTAI, size), Interceptor {

	override val configKeyDomain = ConfigKey.Domain("999hentai.net")

	override val availableSortOrders: EnumSet<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST
	)

	override val isMultipleTagsSupported = false

	override suspend fun getAvailableLocales() = setOf(
		Locale.ENGLISH,
		Locale.CHINESE,
		Locale.JAPANESE,
		Locale("es")
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

	private var cdnUrl: String? = null

	private suspend fun getCdnUrl(): String {
		if (cdnUrl.isNullOrEmpty()) {
			val url = "https://$domain/manga-home"
			val response = webClient.httpGet(url).parseHtml()
			val cdn = response.selectFirst("img.v-thumbnail")?.attr("data-src")
			cdnUrl = cdn?.toHttpUrlOrNull()?.host
		}

		return cdnUrl ?: "edge.fast4speed.rsvp"
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
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
				title = name.capitalize(),
				key = name,
				source = source
			)
		}
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		return when (filter) {
			is MangaListFilter.Advanced -> {
				if (filter.tags.isEmpty() && filter.sortOrder == SortOrder.POPULARITY) {
					getPopularList(page, filter.locale)
				} else {
					getSearchList(page, null, filter.locale, filter.tags, filter.sortOrder)
				}
			}
			is MangaListFilter.Search -> {
				getSearchList(page, filter.query, null, null, filter.sortOrder)
			}
			else -> emptyList()
		}
	}

	private suspend fun getPopularList(
		page: Int,
		locale: Locale?
	): List<Manga> {
		val query = """
			queryPopularChapters(
				size: $size
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
				limit: $size
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
				else -> "https://${getCdnUrl()}/$cover"
			},
			author = null,
			isNsfw = true,
			url = id,
			publicUrl = "/hchapter/$id".toAbsoluteUrl(domain),
			tags = emptySet(),
			source = source,
			state = MangaState.FINISHED,
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
                firstPics
                tags
            }
		""".trimIndent()

		val entry = apiCall(query)
			.getJSONObject("queryChapter")

		val id = entry.getString("_id")
		val name = entry.getString("name")
		val cover = runCatching {
			entry.getJSONArray("firstPics")
				.getJSONObject(0)
				.getString("url")
		}.getOrNull()
		val tags = entry.optJSONArray("tags")?.mapJSON {
			SiteTag(
				name = it.getString("tagName"),
				type = it.getStringOrNull("tagType")
			)
		}
		return manga.copy(
			title = name.replace(shortenTitleRegex, "").trim(),
			altTitle = name,
			coverUrl = when {
				cover?.startsWith("http") == true -> cover
				cover == null -> ""
				else -> "https://${getCdnUrl()}/$cover"
			},
			author = tags?.filter { it.type == "artist" }?.joinToString { it.name.capitalize() },
			isNsfw = true,
			tags = tags?.mapToSet {
				MangaTag(
					title = it.name.capitalize(),
					key = it.name,
					source = source
				)
			}.orEmpty(),
			state = MangaState.FINISHED,
			description = entry.getStringOrNull("description"),
			chapters = listOf(
				MangaChapter(
					id = generateUid(id),
					name = name,
					number = 1,
					url = id,
					uploadDate = kotlin.runCatching {
						dateFormat.parse(entry.getString("uploadDate"))!!.time
					}.getOrDefault(0L),
					branch = when (entry.getStringOrNull("language")) {
						"en" -> "English"
						"jp" -> "Japanese"
						"cn" -> "Chinese"
						"es" -> "Spanish"
						else -> entry.getStringOrNull("language")?.capitalize()
					},
					scanlator = when(entry.getStringOrNull("format")) {
						"artistcg" -> "ArtistCG"
						"gamecg" -> "GameCG"
						"imageset" -> "ImageSet"
						else -> entry.getStringOrNull("format")?.capitalize()
					},
					source = source
				)
			)
		)
	}

	data class SiteTag(
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
				size: $size
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
				"https://${getCdnUrl()}/$it/"
			}
		}

		val pics = pages.getJSONArray("pics").toJSONList()
		val picsS = pages.getJSONArray("picsS").toJSONList()

		return pics.zip(picsS).map {
			val img = it.first.getString("url")
			val imgS = it.second.getString("url")
			MangaPage(
				id = generateUid(img),
				url = cdn + img,
				preview = cdn + imgS,
				source = source
			)
		}
	}

	private suspend fun apiCall(query: String): JSONObject {
		return webClient.graphQLQuery("https://api.$domain/api", query).getJSONObject("data")
	}

	companion object {
		private const val size = 20
		private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
		private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

		private fun String.capitalize(): String {
			return this.trim().split(" ").joinToString(" ") { word ->
				word.replaceFirstChar {
					if (it.isLowerCase()) {
						it.titlecase(
							Locale.getDefault(),
						)
					} else {
						it.toString()
					}
				}
			}
		}
	}
}
