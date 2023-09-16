package org.koitharu.kotatsu.parsers.site.heancms

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

internal abstract class HeanCms(
	context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	//For some sources, you need to send a json. For the moment, this part only works in Get. ( ex source need json gloriousscan.com , omegascans.org )
	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		var firstTag = false
		val url = buildString {
			append("https://api.")
			append(domain)
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
				"https://api.$domain/${j.getString("thumbnail")}"
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
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	protected open val datePattern = "yyyy-MM-dd"
	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, Locale.ENGLISH)

		val slug = manga.url.substringAfterLast('/')
		val chapter = root.selectFirstOrThrow("script:containsData(chapter_slug)").data()
			.replace("\\", "")
			.substringAfter("\"seasons\":")
			.substringBefore("}]}],\"children\"")
			.split("chapter_name")
			.drop(1)

		return manga.copy(
			altTitle = root.selectFirstOrThrow("p.text-center.text-gray-400").text(),
			tags = emptySet(),
			author = root.select("div.flex.flex-col.gap-y-2 p:contains(Autor:) strong").text(),
			description = root.selectFirst("h5:contains(Desc) + .bg-gray-800")?.html(),
			chapters = chapter.mapChapters(reversed = true) { i, it ->
				val slugChapter = it.substringAfter("chapter_slug\":\"").substringBefore("\",\"")
				val url = "https://$domain/series/$slug/$slugChapter"
				val date = it.substringAfter("created_at\":\"").substringBefore("\",\"").substringBefore("T")
				val name = slugChapter.replace("-", " ")
				MangaChapter(
					id = generateUid(url),
					name = name,
					number = i + 1,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(date),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("p.flex img").map { img ->
			val url = img.src() ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comics").parseHtml()

		val tags = doc.selectFirstOrThrow("script:containsData(Genres)").data()
			.replace("\\", "")
			.substringAfterLast("\"Genres\"")
			.split("\",{\"")
			.drop(1)

		return tags.mapNotNullToSet {
			MangaTag(
				key = it.substringAfter("id\":").substringBefore(",\""),
				title = it.substringAfter("name\":\"").substringBefore("\"}]"),
				source = source,
			)
		}
	}
}
