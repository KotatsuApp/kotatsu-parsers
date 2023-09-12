package org.koitharu.kotatsu.parsers.site.uk

import androidx.collection.ArraySet
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import java.text.SimpleDateFormat
import java.util.*

private const val HEADER_ENCODING = "Content-Encoding"
private val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
@MangaSourceParser("HENTAIUKR", "Hentaiukr", "uk")
class HentaiUkrParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.HENTAIUKR, 1),
	Interceptor {

	private val allManga get() = "https://$domain/search/objects.json"

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("hentaiukr.com")

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val json= webClient.httpGet(allManga).parseJson().getJSONArray("manga").toJSONList().find { it.getString("name") == manga.title }
		val html_manga = webClient.httpGet("https://$domain${manga.url}").parseHtml()
		val about = html_manga.body().requireElementById("about").text()

		return manga.copy(
			description = about,
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.id),
					name = manga.title,
					number = 1,
					url = manga.url,
					scanlator = null,
					uploadDate = date.tryParse(json!!.getString("add_date")),
					branch = null,
					source = source,
				)
			)
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		// No pagination
		if(page != 1){
			return emptyList()
		}

		// Get all manga
		var allManga = webClient.httpGet(allManga).parseJson().getJSONArray("manga").toJSONList()

		// Search
		if (!query.isNullOrBlank()){
			val queryArray = ArraySet<JSONObject>()
			allManga.map { item ->
				when (query.toString()) {
					in item.getString("name") -> queryArray.add(item)
					in item.getString("eng_name") -> queryArray.add(item)
					in item.getString("orig_name") -> queryArray.add(item)
					in item.getString("author") -> queryArray.add(item)
					in item.getString("team") -> queryArray.add(item)
					else -> {}
				}
			}
			allManga = queryArray.toList()
		}

		// Return to app
		return allManga.map { jo ->
			val id = jo.getString("id")
			Manga(
				id = generateUid(id),
				title = jo.getString("name"),
				altTitle = jo.getStringOrNull("eng_name"),
				url = jo.getString("url"),
				publicUrl = "https://$domain${jo.getString("url")}",
				rating = RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = "https://$domain${jo.getString("thumb")}",
				tags = getTags(jo.optJSONArray("tags")),
				state = null,
				author = jo.getString("author"),
				largeCoverUrl = null,
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val html_pages = webClient.httpGet( "https://$domain${chapter.url}vertical_reader.html").parseHtml()
		return html_pages.select("img.image").mapIndexed { i, page ->
			MangaPage(
				id = generateUid(i.toString()),
				"https://$domain${page.attr("src")}",
				null,
				source
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		return emptySet()
	}

	private fun getTags(jsonTags: JSONArray): Set<MangaTag> {
		val tagsSet = ArraySet<MangaTag>(jsonTags.length())
		repeat(jsonTags.length()) { i ->
			val item = jsonTags.getJSONObject(i)

			tagsSet.add(MangaTag(title = item.getString("name"), key = item.getString("id"), source = source))
		}
		return tagsSet
	}

	// Need for disable encoding (with encoding not working)
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = if (request.header(HEADER_ENCODING) != null) {
			request.newBuilder().removeHeader(HEADER_ENCODING).build()
		} else {
			request
		}
		return chain.proceed(newRequest)
	}
}

