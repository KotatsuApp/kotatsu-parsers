package org.koitharu.kotatsu.parsers.site.ar

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("FLIXSCANS", "Flix Scans", "ar")
internal class FlixScans(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.FLIXSCANS, 18) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)
	override val configKeyDomain = ConfigKey.Domain("flixscans.com")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val json = if (!query.isNullOrEmpty()) {
			if (page > 1) {
				return emptyList()
			}
			val url = "https://api.$domain/api/v1/search/serie"
			val body = JSONObject()
			body.put("title", query.urlEncoded())
			webClient.httpPost(url, body).parseJson().getJSONArray("data")
		} else if (!tags.isNullOrEmpty()) {
			if (page > 1) {
				return emptyList()
			}
			val tagQuery = tags.joinToString(separator = ",") { it.key }
			val url = "https://api.$domain/api/v1/search/advance?=&genres=$tagQuery&serie_type=webtoon"
			webClient.httpGet(url).parseJson().getJSONArray("data")
		} else {
			val url = "https://api.$domain/api/v1/webtoon/homepage/latest/home?page=$page"
			webClient.httpGet(url).parseJson().getJSONArray("data")
		}
		return json.mapJSON { j ->
			val href = "https://$domain/series/${j.getString("prefix")}-${j.getString("id")}-${j.getString("slug")}"
			val cover = "https://api.$domain/storage/" + j.getString("thumbnail")
			Manga(
				id = generateUid(href),
				title = j.getString("title"),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = cover,
				tags = emptySet(),
				state = when (j.getString("status")) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/search/advance").parseHtml()
		val json = JSONArray(doc.requireElementById("__NUXT_DATA__").data())
		val tagsList = json.getJSONArray(3).toString().replace("[", "").replace("]", "").split(",")
		return tagsList.mapNotNullToSet { idTag ->
			val id = idTag.toInt()
			val idKey = json.getJSONObject(id).getInt("id")
			val key = json.get(idKey).toString()
			val idName = json.getJSONObject(id).getInt("name")
			val name = json.get(idName).toString()
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chaptersDeferred = async { loadChapters(manga.url) }
		val json = JSONArray(doc.requireElementById("__NUXT_DATA__").data())
		val descId = json.getJSONObject(6).getInt("story")
		val desc = json.getString(descId)
		val tagsId = json.getJSONObject(6).getInt("genres")
		val tagsList = json.getJSONArray(tagsId).toString().replace("[", "").replace("]", "").split(",")
		val ratingId = json.getJSONObject(6).getInt("rating")
		val rating = json.getString(ratingId)
		val nsfwId = json.getJSONObject(6).getInt("nsfw")
		val nsfw = json.getBoolean(nsfwId)
		manga.copy(
			description = desc,
			tags = tagsList.mapToSet { idTag ->
				val id = idTag.toInt()
				val idKey = json.getJSONObject(id).getInt("id")
				val key = json.get(idKey).toString()
				val idName = json.getJSONObject(id).getInt("name")
				val name = json.get(idName).toString()
				MangaTag(
					key = key,
					title = name,
					source = source,
				)
			},
			rating = rating?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			isNsfw = nsfw,
			chapters = chaptersDeferred.await(),
		)
	}

	private val dateFormat = SimpleDateFormat("yyyy-MM-dd", sourceLocale)

	private suspend fun loadChapters(baseUrl: String): List<MangaChapter> {
		val key = baseUrl.substringAfter("-").substringBefore("-")
		val seriesKey = baseUrl.substringAfterLast("/").substringBefore("-")
		val json = JSONArray(webClient.httpGet("https://api.$domain/api/v1/webtoon/chapters/$key-desc").parseRaw())
		return json.mapJSONIndexed { i, j ->
			val url = "https://$domain/read/webtoon/$seriesKey-${j.getString("id")}-${j.getString("slug")}"
			val date = j.getString("createdAt").substringBeforeLast("T")
			MangaChapter(
				id = generateUid(url),
				url = url,
				name = j.getString("slug").replace("-", " "),
				number = i + 1,
				branch = null,
				uploadDate = dateFormat.tryParse(date),
				scanlator = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val json = JSONArray(doc.requireElementById("__NUXT_DATA__").data())
		val chapterData = json.getJSONObject(6).getInt("chapterData")
		val pageLocate = json.getJSONObject(chapterData).getInt("webtoon")
		val pages = json.getJSONArray(pageLocate).toString().replace("[", "").replace("]", "").split(",")
		return pages.map {
			val id = it.toInt()
			val url = "https://api.$domain/storage/" + json.getString(id)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
