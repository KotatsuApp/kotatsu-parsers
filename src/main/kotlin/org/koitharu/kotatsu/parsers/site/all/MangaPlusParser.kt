package org.koitharu.kotatsu.parsers.site.all

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.Locale
import java.util.UUID

@MangaSourceParser("MANGAPLUSPARSER", "MangaPlus", type = ContentType.HENTAI)
class MangaPlusParser(context: MangaLoaderContext) : MangaParser(context, MangaSource.MANGAPLUSPARSER), Interceptor {

	override val configKeyDomain = ConfigKey.Domain("mangaplus.shueisha.co.jp")

	override val availableSortOrders = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL
	)

	override suspend fun getAvailableLocales()= setOf(
		Locale.ENGLISH,
		Locale("es"),
		Locale.FRENCH,
		Locale("id"),
		Locale("pt_br"),
		Locale("ru"),
		Locale("th"),
		Locale("vi")
	)

	private val extraHeaders = Headers.headersOf("Session-Token", UUID.randomUUID().toString())

	override suspend fun getList(offset: Int, filter: MangaListFilter?): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}

		return when (filter) {
			is MangaListFilter.Advanced -> {
				when (filter.sortOrder) {
					SortOrder.POPULARITY -> getPopularList(filter.locale)
					SortOrder.UPDATED -> getLatestList(filter.locale)
					else -> getAllTitleList(filter.locale)
				}
			}
			is MangaListFilter.Search -> getAllTitleList(query = filter.query)
			else -> getAllTitleList()
		}
	}

	private suspend fun getPopularList(locale: Locale?): List<Manga> {
		val json = apiCall("/title_list/ranking")

		return json.getJSONObject("titleRankingView")
			.getJSONArray("titles")
			.toJSONList()
			.toMangaList(locale.toSiteLocale())
	}

	private suspend fun getLatestList(locale: Locale?): List<Manga> {
		val json = apiCall("/title_list/updated")

		return json.getJSONObject("titleUpdatedView")
			.getJSONArray("latestTitle")
			.mapJSON { it.getJSONObject("title") }
			.toMangaList(locale.toSiteLocale())
	}

	private suspend fun getAllTitleList(locale: Locale? = null, query: String? = null): List<Manga> {
		val json = apiCall("/title_list/allV2")

		return json.getJSONObject("allTitlesViewV2")
			.getJSONArray("AllTitlesGroup")
			.mapJSON { it.getJSONArray("titles").toJSONList() }
			.flatten()
			.toMangaList(locale.toSiteLocale(), query)

	}

	private fun Collection<JSONObject>.toMangaList(langToFilter: String?, query: String? = null): List<Manga> {
		return mapNotNull {
			val language = it.getStringOrNull("language") ?: "ENGLISH"

			// filter out any other language other than langToFilter
			if (langToFilter != null && language != langToFilter)
				return@mapNotNull null

			val name = it.getString("name")
			val author = it.getString("author").replace(" / ", ", ")

			// filter out any other title or author which doesn't match search input
			if (query != null && !(name.contains(query, true) || author.contains(query, true)))
				return@mapNotNull null

			val titleId = it.getInt("titleId").toString()

			Manga(
				id = generateUid(titleId),
				url = titleId,
				publicUrl = "/titles/$titleId".toAbsoluteUrl(domain),
				title = name,
				coverUrl = it.getString("portraitImageUrl"),
				altTitle = null,
				author = author,
				isNsfw = false,
				rating = RATING_UNKNOWN,
				state = null,
				source = source,
				tags = emptySet()
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = apiCall("/title_detailV3?title_id=${manga.url}")
			.getJSONObject("titleDetailView")
		val title = json.getJSONObject("title")
		val chapterList = json.getJSONArray("chapterListGroup")
			.toJSONList()
			.flatMap {
				it.optJSONArray("firstChapterList")?.toJSONList().orEmpty() +
						it.optJSONArray("lastChapterList")?.toJSONList().orEmpty()
			}
		val language = title.getStringOrNull("language") ?: "ENGLISH"

		return manga.copy(
			title = title.getString("name"),
			publicUrl = "/titles/${title.getInt("titleId")}".toAbsoluteUrl(domain),
			coverUrl = title.getString("portraitImageUrl"),
			author = title.getString("author").replace(" / ", ", "),
			description = json.getString("overview"),
			chapters = chapterList.mapNotNull { chapter ->
				val chapterId = chapter.getInt("chapterId").toString()
				val name = chapter.getString("name")
				val subtitle = chapter.getStringOrNull("subTitle")
					?: return@mapNotNull null

				MangaChapter(
					id = generateUid(chapterId),
					url = chapterId,
					name = "$name - $subtitle",
					number = name.substringAfter("#").toIntOrNull() ?: -1,
					uploadDate = chapter.getInt("startTimeStamp") * 1000L,
					branch = language,
					scanlator = null,
					source = source
				)
			}
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val pages = apiCall("/manga_viewer?chapter_id=${chapter.url}&split=yes&img_quality=super_high")
			.getJSONObject("mangaViewer")
			.getJSONArray("pages")

		return pages.mapJSONNotNull {
			val mangaPage = it.optJSONObject("mangaPage")
				?: return@mapJSONNotNull null
			val url = mangaPage.getString("imageUrl")
			val encryptionKey = mangaPage.getStringOrNull("encryptionKey")
			MangaPage(
				id = generateUid(url),
				url = url + if (encryptionKey == null ) "" else "#$encryptionKey",
				preview = null,
				source = source
			)
		}
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)
		val encryptionKey = request.url.fragment

		if (encryptionKey.isNullOrEmpty()) {
			return response
		}

		val contentType = response.headers["Content-Type"] ?: "image/jpeg"

		val image = requireNotNull(response.body).bytes().decodeXorCipher(encryptionKey)
		val body = image.toResponseBody(contentType.toMediaTypeOrNull())

		return response.newBuilder()
			.body(body)
			.build()
	}

	private fun ByteArray.decodeXorCipher(key: String): ByteArray {
		val keyStream = key.chunked(2)
			.map { it.toInt(16) }

		return mapIndexed { i, byte -> byte.toInt() xor keyStream[i % keyStream.size] }
			.map(Int::toByte)
			.toByteArray()
	}

	private suspend fun apiCall(url: String): JSONObject {
		val newUrl = "$apiUrl$url".toHttpUrl().newBuilder()
			.addQueryParameter("format", "json")
			.build()
		val response = webClient.httpGet(newUrl, extraHeaders).parseJson()

		val success = response.optJSONObject("success")

		return checkNotNull(success) {
			val error = response.getJSONObject("error")
			val reason = error.getJSONArray("popups")
				.toJSONList()
				.firstOrNull { it.getStringOrNull("language") == null }
				?.getStringOrNull("body")

			reason ?: "Unknown Error"
		}
	}

	private fun Locale?.toSiteLocale(): String? {
		if (this == null) return null

		return when {
			equals(Locale.ENGLISH) -> "ENGLISH"
			equals(Locale("es")) -> "SPANISH"
			equals(Locale.FRENCH) -> "FRENCH"
			equals(Locale("id")) -> "INDONESIAN"
			equals(Locale("pt-BR")) -> "PORTUGUESE_BR"
			equals(Locale("ru")) -> "RUSSIAN"
			equals(Locale("th")) -> "THAI"
			equals(Locale("vi")) -> "VIETNAMESE"
			else -> null
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return emptySet()
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return emptyList()
	}

	companion object {
		private const val apiUrl = "https://jumpg-webapi.tokyo-cdn.com/api"
	}
}

