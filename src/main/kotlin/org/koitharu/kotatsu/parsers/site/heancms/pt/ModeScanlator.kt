package org.koitharu.kotatsu.parsers.site.heancms.pt

import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MODESCANLATOR", "ModeScanlator", "pt")
internal class ModeScanlator(
	context: MangaLoaderContext,
) : HeanCms(context, MangaSource.MODESCANLATOR, "modescanlator.com") {

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://api.")
			append(domain)
			append("/query?adult=true&query_string=")
			when (filter) {
				is MangaListFilter.Search -> {
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					filter.states.oneOrThrowIfMany()?.let {
						append("&series_status=")
						append(
							when (it) {
								MangaState.ONGOING -> "Ongoing"
								MangaState.FINISHED -> "Completed"
								MangaState.ABANDONED -> "Dropped"
								MangaState.PAUSED -> "Hiatus"
								else -> ""
							},
						)

					}
					append("&orderBy=")
					when (filter.sortOrder) {
						SortOrder.POPULARITY -> append("total_views&order=desc")
						SortOrder.UPDATED -> append("latest&order=desc")
						SortOrder.NEWEST -> append("created_at&order=desc")
						SortOrder.ALPHABETICAL -> append("title&order=asc")
						SortOrder.ALPHABETICAL_DESC -> append("title&order=desc")
						else -> append("latest&order=desc")
					}
					append("&series_type=All&perPage=")
					append(pageSize)
					append("&tags_ids=")
					append("[".urlEncoded())
					filter.tags.joinTo(this, ",") { it.key }
					append("]".urlEncoded())

				}

				null -> {}
			}
			append("&page=")
			append(page)
		}
		val json = webClient.httpGet(url).parseJson()
		return json.getJSONArray("data").mapJSON { j ->
			val slug = j.getString("series_slug")
			val urlManga = "https://$domain/$pathManga/$slug"
			val cover = if (j.getString("thumbnail").contains('/')) {
				j.getString("thumbnail")
			} else {
				"https://api.$domain/${j.getString("thumbnail")}"
			}
			Manga(
				id = j.getLong("id"),
				title = j.getString("title"),
				altTitle = null,
				url = urlManga.toRelativeUrl(domain),
				publicUrl = urlManga,
				rating = j.getFloatOrDefault("rating", RATING_UNKNOWN) / 5f,
				isNsfw = isNsfwSource,
				coverUrl = cover,
				tags = setOf(),
				state = when (j.getStringOrNull("status")) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Dropped" -> MangaState.ABANDONED
					"Hiatus" -> MangaState.PAUSED
					else -> null
				},
				author = j.getStringOrNull("author"),
				source = source,
				description = j.getString("description"),
			)
		}

	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = buildString {
			append("https://api.")
			append(domain)
			append("/chapter/query?perPage=9999&series_id=")
			append(manga.id)
		}
		val json = webClient.httpGet(url).parseJson()
		val dateFormat = SimpleDateFormat(datePattern, Locale.ENGLISH)

		val chaptersJsonArray = json.getJSONArray("data")
		var totalChapters = json.getJSONObject("meta").getInt("total")
		val chapters = chaptersJsonArray.mapJSON { j ->
			val slug = j.getJSONObject("series").getString("series_slug")
			val chapterUrl = "https://$domain/$pathManga/$slug/${j.getString("chapter_slug")}"
			val date = j.getString("created_at").substringBeforeLast("T")
			MangaChapter(
				id = j.getLong("id"),
				url = chapterUrl,
				name = j.getString("chapter_name"),
				number = totalChapters--,
				branch = null,
				uploadDate = dateFormat.tryParse(date),
				scanlator = null,
				source = source,
			)
		}

		return manga.copy(
			chapters = chapters.reversed(),
		)
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comics").parseHtml()

		val regex = Regex("\"tags\\\\.*?(\\[.+?])")
		val tags = doc.select("script").firstNotNullOf { script ->
			regex.find(script.html())?.groupValues?.getOrNull(1)
		}.unescapeJson()
		return JSONArray(tags).mapJSONToSet {
			MangaTag(
				key = it.getInt("id").toString(),
				title = it.getString("name").toTitleCase(sourceLocale),
				source = source,
			)
		}
	}
}
