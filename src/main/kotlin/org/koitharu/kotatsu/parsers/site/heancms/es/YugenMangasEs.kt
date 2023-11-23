package org.koitharu.kotatsu.parsers.site.heancms.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.urlEncoded

@MangaSourceParser("YUGEN_MANGAS_ES", "YugenMangas.lat", "es", ContentType.HENTAI)
internal class YugenMangasEs(context: MangaLoaderContext) :
	HeanCms(context, MangaSource.YUGEN_MANGAS_ES, "yugenmangas.lat") {

	private val domainAlt = "yugenmangas.net"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		var firstTag = false
		val url = buildString {
			append("https://api.")
			append(domainAlt)
			append("/query?query_string=")
			if (!query.isNullOrEmpty()) {
				append(query.urlEncoded())
			}
			append("&series_status=All&order=desc&orderBy=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("total_views")
				SortOrder.UPDATED -> append("latest")
				SortOrder.NEWEST -> append("created_at")
				SortOrder.ALPHABETICAL -> append("title")
				else -> append("latest")
			}
			append("&series_type=Comic&page=")
			append(page)
			append("&perPage=12&tags_ids=")
			append("[".urlEncoded())
			if (!tags.isNullOrEmpty()) {
				for (tag in tags) {
					// Just to make it fit [1,2,44] ect
					if (!firstTag) {
						firstTag = true
					} else {
						append(",")
					}
					append(tag.key)
				}
			}
			append("]".urlEncoded())
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
