package org.koitharu.kotatsu.parsers.site.all

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal abstract class LineWebtoonsParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
) : MangaParser(context, source) {

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	private val signer by lazy {
		WebtoonsUrlSigner("gUtPzJFZch4ZyAGviiyH94P99lQ3pFdRTwpJWDlSGFfwgpr6ses5ALOxWHOIT7R1")
	}

	// we don't __really__ support changing this domain because:
	// 1. I don't think other websites have this exact API
	// 2. most communication is done with other domains (hosting API and static content), which are not configurable
	// 3. we rely on the HTTP client setting the referer header to webtoons.com
	//
	// This effectively means that changing the domain will break the source. Yikes
	override val configKeyDomain = ConfigKey.Domain("webtoons.com")

	private val apiDomain = "global.apis.naver.com"
	private val staticDomain = "webtoon-phinf.pstatic.net"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		// doesn't actually sort by rating, but by likes
		// this should be fine though
		SortOrder.RATING,
		SortOrder.UPDATED,
	)

	override val userAgentKey = ConfigKey.UserAgent("nApps (Android 12;; linewebtoon; 3.1.0)")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		return page.url.toAbsoluteUrl(staticDomain)
	}

	// some language tags do not map perfectly to the ones used by the API
	private val languageCode: String
		get() = when (val tag = sourceLocale.toLanguageTag()) {
			"in" -> "id"
			"zh" -> "zh-hant"
			else -> tag
		}

	private suspend fun getChapters(titleNo: Long): List<MangaChapter> {
		val firstResult = makeRequest(
			url = "/lineWebtoon/webtoon/challengeEpisodeList.json?v=2&titleNo=$titleNo&startIndex=0&pageSize=30",
		)

		val totalEpisodeCount = firstResult
			.getJSONObject("episodeList")
			.getInt("totalServiceEpisodeCount")

		val episodes = firstResult
			.getJSONObject("episodeList")
			.getJSONArray("episode")
			.asTypedList<JSONObject>()
			.toMutableList()

		while (episodes.count() < totalEpisodeCount) {
			val page = makeRequest(
				url = "/lineWebtoon/webtoon/challengeEpisodeList.json?v=2&titleNo=$titleNo&startIndex=${episodes.count()}&pageSize=30",
			).getJSONObject("episodeList")
				.getJSONArray("episode")
				.asTypedList<JSONObject>()

			episodes.addAll(page)
		}

		return episodes.mapChapters { i, jo ->
			MangaChapter(
				id = generateUid("$titleNo-$i"),
				name = jo.getString("episodeTitle"),
				number = jo.getInt("episodeSeq").toFloat(),
				volume = 0,
				url = "$titleNo-${jo.get("episodeNo")}",
				uploadDate = jo.getLong("modifyYmdt"),
				branch = null,
				scanlator = null,
				source = source,
			)
		}.sortedBy { it.number }
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val titleNo = manga.url.toLong()
		val chaptersDeferred = async { getChapters(titleNo) }

		makeRequest("/lineWebtoon/webtoon/challengeTitleInfo.json?v=2&titleNo=${titleNo}")
			.getJSONObject("titleInfo")
			.let { jo ->
				Manga(
					id = generateUid(titleNo),
					title = jo.getString("title"),
					altTitle = null,
					url = "$titleNo",
					publicUrl = "https://$domain/$languageCode/canvas/a/list?title_no=${titleNo}",
					rating = jo.getFloatOrDefault("starScoreAverage", -10f) / 10f,
					isNsfw = jo.getBooleanOrDefault("ageGradeNotice", isNsfwSource),
					coverUrl = jo.getString("thumbnail").toAbsoluteUrl(staticDomain),
					largeCoverUrl = jo.getStringOrNull("thumbnailVertical")?.toAbsoluteUrl(staticDomain),
					tags = setOf(parseTag(jo.getJSONObject("genreInfo"))),
					author = jo.getStringOrNull("writingAuthorName"),
					description = jo.getString("synopsis"),
					// I don't think the API provides this info
					state = null,
					chapters = chaptersDeferred.await(),
					source = source,
				)
			}
	}

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val manga = when {
			!filter.query.isNullOrEmpty() -> {
				makeRequest("/lineWebtoon/webtoon/searchChallenge?query=${filter.query.urlEncoded()}&startIndex=${offset + 1}&pageSize=20")
					.getJSONObject("challengeSearch")
					.getJSONArray("titleList")
					.mapJSON { jo ->
						val titleNo = jo.getLong("titleNo")

						Manga(
							id = generateUid(titleNo),
							title = jo.getString("title"),
							altTitle = null,
							url = titleNo.toString(),
							publicUrl = "https://$domain/$languageCode/canvas/a/list?title_no=$titleNo",
							rating = RATING_UNKNOWN,
							isNsfw = isNsfwSource,
							coverUrl = jo.getString("thumbnail").toAbsoluteUrl(staticDomain),
							largeCoverUrl = null,
							tags = emptySet(),
							author = jo.getStringOrNull("writingAuthorName"),
							description = null,
							state = null,
							source = source,
						)
					}
			}

			else -> {

				val genre = filter.tags.oneOrThrowIfMany()?.key ?: "ALL"

				val sortOrderStr = when (order) {
					SortOrder.UPDATED -> "UPDATE"
					SortOrder.POPULARITY -> "READ_COUNT"
					SortOrder.RATING -> "LIKEIT"
					else -> throw IllegalArgumentException("Unsupported sort order: $order")
				}

				val result =
					makeRequest("/lineWebtoon/webtoon/challengeGenreTitleList.json?genre=$genre&sortOrder=$sortOrderStr&startIndex=${offset + 1}&pageSize=20")

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
							url = titleNo.toString(),
							publicUrl = "https://$domain/$languageCode/canvas/a/list?title_no=$titleNo",
							rating = jo.getFloatOrDefault("starScoreAverage", -10f) / 10f,
							isNsfw = jo.getBooleanOrDefault("ageGradeNotice", isNsfwSource),
							coverUrl = jo.getString("thumbnail").toAbsoluteUrl(staticDomain),
							largeCoverUrl = jo.getStringOrNull("thumbnailVertical")?.toAbsoluteUrl(staticDomain),
							tags = setOfNotNull(genres[jo.getString("representGenre")]),
							author = jo.getStringOrNull("writingAuthorName"),
							description = jo.getString("synopsis"),
							// I don't think the API provides this info
							state = null,
							source = source,
						)
					}
			}
		}


		return manga
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val (titleNo, episodeNo) = requireNotNull(chapter.url.splitTwoParts('-'))

		return makeRequest("/lineWebtoon/webtoon/challengeEpisodeInfo.json?v=2&titleNo=$titleNo&episodeNo=$episodeNo")
			.getJSONObject("episodeInfo")
			.getJSONArray("imageInfo")
			.mapJSONIndexed { i, jo ->
				MangaPage(
					id = generateUid("$titleNo-$episodeNo-$i"),
					url = jo.getString("url"),
					preview = null,
					source = source,
				)
			}
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val titleNo = link.queryParameter("title_no") ?: return null
		return resolver.resolveManga(this, url = titleNo.toString())
	}

	private fun parseTag(jo: JSONObject): MangaTag {
		return MangaTag(
			title = jo.getString("name"),
			key = jo.getString("code"),
			source = source,
		)
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		return makeRequest("/lineWebtoon/webtoon/challengeGenreList.json")
			.getJSONObject("genreList")
			.getJSONArray("challengeGenres")
			.mapJSONToSet { jo -> parseTag(jo) }
	}

	private suspend fun makeRequest(url: String): JSONObject {
		val resp = webClient.httpGet(finalizeUrl(url))
		val message: JSONObject? = resp.parseJson().optJSONObject("message")
		return when (resp.code) {
			in 200..299 -> checkNotNull(message).getJSONObject("result")
			404 -> throw NotFoundException(message?.getStringOrNull("message").orEmpty(), url)
			else -> {
				val code = message?.getIntOrDefault("code", 0)
				val errorMessage = message?.getStringOrNull("message")
				throw ParseException("Api error (code=$code): $errorMessage", url)
			}
		}
	}

	private fun finalizeUrl(url: String): HttpUrl {
		val httpUrl = url.toAbsoluteUrl(apiDomain).toHttpUrl()
		val builder = httpUrl.newBuilder()
			.addQueryParameter("serviceZone", "GLOBAL")
		if (httpUrl.queryParameter("v") == null) {
			builder.addQueryParameter("v", "1")
		}
		builder.addQueryParameter("language", languageCode)
			.addQueryParameter("locale", "languageCode")
			.addQueryParameter("platform", "APP_ANDROID")
		signer.makeEncryptUrl(builder)
		return builder.build()
	}

	@MangaSourceParser("LINEWEBTOONS_EN", "LineWebtoons English", "en", type = ContentType.MANGA)
	class English(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.LINEWEBTOONS_EN)

	@MangaSourceParser("LINEWEBTOONS_ZH", "LineWebtoons Chinese", "zh", type = ContentType.MANGA)
	class Chinese(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.LINEWEBTOONS_ZH)

	@MangaSourceParser("LINEWEBTOONS_TH", "LineWebtoons Thai", "th", type = ContentType.MANGA)
	class Thai(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.LINEWEBTOONS_TH)

	@MangaSourceParser("LINEWEBTOONS_ID", "LineWebtoons Indonesian", "id", type = ContentType.MANGA)
	class Indonesian(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.LINEWEBTOONS_ID)

	@MangaSourceParser("LINEWEBTOONS_ES", "LineWebtoons Spanish", "es", type = ContentType.MANGA)
	class Spanish(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.LINEWEBTOONS_ES)

	@MangaSourceParser("LINEWEBTOONS_FR", "LineWebtoons French", "fr", type = ContentType.MANGA)
	class French(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.LINEWEBTOONS_FR)

	@MangaSourceParser("LINEWEBTOONS_DE", "LineWebtoons German", "de", type = ContentType.MANGA)
	class German(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.LINEWEBTOONS_DE)

	private inner class WebtoonsUrlSigner(private val secret: String) {

		private val mac = Mac.getInstance("HmacSHA1").apply {
			this.init(SecretKeySpec(secret.encodeToByteArray(), "HmacSHA1"))
		}

		private fun getMessage(url: String, msgpad: String): String {
			return url.substring(0, 0xFF.coerceAtMost(url.length)) + msgpad
		}

		private fun getMessageDigest(s: String): String {
			val signedMessage = synchronized(mac) { mac.doFinal(s.toByteArray()) }
			return context.encodeBase64(signedMessage)
		}

		fun makeEncryptUrl(urlBuilder: HttpUrl.Builder) {
			val msgPad = Calendar.getInstance().timeInMillis.toString()
			val digest = getMessageDigest(getMessage(urlBuilder.build().toString(), msgPad))
			urlBuilder
				.addQueryParameter("msgpad", msgPad)
				.addQueryParameter("md", digest)
//				.addEncodedQueryParameter("md", digest.urlEncoded())
		}
	}
}
