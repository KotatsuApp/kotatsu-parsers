package org.koitharu.kotatsu.parsers.site.en

import okhttp3.Headers
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import java.net.URI
import java.net.URLEncoder
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal abstract class LineWebtoonsParser(context: MangaLoaderContext, source: MangaSource) : MangaParser(context, source)  {
	private val signer = WebtoonsUrlSigner("gUtPzJFZch4ZyAGviiyH94P99lQ3pFdRTwpJWDlSGFfwgpr6ses5ALOxWHOIT7R1")

	override val configKeyDomain
		get() = ConfigKey.Domain("webtoons.com")
	private val configKeyApiDomain
		get() = ConfigKey.Domain("global.apis.naver.com")
	private val configKeyStaticDomain
		get() = ConfigKey.Domain("webtoon-phinf.pstatic.net")

	private val apiDomain
		get() = config[configKeyApiDomain]
	private val staticDomain
		get() = config[configKeyStaticDomain]

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		// doesn't actually sort by rating, but by likes
		// this should be fine though
		SortOrder.RATING,
		SortOrder.UPDATED,
	)
	override val headers: Headers
		get() = Headers.Builder()
			.add("User-Agent", "nApps (Android 12;; linewebtoon; 3.1.0)")
			.build()

	override suspend fun getPageUrl(page: MangaPage): String {
		return page.url
	}

	private suspend fun getChapters(titleNo: Long): List<MangaChapter> {
		val firstResult = makeRequest("/lineWebtoon/webtoon/challengeEpisodeList.json?v=2&titleNo=$titleNo&startIndex=0&pageSize=30")

		val totalEpisodeCount = firstResult
			.getJSONObject("episodeList")
			.getInt("totalServiceEpisodeCount")

		val episodes = firstResult
			.getJSONObject("episodeList")
			.getJSONArray("episode")
			.toJSONList()
			.toMutableList()

		while (episodes.count() < totalEpisodeCount) {
			val page = makeRequest("/lineWebtoon/webtoon/challengeEpisodeList.json?v=2&titleNo=$titleNo&startIndex=${episodes.count()}&pageSize=30")
				.getJSONObject("episodeList")
				.getJSONArray("episode")
				.toJSONList()

			episodes.addAll(page)
		}

		return episodes.mapIndexed { i, jo ->
			MangaChapter(
				id = generateUid("$titleNo-$i"),
				name = jo.getString("episodeTitle"),
				number = jo.getInt("episodeSeq"),
				url = "$titleNo-${jo.getString("episodeNo")}",
				uploadDate = jo.getLong("modifyYmdt"),
				// do we want to use it for anything?
				branch = null,
				scanlator = null,
				source = source,
			)
		}.sortedBy { it.number }
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val titleNo = manga.url.toLong()

		return makeRequest("/lineWebtoon/webtoon/challengeTitleInfo.json?v=2&titleNo=${titleNo}")
			.getJSONObject("titleInfo")
			.let { jo ->
				Manga(
					id = generateUid(titleNo),
					title = jo.getString("title"),
					altTitle = null,
					url = "$titleNo",
					publicUrl = "https://${domain}/en/canvas/a/list?title_no=${titleNo}",
					rating = jo.getDouble("starScoreAverage").toFloat() / 10f,
					isNsfw = jo.getBoolean("ageGradeNotice"),
					coverUrl = "https://$staticDomain${jo.getString("thumbnail")}",
					largeCoverUrl = if (jo.has("thumbnailVertical")) {
						"https://$staticDomain${jo.getString("thumbnailVertical")}"
					} else { null },
					tags = setOf(parseTag(jo.getJSONObject("genreInfo"))),
					author = jo.getString("writingAuthorName"),
					description = jo.getString("synopsis"),
					// I don't think the API provides this info
					state = null,
					chapters = getChapters(titleNo),
					source = source,
				)
			}
	}

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val genre = tags.oneOrThrowIfMany()?.key ?: "ALL"

		val sortOrderStr = when (sortOrder) {
			SortOrder.UPDATED -> "UPDATE"
			SortOrder.POPULARITY -> "READ_COUNT"
			SortOrder.RATING -> "LIKEIT"
			else -> {
				throw Exception("Unreachable")
			}
		}

		val manga = if (query != null) {
			if (!tags.isNullOrEmpty()) {
				throw IllegalArgumentException("This source does not support search with tags")
			}

			makeRequest("/lineWebtoon/webtoon/searchChallenge?query=${query.urlEncoded()}&startIndex=${offset+1}&pageSize=20")
				.getJSONObject("challengeSearch")
				.getJSONArray("titleList")
				.mapJSON { jo ->
					val titleNo = jo.getLong("titleNo")

					Manga(
						id = generateUid(titleNo),
						title = jo.getString("title"),
						altTitle = null,
						url = "$titleNo",
						publicUrl = "https://${domain}/en/canvas/a/list?title_no=${titleNo}",
						rating = RATING_UNKNOWN,
						isNsfw = false,
						coverUrl = "https://$staticDomain${jo.getString("thumbnail")}",
						largeCoverUrl = null,
						tags = setOf(),
						author = jo.getString("writingAuthorName"),
						description = null,
						state = null,
						source = source,
					)
				}
		} else {
			val result = makeRequest("/lineWebtoon/webtoon/challengeGenreTitleList.json?genre=${genre}&sortOrder=${sortOrderStr}&startIndex=${offset+1}&pageSize=20")

			val genres = result.getJSONObject("genreList")
				.getJSONArray("challengeGenres")
				.mapJSON { jo -> parseTag(jo) }
				.associateBy { tag -> tag.key }

			result
				.getJSONObject("titleList")
				.getJSONArray("titles")
				.mapJSON { jo ->
					val titleNo = jo.getLong("titleNo")

					Manga(
						id = generateUid(titleNo),
						title = jo.getString("title"),
						altTitle = null,
						url = "$titleNo",
						publicUrl = "https://${domain}/en/canvas/a/list?title_no=${titleNo}",
						rating = jo.getDouble("starScoreAverage").toFloat() / 10f,
						isNsfw = jo.getBoolean("ageGradeNotice"),
						coverUrl = "https://$staticDomain${jo.getString("thumbnail")}",
						largeCoverUrl = if (jo.has("thumbnailVertical")) {
							"https://$staticDomain${jo.getString("thumbnailVertical")}"
						} else { null },
						tags = setOf(genres[jo.getString("representGenre")]!!),
						author = jo.getString("writingAuthorName"),
						description = jo.getString("synopsis"),
						// I don't think the API provides this info
						state = null,
						source = source,
					)
				}
		}

		return manga
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val (titleNo, episodeNo) = chapter.url.splitTwoParts('-')!!

		return makeRequest("/lineWebtoon/webtoon/challengeEpisodeInfo.json?v=2&titleNo=${titleNo}&episodeNo=${episodeNo}")
			.getJSONObject("episodeInfo")
			.getJSONArray("imageInfo")
			.mapJSONIndexed() { i, jo ->
				MangaPage(
					id = generateUid("$titleNo-$episodeNo-$i"),
					url = "https://$staticDomain${jo.getString("url")}",
					preview = null,
					source = source,
				)
			}
	}

	private fun parseTag(jo: JSONObject): MangaTag {
		return MangaTag(
			title = jo.getString("name"),
			key = jo.getString("code"),
			source = source,
		)
	}

	override suspend fun getTags(): Set<MangaTag> {
		return makeRequest("/lineWebtoon/webtoon/challengeGenreList.json")
			.getJSONObject("genreList")
			.getJSONArray("challengeGenres")
			.mapJSONToSet { jo -> parseTag(jo) }
	}

	private suspend fun makeRequest(url: String): JSONObject {
		val resp = webClient.httpGet(finalizeUrl(url))
		val message = resp.parseJson().getJSONObject("message")
		if (resp.isSuccessful) {
			return message.getJSONObject("result")
		} else {
			// TODO: handle 404 and some other error codes
			val code = message.getInt("code")
			val errorMessage = message.getString("message")
			throw ParseException("Api error (code=$code): $errorMessage", url)
		}
	}

	private fun finalizeUrl(url: String): String {
		val urlWithHost = "https://${apiDomain}$url"
		val uri = URI(urlWithHost)
		val hasVersion = (uri.rawQuery ?: "").split("&").any { it.startsWith("v=") }
		val hasQuery = uri.rawQuery != null
		// some language tags do not map perfectly to the ones used by the API
		val language = when (val tag = sourceLocale.toLanguageTag()) {
			"in" -> "id"
			"zh" -> "zh-hant"
			else -> tag
		}

		val urlWithParams = urlWithHost + if (hasQuery) {
			"&"
		} else {
			"?"
		} + "serviceZone=GLOBAL&" + if (!hasVersion) {
			"v=1"
		} else { "" } + "&language=${language}&locale=${language}&platform=APP_ANDROID"

		return signer.makeEncryptUrl(urlWithParams)
	}

	@MangaSourceParser("LINEWEBTOONS_EN", "Line Webtoons English", "en", type = ContentType.MANGA)
	class English(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaSource.LINEWEBTOONS_EN)
	@MangaSourceParser("LINEWEBTOONS_ZH", "Line Webtoons Chinese", "zh", type = ContentType.MANGA)
	class Chinese(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaSource.LINEWEBTOONS_ZH)
	@MangaSourceParser("LINEWEBTOONS_TH", "Line Webtoons Thai", "th", type = ContentType.MANGA)
	class Thai(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaSource.LINEWEBTOONS_TH)
	@MangaSourceParser("LINEWEBTOONS_ID", "Line Webtoons Indonesian", "id", type = ContentType.MANGA)
	class Indonesian(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaSource.LINEWEBTOONS_ID)
	@MangaSourceParser("LINEWEBTOONS_ES", "Line Webtoons Spanish", "es", type = ContentType.MANGA)
	class Spanish(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaSource.LINEWEBTOONS_ES)
	@MangaSourceParser("LINEWEBTOONS_FR", "Line Webtoons French", "fr", type = ContentType.MANGA)
	class French(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaSource.LINEWEBTOONS_FR)
	@MangaSourceParser("LINEWEBTOONS_DE", "Line Webtoons German", "de", type = ContentType.MANGA)
	class German(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaSource.LINEWEBTOONS_DE)

}


private class WebtoonsUrlSigner(val secret: String) {
	private val mac = Mac.getInstance("HmacSHA1").apply {
		this.init(SecretKeySpec(secret.encodeToByteArray(), "HmacSHA1"))
	}

	private fun getMessage(url: String, msgpad: String): String {
		return url.substring(0, 0xFF.coerceAtMost(url.length)) + msgpad
	}

	private fun getMessageDigest(s: String): String {
		var signedMessage: ByteArray
		synchronized(mac) { signedMessage = mac.doFinal(s.toByteArray()) }

		// we don't use the context.encodeBase64 here because it adds newlines and doesn't add padding
		// we, however, need padding and no newlines
		return encodeBase64(signedMessage)
	}


	fun makeEncryptUrl(s: String): String {
		return makeEncryptUrlCore(
			s,
			java.lang.String.valueOf(Calendar.getInstance().timeInMillis),
		)
	}

	private fun makeEncryptUrlCore(url: String, msgpad: String): String {
		val digest = URLEncoder.encode(getMessageDigest(getMessage(url, msgpad)), "utf-8")
		return url + if (url.contains("?")) {
			"&"
		} else {
			"?"
		} + "msgpad=${msgpad}&md=${digest}"
	}
}


private val INT_TO_BASE64: CharArray = charArrayOf(
	'A',
	'B',
	'C',
	'D',
	'E',
	'F',
	'G',
	'H',
	'I',
	'J',
	'K',
	'L',
	'M',
	'N',
	'O',
	'P',
	'Q',
	'R',
	'S',
	'T',
	'U',
	'V',
	'W',
	'X',
	'Y',
	'Z',
	'a',
	'b',
	'c',
	'd',
	'e',
	'f',
	'g',
	'h',
	'i',
	'j',
	'k',
	'l',
	'm',
	'n',
	'o',
	'p',
	'q',
	'r',
	's',
	't',
	'u',
	'v',
	'w',
	'x',
	'y',
	'z',
	'0',
	'1',
	'2',
	'3',
	'4',
	'5',
	'6',
	'7',
	'8',
	'9',
	'+',
	'/',
)

private fun encodeBase64(arr: ByteArray): String {
	val groupsCount = arr.size / 3
	val extraCount = arr.size - groupsCount * 3

	val sb = StringBuffer((arr.size + 2) / 3 * 4)
	val code = INT_TO_BASE64
	var groupIndex = 0
	var position = 0
	while (groupIndex < groupsCount) {
		val v4 = arr[position].toInt() and 0xFF
		val v5 = arr[position + 1].toInt() and 0xFF
		val v6 = arr[position + 2].toInt() and 0xFF
		sb.append(code[v4 shr 2])
		sb.append(code[v4 shl 4 and 0x3F or (v5 shr 4)])
		sb.append(code[v5 shl 2 and 0x3F or (v6 shr 6)])
		sb.append(code[v6 and 0x3F])
		++groupIndex
		position += 3
	}

	if (extraCount != 0) {
		val v1 = arr[position].toInt() and 0xFF
		sb.append(code[v1 shr 2])
		if (extraCount == 1) {
			sb.append(code[v1 shl 4 and 0x3F])
			sb.append("==")
		} else {
			val v2 = arr[position + 1].toInt() and 0xFF
			sb.append(code[v1 shl 4 and 0x3F or (v2 shr 4)])
			sb.append(code[v2 shl 2 and 0x3F])
			sb.append('=')
		}
	}
	return sb.toString()
}
