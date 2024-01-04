package org.koitharu.kotatsu.parsers.site.uk

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import java.text.SimpleDateFormat
import java.util.*

private const val HEADER_ENCODING = "Content-Encoding"
private const val PAGE_SIZE = 60

// NOTE High profile focus
@MangaSourceParser("HENTAIUKR", "HentaiUkr", "uk", ContentType.HENTAI)
class HentaiUkrParser(context: MangaLoaderContext) : MangaParser(context, MangaSource.HENTAIUKR), Interceptor {

	private val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)

	private val allManga = SoftSuspendLazy {
		runCatchingCancellable {
			webClient.httpGet("https://$domain/search/objects.json").parseJson()
		}.recoverCatchingCancellable {
			webClient.httpGet("https://$domain/search/objects2.json").parseJson()
		}.recoverCatchingCancellable {
			webClient.httpGet("https://$domain/search/objects69.json").parseJson()
		}.getOrThrow().getJSONArray("manga").toJSONList()
	}

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("hentaiukr.com")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
	)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val jsonDeferred = async { allManga.get().first { it.getString("url") == manga.url } }
		val htmlDeferred = async { webClient.httpGet("https://$domain${manga.url}").parseHtml() }

		val about = htmlDeferred.await().body().requireElementById("about").text()

		manga.copy(
			description = about,
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.id),
					name = manga.title,
					number = 1,
					url = manga.url,
					scanlator = null,
					uploadDate = date.tryParse(jsonDeferred.await().getString("add_date")),
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		tagsExclude: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		// Get all manga
		val json = allManga.get().toMutableList()

		// Search
		if (!query.isNullOrEmpty()) {
			json.retainAll { item ->
				item.getString("name").contains(query, ignoreCase = true) ||
					item.getStringOrNull("eng_name")?.contains(query, ignoreCase = true) == true ||
					item.getStringOrNull("orig_name")?.contains(query, ignoreCase = true) == true ||
					item.getStringOrNull("author")?.contains(query, ignoreCase = true) == true ||
					item.getStringOrNull("team")?.contains(query, ignoreCase = true) == true
			}
		}

		if (!tags.isNullOrEmpty()) {
			val ids = tags.mapToSet { it.key }
			json.retainAll { item ->
				item.getJSONArray("tags")
					.mapJSON { it.getAsString() }
					.any { x -> x in ids }
			}
		}

		// Return to app
		return json.drop(offset).take(PAGE_SIZE).map { jo ->
			val id = jo.getAsLong()
			Manga(
				id = generateUid(id),
				title = jo.getString("name"),
				altTitle = jo.getStringOrNull("eng_name"),
				url = jo.getString("url"),
				publicUrl = jo.getString("url").toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = jo.getString("thumb").toAbsoluteUrl(domain),
				tags = getTags(jo.optJSONArray("tags")),
				state = null,
				author = jo.getStringOrNull("author"),
				largeCoverUrl = null,
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val htmlPages = webClient.httpGet("https://$domain${chapter.url}vertical_reader.html").parseHtml()
		return htmlPages.select("img.image").mapIndexed { i, page ->
			MangaPage(
				id = generateUid(i.toString()),
				"https://$domain${page.attr("src")}",
				null,
				source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return allManga.get().flatMapTo(HashSet()) { x ->
			x.getJSONArray("tags").mapJSON { t ->
				MangaTag(
					title = t.getString("name"),
					key = t.getAsString(),
					source = source,
				)
			}
		}
	}

	private fun getTags(jsonTags: JSONArray): Set<MangaTag> {
		val tagsSet = ArraySet<MangaTag>(jsonTags.length())
		repeat(jsonTags.length()) { i ->
			val item = jsonTags.getJSONObject(i)
			tagsSet.add(
				MangaTag(
					title = item.getString("name"),
					key = item.getAsString(),
					source = source,
				),
			)
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

	private fun JSONObject.getAsLong(): Long {
		val rawValue = opt("id")
		return when (rawValue) {
			null, JSONObject.NULL -> null
			is Long -> rawValue
			is Number -> rawValue.toLong()
			is String -> rawValue.toLong()
			else -> null
		} ?: error("Cannot read value $rawValue as Long")
	}

	private fun JSONObject.getAsString(): String {
		return get("id").toString()
	}
}
