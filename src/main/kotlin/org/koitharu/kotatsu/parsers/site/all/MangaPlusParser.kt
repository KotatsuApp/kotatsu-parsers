package org.koitharu.kotatsu.parsers.site.all

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.util.*

internal abstract class MangaPlusParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	private val sourceLang: String,
) : SinglePageMangaParser(context, source), Interceptor {

	private val apiUrl = "https://jumpg-webapi.tokyo-cdn.com/api"
	override val configKeyDomain = ConfigKey.Domain("mangaplus.shueisha.co.jp")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	private val extraHeaders = Headers.headersOf("Session-Token", UUID.randomUUID().toString())

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		return when {
			filter.query.isNullOrEmpty() -> {
				when (order) {
					SortOrder.POPULARITY -> getPopularList()
					SortOrder.UPDATED -> getLatestList()
					else -> getAllTitleList()
				}
			}

			else -> getAllTitleList(filter.query)
		}
	}

	private suspend fun getPopularList(): List<Manga> {
		val json = apiCall("/title_list/ranking")

		return json.getJSONObject("titleRankingView")
			.getJSONArray("titles")
			.asTypedList<JSONObject>()
			.toMangaList()
	}

	private suspend fun getLatestList(): List<Manga> {
		val json = apiCall("/title_list/updated")

		return json.getJSONObject("titleUpdatedView")
			.getJSONArray("latestTitle")
			.mapJSON { it.getJSONObject("title") }
			.toMangaList()
	}

	// since search is local, save network calls on related manga call
	private val allTitleCache = suspendLazy {
		apiCall("/title_list/allV2")
			.getJSONObject("allTitlesViewV2")
			.getJSONArray("AllTitlesGroup")
			.mapJSON { it.getJSONArray("titles").asTypedList<JSONObject>() }
			.flatten()
	}

	private suspend fun getAllTitleList(query: String? = null): List<Manga> {
		return allTitleCache.get().toMangaList(query)
	}

	private fun List<JSONObject>.toMangaList(query: String? = null): List<Manga> {
		return mapNotNull {
			val language = it.getStringOrNull("language") ?: "ENGLISH"

			if (language != sourceLang) {
				return@mapNotNull null
			}

			val name = it.getString("name")
			val author = it.getString("author")
				.split('/')
				.joinToString(transform = String::trim)

			// filter out any other title or author which doesn't match search input
			if (query != null && !(name.contains(query, true) || author.contains(query, true))) {
				return@mapNotNull null
			}

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
				tags = emptySet(),
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = apiCall("/title_detailV3?title_id=${manga.url}")
			.getJSONObject("titleDetailView")
		val title = json.getJSONObject("title")

		val completed = json.getJSONObject("titleLabels")
			.getString("releaseSchedule").let {
				it == "DISABLED" || it == "COMPLETED"
			}

		val hiatus = json.getStringOrNull("nonAppearanceInfo")?.contains("on a hiatus") == true

		return manga.copy(
			title = title.getString("name"),
			publicUrl = "/titles/${title.getInt("titleId")}".toAbsoluteUrl(domain),
			coverUrl = title.getString("portraitImageUrl"),
			author = title.getString("author")
				.split("/").joinToString(transform = String::trim),
			description = buildString {
				json.getString("overview").let(::append)
				json.getStringOrNull("viewingPeriodDescription")
					?.takeIf { !completed }
					?.let { append("<br><br>", it) }
			},
			chapters = parseChapters(
				json.getJSONArray("chapterListGroup"),
				title.getStringOrNull("language") ?: "ENGLISH",
			),
			state = when {
				completed -> MangaState.FINISHED
				hiatus -> MangaState.PAUSED
				else -> MangaState.ONGOING
			},
		)
	}

	private fun parseChapters(chapterListGroup: JSONArray, language: String): List<MangaChapter> {
		val chapterList = chapterListGroup
			.asTypedList<JSONObject>()
			.flatMap {
				it.optJSONArray("firstChapterList")?.asTypedList<JSONObject>().orEmpty() +
					it.optJSONArray("lastChapterList")?.asTypedList<JSONObject>().orEmpty()
			}

		return chapterList.mapChapters { _, chapter ->
			val chapterId = chapter.getInt("chapterId").toString()
			val subtitle = chapter.getStringOrNull("subTitle") ?: return@mapChapters null

			MangaChapter(
				id = generateUid(chapterId),
				url = chapterId,
				name = subtitle,
				number = chapter.getString("name")
					.substringAfter("#")
					.toFloatOrNull() ?: -1f,
				volume = 0,
				uploadDate = chapter.getInt("startTimeStamp") * 1000L,
				branch = when (language) {
					"PORTUGUESE_BR" -> "Portuguese (Brazil)"
					else -> language.lowercase().toTitleCase()
				},
				scanlator = null,
				source = source,
			)
		}
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
				url = url + if (encryptionKey == null) "" else "#$encryptionKey",
				preview = null,
				source = source,
			)
		}
	}

	// image descrambling
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)
		val encryptionKey = request.url.fragment

		if (encryptionKey.isNullOrEmpty()) {
			return response
		}

		return response.map { responseBody ->
			val contentType = response.headers["Content-Type"] ?: "image/jpeg"
			val image = responseBody.bytes().decodeXorCipher(encryptionKey)
			image.toResponseBody(contentType.toMediaTypeOrNull())
		}
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
				.asTypedList<JSONObject>()
				.firstOrNull { it.getStringOrNull("language") == null }

			if (reason?.getStringOrNull("subject") == "Not Found" && url.contains("manga_viewer")) {
				"This chapter has expired"
			} else {
				reason?.getStringOrNull("body") ?: "Unknown Error"
			}
		}
	}

	@MangaSourceParser("MANGAPLUSPARSER_EN", "MANGA Plus English", "en")
	class English(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_EN,
		"ENGLISH",
	)

	@MangaSourceParser("MANGAPLUSPARSER_ES", "MANGA Plus Spanish", "es")
	class Spanish(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_ES,
		"SPANISH",
	)

	@MangaSourceParser("MANGAPLUSPARSER_FR", "MANGA Plus French", "fr")
	class French(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_FR,
		"FRENCH",
	)

	@MangaSourceParser("MANGAPLUSPARSER_ID", "MANGA Plus Indonesian", "id")
	class Indonesian(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_ID,
		"INDONESIAN",
	)

	@MangaSourceParser("MANGAPLUSPARSER_PTBR", "MANGA Plus Portuguese (Brazil)", "pt")
	class Portuguese(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_PTBR,
		"PORTUGUESE_BR",
	)

	@MangaSourceParser("MANGAPLUSPARSER_RU", "MANGA Plus Russian", "ru")
	class Russian(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_RU,
		"RUSSIAN",
	)

	@MangaSourceParser("MANGAPLUSPARSER_TH", "MANGA Plus Thai", "th")
	class Thai(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_TH,
		"THAI",
	)

	@MangaSourceParser("MANGAPLUSPARSER_VI", "MANGA Plus Vietnamese", "vi")
	class Vietnamese(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_VI,
		"VIETNAMESE",
	)

	@MangaSourceParser("MANGAPLUSPARSER_DE", "MANGA Plus German", "de")
	class German(context: MangaLoaderContext) : MangaPlusParser(
		context,
		MangaParserSource.MANGAPLUSPARSER_DE,
		"GERMAN",
	)
}
