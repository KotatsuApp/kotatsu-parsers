package org.koitharu.kotatsu.parsers.site.heancms.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("YUGEN_MANGAS_ES", "YugenMangas.lat", "es", ContentType.HENTAI)
internal class YugenMangasEs(context: MangaLoaderContext) :
	HeanCms(context, MangaSource.YUGEN_MANGAS_ES, "yugenmangas.lat") {

	private val domainAlt = "yugenmangas.net"

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://api.")
			append(domainAlt)
			append("/query?query_string=")
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
						SortOrder.ALPHABETICAL -> append("title&order=desc")
						SortOrder.ALPHABETICAL_DESC -> append("title&order=asc")
						else -> append("latest&order=desc")
					}
					append("&series_type=Comic&perPage=12")
					append("&tags_ids=")
					append("[".urlEncoded())
					append(filter.tags.joinToString(",") { it.key })
					append("]".urlEncoded())
				}

				null -> {}
			}

			append("&page=")
			append(page.toString())
		}

		val json = webClient.httpGet(url).parseJson()

		return json.getJSONArray("data").mapJSON { j ->
			val slug = j.getString("series_slug")
			val urlManga = "https://$domain/series/$slug"
			val cover = if (j.getString("thumbnail").contains('/')) {
				j.getString("thumbnail")
			} else {
				"https://api.$domainAlt/${j.getString("thumbnail")}"
			}
			Manga(
				id = generateUid(urlManga),
				title = j.getString("title"),
				altTitle = null,
				url = urlManga,
				publicUrl = urlManga,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = cover,
				tags = setOf(),
				state = when (j.getString("status")) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Dropped" -> MangaState.ABANDONED
					"Hiatus" -> MangaState.PAUSED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}
}
