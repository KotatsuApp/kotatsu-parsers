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
import org.koitharu.kotatsu.parsers.util.CryptoAES
import org.json.JSONObject
import org.json.JSONArray
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
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = availableTags.get(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.ABANDONED,
				MangaState.PAUSED,
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
					else -> "all"
				})
			}

			append("&full=true")
                  
			if (filter.tags.isNotEmpty()) {
				append("&genre=")
				append(filter.tags.joinToString(separator = ",") { it.key })
			}
		}

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("comics")

		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val allTags = fetchTags().orEmpty()
			val tags = allTags.let { allTags ->
				jo.optJSONArray("genres")?.asTypedList<String>()?.mapNotNullToSet { g ->
					allTags.find { x -> x.key == g }
				}
			}.orEmpty()
			
			Manga(
				id = generateUid(id),
				url = "/comics/$id",
				publicUrl = "https://$domain/comic/$id",
				title = jo.getString("title"),
				altTitles = setOf(jo.getString("anotherName")),
				coverUrl = jo.getString("thumbnail"),
				largeCoverUrl = jo.getString("thumbnail"),
				authors = emptySet(),
				tags = tags,
				state = when(jo.getString("status")) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					"hiatus" -> MangaState.PAUSED
					"cancelled" -> MangaState.ABANDONED
					else -> null
				},
				description = jo.getString("description"),
				contentRating = if (jo.getBooleanOrDefault("r18", false)) ContentRating.ADULT else ContentRating.SUGGESTIVE,
				source = source,
				rating = RATING_UNKNOWN,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val json = webClient.httpGet("https://$apiSuffix/${manga.url}").parseJson()
		val id = json.getLong("id")

		val authors = json.optJSONArray("authors")?.mapJSONToSet { jo ->
			jo.getString("name")
		}.orEmpty()

		val team = json.optJSONArray("teams")?.getJSONObject(0)?.getString("name")

		val allTags = fetchTags().orEmpty()
		val tags = allTags.let { allTags ->
			json.optJSONArray("genres")?.asTypedList<String>()?.mapNotNullToSet { g ->
				allTags.find { x -> x.key == g }
			}
		}.orEmpty()

		val chaptersDeferred = async {
			webClient.httpGet("https://$apiSuffix/chapters/comic/$id").parseJsonArray()
		}

		manga.copy(
			title = json.getString("title"),
			altTitles = setOf(json.getString("anotherName")),
			contentRating = if (json.getBooleanOrDefault("r18", false)) {
				ContentRating.ADULT
			} else {
				ContentRating.SUGGESTIVE
			},
			authors = authors,
			tags = tags,
			description = json.getString("description"),
			state = when(json.getString("status")) {
				"ongoing" -> MangaState.ONGOING
				"completed" -> MangaState.FINISHED
				"hiatus" -> MangaState.PAUSED
				"cancelled" -> MangaState.ABANDONED
				else -> null
			},
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
