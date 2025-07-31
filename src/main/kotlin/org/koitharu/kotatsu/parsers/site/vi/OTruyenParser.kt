package org.koitharu.kotatsu.parsers.site.vi

import org.jsoup.HttpStatusException
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Favicon
import org.koitharu.kotatsu.parsers.model.Favicons
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale
import java.util.TimeZone

@MangaSourceParser("OTRUYEN", "Ổ Truyện", "vi")
internal class OTruyenParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.OTRUYEN, 24) {

	override val configKeyDomain = ConfigKey.Domain("otruyenapi.com")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.KOTATSU)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.remove(configKeyDomain)
		keys.remove(userAgentKey)
	}

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon(
					"https://otruyen.cc/favicon.ico", 32, null),
			),
			domain,
		)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = fetchTags(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.UPCOMING,
			),
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/v1/api/tim-kiem?keyword=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {
					val tag = filter.tags.oneOrThrowIfMany()
					if (tag != null) {
						append("/v1/api/the-loai/")
						append(tag.key)
						append("?page=")
						append(page)
					} else if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									MangaState.ONGOING -> "/v1/api/danh-sach/dang-phat-hanh?page=${page}"
									MangaState.FINISHED -> "/v1/api/danh-sach/hoan-thanh?page=${page}"
									MangaState.UPCOMING -> "/v1/api/danh-sach/sap-ra-mat?page=${page}"
									else -> "/v1/api/danh-sach/dang-phat-hanh?page=${page}" // default
								}
							)
						}
					} else {
						append("/v1/api/danh-sach/truyen-moi") // SortOrder.NEWEST
						append("?page=${page}")
					}
				}
			}
		}

		val json = try {
			webClient.httpGet(url).parseJson()
		} catch (e: HttpStatusException) {
			if (e.statusCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
				return emptyList()
			} else {
				throw e
			}
		}

		val items = json.getJSONObject("data").getJSONArray("items")
		return items.mapJSON { jo ->
			Manga(
				id = generateUid(jo.getString("_id").hashCode().toLong()),
				url = jo.getString("slug"),
				publicUrl = "https://otruyen.cc/truyen-tranh/${jo.getString("slug")}",
				title = jo.getString("name"),
				altTitles = emptySet(),
				coverUrl = "https://img.otruyenapi.com/uploads/comics/${jo.getString("thumb_url")}",
				authors = emptySet(),
				tags = emptySet(),
				state = when (jo.getString("status")) {
					"ongoing" -> MangaState.ONGOING
					"coming_soon" -> MangaState.UPCOMING
					"completed" -> MangaState.FINISHED
					else -> null
				},
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				source = source,
				rating = RATING_UNKNOWN,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = "https://$domain/v1/api/truyen-tranh/${manga.url}"
		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONObject("data")
		val item = data.getJSONObject("item")

		val chapters = item.getJSONArray("chapters")
			.mapJSON { it.getJSONArray("server_data") }
			.flatMap { array -> (0 until array.length()).map { array.getJSONObject(it) } }
			.map { jo ->
				val apiData = jo.getString("chapter_api_data")
				MangaChapter(
					id = generateUid(apiData),
					title = jo.optString("chapter_title").ifBlank {
						"Chương ${jo.optString("chapter_name")}"
					},
					number = jo.optString("chapter_name").toFloatOrNull() ?: 0f,
					volume = 0,
					url = apiData,
					scanlator = null,
					uploadDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
						timeZone = TimeZone.getTimeZone("UTC+7")
					}.parseSafe(item.optString("updatedAt")),
					branch = null,
					source = source
				)
			}

		return manga.copy(
			altTitles = item.getJSONArray("origin_name").let { jsonArray ->
				(0 until jsonArray.length()).mapTo(mutableSetOf()) { jsonArray.getString(it) }
			},
			authors = item.getJSONArray("author").let { jsonArray ->
				(0 until jsonArray.length()).mapTo(mutableSetOf()) { jsonArray.getString(it) }
			},
			tags = item.optJSONArray("category").mapJSONToSet { jo ->
				MangaTag(
					title = jo.optString("name"),
					key = jo.optString("slug"),
					source = source
				)
			},
			description = item.optString("content"),
			state = when (item.optString("status")) {
				"ongoing" -> MangaState.ONGOING
				"coming_soon" -> MangaState.UPCOMING
				"completed" -> MangaState.FINISHED
				else -> null
			},
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet(chapter.url).parseJson()
		val data = json.getJSONObject("data")
		val item = data.getJSONObject("item")
		val domainCdn = data.optString("domain_cdn")
		val chapterPath = item.optString("chapter_path")

		return item.getJSONArray("chapter_image").mapJSON { page ->
			val imageFile = page.optString("image_file")
			val imgUrl = "$domainCdn/$chapterPath/$imageFile"
			MangaPage(
				id = generateUid(imgUrl),
				url = imgUrl,
				preview = null,
				source = source
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val url = "https://$domain/v1/api/the-loai"
		val items = webClient.httpGet(url)
			.parseJson()
			.getJSONObject("data")
			.getJSONArray("items")

		return items.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name"),
				key = jo.getString("slug"),
				source = source,
			)
		}
	}
}
