package org.koitharu.kotatsu.parsers.site.en

import com.daveanthonythomas.moshipack.MoshiPack
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.lang.IllegalArgumentException
import java.util.EnumSet

@MangaSourceParser("ANCHIRA", "Anchira", "en", ContentType.HENTAI)
internal class Anchira(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.ANCHIRA, 24) {

	private fun Response.decodeAsJson(): String {
		val data = use { it.body?.bytes() } ?: throw IllegalArgumentException("Response body is null")

		return MoshiPack().msgpackToJson(data)
	}

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("anchira.to")

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST, SortOrder.ALPHABETICAL)

	private val apiHeaders = Headers.Builder()
		.set("X-Requested-With", "XMLHttpRequest")
		.build()

	override suspend fun getTags(): Set<MangaTag> {
		val url = "https://$domain/api/v1/tags"
		val rawJson = webClient.httpGet(url, apiHeaders).decodeAsJson()

		return JSONArray(rawJson).mapJSONToSet {
			MangaTag(
				title = it.getString("name"),
				key = it.getString("name"),
				source = source
			)
		}
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder
	): List<Manga> {
		val url = "https://$domain/api/v1/library".toHttpUrl().newBuilder().apply {
			var advQuery = ""
			tags?.onEach {
				advQuery += "tag:\"^${it.key}$\" "
			}
			if (advQuery.isNotEmpty()) {
				addQueryParameter("s", query?.let { "$it $advQuery" } ?: advQuery)
			} else if (!query.isNullOrEmpty()) {
				addQueryParameter("s", query.trim())
			}
			when (sortOrder) {
				SortOrder.POPULARITY -> addQueryParameter("sort", "32")
				SortOrder.NEWEST -> addQueryParameter("sort", "4")
				SortOrder.ALPHABETICAL -> addQueryParameter("sort", "1")
				else -> {}
			}
			addQueryParameter("page", "$page")
		}.build()

		val rawJson = webClient.httpGet(url, apiHeaders).decodeAsJson()

		val entries = runCatching { JSONObject(rawJson).getJSONArray("entries") }
			.getOrElse { return emptyList() }

		return entries.mapJSON { entry ->
			val id = entry.getInt("id")
			val key = entry.getString("key")
			val entryTags = entry.getJSONArray("tags").mapJSON {
				Tag(
					it.getString("name"),
					it.getIntOrDefault("namespace", -1)
				)
			}
			val coverFile = entry.getJSONObject("cover").getString("n")

			Manga(
				id = generateUid("$id/$key"),
				title = entry.getString("title"),
				altTitle = null,
				url = "$id/$key",
				publicUrl = "/g/$id/$key".toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = "$cdnUrl/$id/$key/m/$coverFile",
				largeCoverUrl = "$cdnUrl/$id/$key/b/$coverFile",
				tags = entryTags.mapToSet {
					when (it.namespace) {
						1 -> MangaTag("Artist: ${it.name}", it.name, source)
						2 -> MangaTag("Circle: ${it.name}", it.name, source)
						3 -> MangaTag("Parody: ${it.name}", it.name, source)
						4 -> MangaTag("Magazine: ${it.name}", it.name, source)
						else -> MangaTag(it.name, it.name, source)
					}
				},
				state = MangaState.FINISHED,
				author = entryTags.filter { it.namespace == 1 }.joinToString { it.name },
				source = source,
			)
		}
	}

	data class Tag(
		val name: String,
		val namespace: Int,
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val url = "https://$domain/api/v1/library/${manga.url}"
		val rawJson = webClient.httpGet(url, apiHeaders).decodeAsJson()
		val jsonData = JSONObject(rawJson)

		return manga.copy(
			altTitle = jsonData.getString("filename"),
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.id),
					name = manga.title,
					number = 1,
					url = manga.url,
					scanlator = null,
					uploadDate = jsonData.getLong("published_at") * 1000,
					branch = null,
					source = source,
				)
			)
		)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val artistQuery = seed.author?.split(",")
			?.map(String::trim)
			?.filterNot(String::isEmpty)
			?.joinToString(" ") {
				"artist:\"^$it$\""
			}.orEmpty().also {
				if (it.isEmpty()) return emptyList()
			}

		return getList(1, artistQuery)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = "https://$domain/api/v1/library/${chapter.url}"
		val rawJson = webClient.httpGet(url, apiHeaders).decodeAsJson()
		val jsonData = JSONObject(rawJson)

		val id = jsonData.getInt("id")
		val key = jsonData.getString("key")
		val hash = jsonData.getString("hash")

		return jsonData.getJSONArray("data").mapJSON { pageData ->
			val fileName = pageData.getString("n")
			MangaPage(
				id = generateUid(fileName),
				url = "$cdnUrl/$id/$key/$hash/b/$fileName",
				preview = "$cdnUrl/$id/$key/s/$fileName",
				source = source
			)
		}
	}

	companion object {
		private const val cdnUrl = "https://kisakisexo.xyz"
	}
}
