package org.koitharu.kotatsu.parsers.site.id

import androidx.collection.arraySetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Headers
import okio.IOException
import org.jsoup.HttpStatusException
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.net.HttpURLConnection
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
		}

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("data")
		return data.mapJSON { jo ->
			val id = generateUid(jo.getString("manga_id"))
			Manga(
				id = generateUid(id),
				url = "/manga/$id",
				publicUrl = "https://$domain/manga/$id",
				title = jo.getString("title"),
				altTitles = setOf(jo.optString("alternative_title") ?: ""),
				coverUrl = jo.getString("cover_image_url"),
				largeCoverUrl = jo.optString("cover_portrait_url").takeIf { it.isNotEmpty() },
				authors = jo.getJSONObject("taxonomy").getJSONArray("Author").mapJSONToSet { x ->
					x.getString("name")
				},
				tags = jo.getJSONObject("taxonomy").getJSONArray("Genre").mapJSONToSet { x ->
					MangaTag(
						key = x.getString("slug"),
						title = x.getString("name"),
						source = source
					)
				},
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

	// Fake functions:

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val author = doc.selectFirst("td:contains(Author:) + td")?.textOrNull()
		return manga.copy(
			tags = doc.select("td:contains(Genres:) + td a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/').substringBefore("-comic"),
					title = a.text().toTitleCase(sourceLocale),
					source = source,
				)
			},
			authors = setOfNotNull(author),
			state = when (doc.selectFirst("td:contains(Status:) + td a")?.text()?.lowercase()) {
				"ongoing" -> MangaState.ONGOING
				"completed" -> MangaState.FINISHED
				else -> null
			},
			description = doc.selectFirst("div.manga-desc p.pdesc")?.html(),
			chapters = doc.select("ul.basic-list li").mapChapters(reversed = true) { i, li ->
				val a = li.selectFirst("a.ch-name") ?: return@mapChapters null
				val href = a.attrAsRelativeUrl("href")
				val name = a.text()

				MangaChapter(
					id = generateUid(href),
					title = name,
					number = name.substringAfter('#').toFloatOrNull() ?: (i + 1f),
					url = href,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
					volume = 0,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain) + "/all"
		val doc = webClient.httpGet(fullUrl).parseHtml()

		return doc.select("img.chapter_img.lazyload").mapNotNull { img ->
			val imageUrl = img.attrOrNull("data-src") ?: return@mapNotNull null
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
	
}