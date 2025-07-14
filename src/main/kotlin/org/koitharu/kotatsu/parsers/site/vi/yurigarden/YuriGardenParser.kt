package org.koitharu.kotatsu.parsers.site.vi.yurigarden

import androidx.collection.arraySetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import org.json.JSONObject
import java.util.*

internal abstract class YuriGardenParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	protected val isR18Enable: Boolean = false
) : LegacyPagedMangaParser(context, source, 18) {

	private val availableTags = suspendLazy(initializer = ::fetchTags)

	override val configKeyDomain = ConfigKey.Domain(domain)
	private val apiSuffix = "api.$domain"

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("x-app-origin", "https://$domain")
		.add("User-Agent", UserAgents.KOTATSU)
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isSearchWithFiltersSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = availableTags.get(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.ABANDONED,
				MangaState.PAUSED,
				MangaState.UPCOMING,
			),
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(apiSuffix)
			append("/comics")
			append("?page=")
			append(page)
			append("&limit=")
			append(pageSize)
			append("&r18=")
			append(isR18Enable)

			append("&sort=")
			append(when (order) {
				SortOrder.NEWEST -> "newest"
				SortOrder.NEWEST_ASC -> "oldest"
				else -> "newest" // default
			})

			if (!filter.query.isNullOrEmpty()) {
				append("&search=")
				append(filter.query.urlEncoded())
			}

			filter.states.oneOrThrowIfMany()?.let { state ->
				append("&status=")
				append(when (state) {
					MangaState.ONGOING -> "ongoing"
					MangaState.FINISHED -> "completed"
					MangaState.PAUSED -> "hiatus"
					MangaState.ABANDONED -> "cancelled"
					MangaState.UPCOMING -> "oncoming"
					else -> "all"
				})
			}

			append("&full=true")
                  
			if (filter.tags.isNotEmpty()) {
				append("&genre=")
				append(filter.tags.joinToString(separator = ",") { it.key })
			}

			if (!filter.author.isNullOrEmpty()) {
				clear()

				append("https://")
				append(apiSuffix)
				append("/creators/authors/")
				append(
					filter.author.substringAfter("(").substringBefore(")")
				)

				return@buildString // end of buildString
			}
		}

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("comics")

		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val altTitles = setOf(jo.optString("anotherName", null))
				.filterNotNull()
				.toSet()
			val tags = fetchTags().let { allTags ->
				jo.optJSONArray("genres")?.asTypedList<String>()?.mapNotNullToSet { g ->
					allTags.find { x -> x.key == g }
				}
			}.orEmpty()
			
			Manga(
				id = generateUid(id),
				url = "/comics/$id",
				publicUrl = "https://$domain/comic/$id",
				title = jo.getString("title"),
				altTitles = altTitles,
				coverUrl = jo.getString("thumbnail"),
				largeCoverUrl = jo.getString("thumbnail"),
				authors = emptySet(),
				tags = tags,
				state = when(jo.optString("status")) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					"hiatus" -> MangaState.PAUSED
					"cancelled" -> MangaState.ABANDONED
					"oncoming" -> MangaState.UPCOMING
					else -> null
				},
				description = jo.optString("description").orEmpty(),
				contentRating = if (jo.getBooleanOrDefault("r18", false)) ContentRating.ADULT else ContentRating.SUGGESTIVE,
				source = source,
				rating = RATING_UNKNOWN,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val id = manga.url.substringAfter("/comics/")
		val json = webClient.httpGet("https://$apiSuffix/comics/${id}").parseJson()

		val authors = json.optJSONArray("authors")?.mapJSONToSet { jo ->
			jo.getString("name") + " (${jo.getLong("id")})"
		}.orEmpty()

		val altTitles = setOf(json.getString("anotherName"))
		val description = json.getString("description")
		val team = json.optJSONArray("teams")?.getJSONObject(0)?.getString("name")

		val chaptersDeferred = async {
			webClient.httpGet("https://$apiSuffix/chapters/comic/${id}").parseJsonArray()
		}

		manga.copy(
			altTitles = altTitles,
			authors = authors,
			chapters = chaptersDeferred.await().mapChapters() { _, jo ->
				val chapId = jo.getLong("id")
				MangaChapter(
					id = generateUid(chapId),
					title = jo.getString("name"),
					number = jo.getFloatOrDefault("order", 0f),
					volume = 0,
					url = "$chapId",
					scanlator = team,
					uploadDate = jo.getLong("lastUpdated"),
					branch = null,
					source = source,
				)
			},
			description = description,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
            val json = webClient.httpGet("https://$apiSuffix/chapters/${chapter.url}").parseJson()
            val pages = json.getJSONArray("pages").asTypedList<JSONObject>()

            return pages.mapIndexed { index, page ->
                val pageUrl = page.getString("url")
                MangaPage(
                    id = generateUid(index.toLong()),
                    url = pageUrl,
                    preview = null,
                    source = source,
                )
            }
        }

	private suspend fun fetchTags(): Set<MangaTag> {
		val json = webClient.httpGet("https://$apiSuffix/resources/systems_vi.json").parseJson()
		val genres = json.getJSONObject("genres")
		return genres.keys().asSequence().mapTo(arraySetOf()) { key ->
			val genre = genres.getJSONObject(key)
			MangaTag(
				title = genre.getString("name").toTitleCase(sourceLocale),
				key = genre.getString("slug"),
				source = source,
			)
		}
	}
}
