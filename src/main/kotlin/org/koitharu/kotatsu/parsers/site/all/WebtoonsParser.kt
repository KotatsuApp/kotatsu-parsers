package org.koitharu.kotatsu.parsers.site.all

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
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
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal abstract class WebtoonsParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
) : MangaParser(context, source) {

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
		SortOrder.POPULARITY, // views
		SortOrder.RATING, // star rating
		//SortOrder.LIKE, // likes
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override val userAgentKey = ConfigKey.UserAgent("nApps (Android 12;; linewebtoon; 3.1.0)")

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = getAllGenreList().values.toSet(),
	)

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

	private suspend fun fetchEpisodes(titleNo: Long): List<MangaChapter> = coroutineScope {
		val firstResult =
			makeRequest("/lineWebtoon/webtoon/episodeList.json?v=5&titleNo=$titleNo&startIndex=0&pageSize=30")

		val totalEpisodeCount = firstResult.getJSONObject("episodeList").getInt("totalServiceEpisodeCount")
		val episodes = firstResult.getJSONObject("episodeList").getJSONArray("episode").toJSONList().toMutableList()

		val additionalEpisodes = (episodes.size until totalEpisodeCount step 30).map { startIndex ->
			async {
				makeRequest("/lineWebtoon/webtoon/episodeList.json?v=5&titleNo=$titleNo&startIndex=$startIndex&pageSize=30").getJSONObject(
					"episodeList",
				).getJSONArray("episode").toJSONList()
			}
		}.awaitAll().flatten()

		episodes.addAll(additionalEpisodes)

		// Optimize object creation and sorting
		episodes.mapChapters { i, jo ->
			MangaChapter(
				id = generateUid("$titleNo-$i"),
				name = jo.getString("episodeTitle"),
				number = jo.getInt("episodeSeq").toFloat(),
				volume = 0,
				url = "$titleNo-${jo.get("episodeNo")}",
				uploadDate = jo.getLong("registerYmdt"),
				branch = null,
				scanlator = null,
				source = source,
			)
		}.sortedBy(MangaChapter::number)
	}

	private fun JSONArray.toJSONList(): List<JSONObject> {
		val list = mutableListOf<JSONObject>()
		for (i in 0 until length()) {
			list.add(getJSONObject(i))
		}
		return list
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val titleNo = manga.url.toLong()
		val chaptersDeferred = async { fetchEpisodes(titleNo) }
		val chapters = chaptersDeferred.await()
		makeRequest("/lineWebtoon/webtoon/titleInfo.json?titleNo=${titleNo}&anyServiceStatus=false").getJSONObject("titleInfo")
			.let { jo ->
				MangaWebtoon(
					Manga(
						id = generateUid(titleNo),
						title = jo.getString("title"),
						altTitle = null,
						url = "$titleNo",
						publicUrl = "https://$domain/$languageCode/originals/a/list?title_no=${titleNo}",
						rating = jo.getFloatOrDefault("starScoreAverage", -10f) / 10f,
						isNsfw = jo.getBooleanOrDefault("ageGradeNotice", isNsfwSource),
						coverUrl = jo.getString("thumbnail").toAbsoluteUrl(staticDomain),
						largeCoverUrl = jo.getStringOrNull("thumbnailVertical")?.toAbsoluteUrl(staticDomain),
						tags = setOf(parseTag(jo.getJSONObject("genreInfo"))),
						author = jo.getStringOrNull("writingAuthorName"),
						description = jo.getString("synopsis"),
						// I don't think the API provides this info,
						state = null,
						chapters = chapters,
						source = source,
					),
					date = jo.getLong("lastEpisodeRegisterYmdt"),
					readCount = jo.getLong("readCount"),
					//likeCount = jo.getLong("likeitCount"),
				).manga
			}
	}

	private val allGenreCache = suspendLazy {
		makeRequest("/lineWebtoon/webtoon/genreList.json").getJSONObject("genreList").getJSONArray("genres")
			.mapJSON { jo -> parseTag(jo) }.associateBy { tag -> tag.key }
	}

	private val allTitleCache = suspendLazy(soft = true) {
		makeRequest("/lineWebtoon/webtoon/titleList.json?").getJSONObject("titleList").getJSONArray("titles")
			.mapJSON { jo ->
				val titleNo = jo.getLong("titleNo")
				MangaWebtoon(
					Manga(
						id = generateUid(titleNo),
						url = titleNo.toString(),
						publicUrl = "https://$domain/$languageCode/originals/a/list?title_no=$titleNo",
						title = jo.getString("title"),
						coverUrl = jo.getString("thumbnail").toAbsoluteUrl(staticDomain),
						altTitle = null,
						author = jo.getStringOrNull("writingAuthorName"),
						isNsfw = jo.getBooleanOrDefault("ageGradeNotice", isNsfwSource),
						rating = jo.getFloatOrDefault("starScoreAverage", -10f) / 10f,
						tags = setOfNotNull(allGenreCache.get()[jo.getString("representGenre")]),
						description = jo.getString("synopsis"),
						state = null,
						source = source,
					),
					date = jo.getLong("lastEpisodeRegisterYmdt"),
					readCount = jo.getLong("readCount"),
					//likeCount = jo.getLong("likeitCount"),
				)
			}
	}

	private suspend fun getAllGenreList(): Map<String, MangaTag> {
		return allGenreCache.get()
	}

	private suspend fun getAllTitleList(): List<MangaWebtoon> {
		return allTitleCache.get()

	}

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val webtoons = when {
			!filter.query.isNullOrEmpty() -> {
				makeRequest("/lineWebtoon/webtoon/searchWebtoon?query=${filter.query.urlEncoded()}").getJSONObject("webtoonSearch")
					.getJSONArray("titleList").mapJSON { jo ->
						val titleNo = jo.getLong("titleNo")
						MangaWebtoon(
							Manga(
								id = generateUid(titleNo),
								title = jo.getString("title"),
								altTitle = null,
								url = titleNo.toString(),
								publicUrl = "https://$domain/$languageCode/originals/a/list?title_no=$titleNo",
								rating = RATING_UNKNOWN,
								isNsfw = isNsfwSource,
								coverUrl = jo.getString("thumbnail").toAbsoluteUrl(staticDomain),
								largeCoverUrl = null,
								tags = emptySet(),
								author = jo.getStringOrNull("writingAuthorName"),
								description = null,
								state = null,
								source = source,
							),
							date = 0L,
							readCount = 0L,
						)
					}
			}

			else -> {
				val genre = filter.tags.oneOrThrowIfMany()?.key ?: "ALL"

				val genres = getAllGenreList()
				var result = getAllTitleList()

				if (genre != "ALL") {
					result = result.filter { it.manga.tags.contains(genres[genre]) }
				}

				when (order) {
					SortOrder.UPDATED -> result.sortedByDescending { it.date }
					SortOrder.POPULARITY -> result.sortedByDescending { it.readCount }
					SortOrder.RATING -> result.sortedByDescending { it.manga.rating }
					//SortOrder.LIKE -> result.sortedBy { it.likeitCount }
					else -> throw IllegalArgumentException("Unsupported sort order: $order")
				}
			}
		}
		return webtoons.map { it.manga }.subList(offset, (offset + 20).coerceAtMost(webtoons.size))
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val (titleNo, episodeNo) = requireNotNull(chapter.url.splitTwoParts('-'))
		return makeRequest("/lineWebtoon/webtoon/episodeInfo.json?v=4&titleNo=$titleNo&episodeNo=$episodeNo").getJSONObject(
			"episodeInfo",
		).getJSONArray("imageInfo").mapJSONIndexed { i, jo ->
			MangaPage(
				id = generateUid("$titleNo-$episodeNo-$i"),
				url = jo.getString("url"),
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
		val builder = httpUrl.newBuilder().addQueryParameter("serviceZone", "GLOBAL")
		if (httpUrl.queryParameter("v") == null) {
			builder.addQueryParameter("v", "1")
		}
		builder.addQueryParameter("language", languageCode).addQueryParameter("locale", "languageCode")
			.addQueryParameter("platform", "APP_ANDROID")
		signer.makeEncryptUrl(builder)
		return builder.build()
	}

	@MangaSourceParser("WEBTOONS_EN", "Webtoons English", "en", type = ContentType.MANGA)
	class English(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_EN)

	@MangaSourceParser("WEBTOONS_ID", "Webtoons Indonesia", "id", type = ContentType.MANGA)
	class Indonesian(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_ID)

	@MangaSourceParser("WEBTOONS_ES", "Webtoons Spanish", "es", type = ContentType.MANGA)
	class Spanish(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_ES)

	@MangaSourceParser("WEBTOONS_FR", "Webtoons French", "fr", type = ContentType.MANGA)
	class French(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_FR)

	@MangaSourceParser("WEBTOONS_TH", "Webtoons Thai", "th", type = ContentType.MANGA)
	class Thai(context: MangaLoaderContext) : WebtoonsParser(context, MangaParserSource.WEBTOONS_TH)

	@MangaSourceParser("WEBTOONS_ZH", "Webtoons Chinese", "zh", type = ContentType.MANGA)
	class Chinese(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.WEBTOONS_ZH)

	@MangaSourceParser("WEBTOONS_DE", "Webtoons German", "de", type = ContentType.MANGA)
	class German(context: MangaLoaderContext) : LineWebtoonsParser(context, MangaParserSource.WEBTOONS_DE)

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
			urlBuilder.addQueryParameter("msgpad", msgPad).addQueryParameter("md", digest)
//				.addEncodedQueryParameter("md", digest.urlEncoded())
		}
	}

	private class MangaWebtoon(
		@JvmField val manga: Manga,
		@JvmField val date: Long,
		@JvmField val readCount: Long,
	)
}
