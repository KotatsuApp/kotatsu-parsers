package org.koitharu.kotatsu.parsers.site.id

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*
import org.koitharu.kotatsu.parsers.Broken

@Broken("TODO: Add author search")
@MangaSourceParser("SHINIGAMI", "Shinigami", "id")
internal class Shinigami(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.SHINIGAMI, 24) {

	override val configKeyDomain = ConfigKey.Domain("id.shinigami.asia")
	private val apiSuffix = "api.shngm.io/v1"
	private val cdnSuffix = "storage.shngm.id"

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}
	
	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("referer", "https://$domain/")
		.add("sec-fetch-dest", "empty")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.RATING,
		SortOrder.RATING_ASC,
	)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = fetchTags(),
			availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED),
			availableContentTypes = EnumSet.of(
				ContentType.MANGA,
				ContentType.MANHWA,
				ContentType.MANHUA
			),
		)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(apiSuffix)
			append("/manga/list")
			append("?page=")
			append(page.toString())
			append("&page_size=")
			append(pageSize)

			append("&sort=")
			append(
				when (order) {
					SortOrder.POPULARITY, SortOrder.POPULARITY_ASC -> "popularity"
					SortOrder.NEWEST, SortOrder.NEWEST_ASC -> "latest"
					SortOrder.RATING, SortOrder.RATING_ASC -> "rating"
					else -> "latest"
				}
			)
			append("&sort_order=")
			append(
				when (order) {
					SortOrder.POPULARITY_ASC, SortOrder.NEWEST_ASC, SortOrder.RATING_ASC -> "asc"
					else -> "desc"
				}
			)

			filter.states.oneOrThrowIfMany()?.let {
				append("&status=")
				append(
					when (it) {
						MangaState.FINISHED -> "completed"
						MangaState.ONGOING -> "ongoing"
						MangaState.PAUSED -> "hiatus"
						else -> ""
					}
				)
			}

			if (filter.types.isNotEmpty()) {
				filter.types.forEach {
					append("&format=")
					append(
						when (it) {
							ContentType.MANGA -> "manga"
							ContentType.MANHUA -> "manhua"
							ContentType.MANHWA -> "manhwa"
							else -> ""
						}
					)
				}
			}

			if (filter.tags.isNotEmpty()) {
				append("&genres_include=")
				filter.tags.joinTo(this, ",") { it.key }
				append("&genres_include_mode=and")
			}

			if (filter.tagsExclude.isNotEmpty()) {
				append("&genres_exclude=")
				filter.tagsExclude.joinTo(this, ",") { it.key }
				append("&genres_exclude_mode=and")
			}

			if (!filter.query.isNullOrEmpty()) {
				append("&q=")
				val encodedQuery = filter.query.splitByWhitespace().joinToString(separator = "+") { part ->
					part.urlEncoded()
				}
				append(encodedQuery)
			}
		}

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("data")
		return data.mapJSON { jo ->
			val id = generateUid(jo.getString("manga_id"))
			Manga(
				id = generateUid(id),
				url = "/manga/detail/$id",
				publicUrl = "https://$domain/series/$id",
				title = jo.getString("title"),
				altTitles = setOf(jo.optString("alternative_title") ?: ""),
				coverUrl = jo.getString("cover_image_url"),
				largeCoverUrl = jo.optString("cover_portrait_url").takeIf { it.isNotEmpty() },
				authors = jo.optJSONObject("taxonomy")?.optJSONArray("Author")?.mapJSONToSet { x ->
					x.getString("name")
				}.orEmpty(),
				tags = jo.optJSONObject("taxonomy")?.optJSONArray("Genre")?.mapJSONToSet { x ->
					MangaTag(
						key = x.getString("slug"),
						title = x.getString("name"),
						source = source
					)
				}.orEmpty(),
				state = when (jo.getInt("status")) {
					1 -> MangaState.ONGOING
					2 -> MangaState.FINISHED
					3 -> MangaState.PAUSED
					else -> null
				},
				description = jo.optString("description"),
				contentRating = null,
				source = source,
				rating = RATING_UNKNOWN
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = webClient.httpGet("https://$apiSuffix" + manga.url).parseJson()
		val jo = json.getJSONObject("data")
		val id = jo.getString("manga_id")
		return manga.copy(
			tags = jo.optJSONObject("taxonomy")?.optJSONArray("Genre")?.mapJSONToSet { x ->
				MangaTag(
					key = x.getString("slug"),
					title = x.getString("name"),
					source = source,
				)
			}.orEmpty(),
			authors = jo.optJSONObject("taxonomy")?.optJSONArray("Author")?.mapJSONToSet { x ->
				x.getString("name")
			}.orEmpty(),
			state = when (jo.getInt("status")) {
				1 -> MangaState.ONGOING
				2 -> MangaState.FINISHED
				3 -> MangaState.PAUSED
				else -> null
			},
			description = jo.optString("description"),
			largeCoverUrl = jo.optString("cover_portrait_url").takeIf { it.isNotEmpty() },
			chapters = getChapters(id)
		)
	}

	private suspend fun getChapters(mangaId: String): List<MangaChapter> {
		val url = "https://$apiSuffix/chapter/$mangaId/list?page=1&page_size=24&sort_by=chapter_number&sort_order=asc"
		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("data")
		
		return data.mapJSON { jo ->
			val chapterId = jo.getString("chapter_id")
			val number = jo.getInt("chapter_number").toFloat()
			val title = jo.optString("chapter_title").takeIf { it.isNotEmpty() } 
				?: "Chapter $number"
			
			MangaChapter(
				id = generateUid(chapterId),
				title = title,
				number = number,
				url = "chapter/detail/$chapterId",
				scanlator = null,
				uploadDate = jo.getString("release_date").parseDate(),
				branch = null,
				source = source,
				volume = 0
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet("https://$apiSuffix/${chapter.url}").parseJson()
		val data = json.getJSONObject("data")
		val chapterData = data.getJSONObject("chapter")
		val basePath = chapterData.getString("path")
		val datas = chapterData.getJSONArray("data")
		
		return datas.mapJSON { imgs ->
			val imageUrl = "https://$cdnSuffix" + basePath + imgs
			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val json = webClient.httpGet("https://$apiSuffix/genre/list").parseJson()
		return json.getJSONArray("data").mapJSONToSet { x ->
			MangaTag(
				key = x.getString("slug"),
				title = x.getString("name"),
				source = source,
			)
		}
	}

	private fun String.parseDate(): Long {
		return try {
			SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
				timeZone = TimeZone.getTimeZone("UTC")
			}.parse(this)?.time ?: 0L
		} catch (e: Exception) {
			0L
		}
	}
}