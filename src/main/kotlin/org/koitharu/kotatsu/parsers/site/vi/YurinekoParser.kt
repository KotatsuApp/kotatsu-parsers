package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("YURINEKO", "YuriNeko", "vi", ContentType.HENTAI)
internal class YurinekoParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.YURINEKO, 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("yurineko.moe")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED)

	private val apiDomain
		get() = "api.$domain"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val listUrl = when {
			!filter.query.isNullOrEmpty() -> {
				"/search?query=${filter.query.urlEncoded()}&page=$page"
			}

			else -> {
				if (filter.tags.isNotEmpty()) {
					val tagKeys = filter.tags.joinToString(separator = ",") { it.key }
					"/advancedSearch?genre=$tagKeys&notGenre=&sort=7&minChapter=1&status=0&page=$page"
				} else {
					"/lastest2?page=$page"
				}
			}
		}
		val jsonResponse = webClient.httpGet(listUrl.toAbsoluteUrl(apiDomain)).parseJson()
		return jsonResponse.getJSONArray("result")
			.mapJSON { jo ->
				val id = jo.getLong("id")
				val relativeUrl = "/manga/$id"
				Manga(
					id = generateUid(id),
					title = jo.getString("originalName"),
					altTitle = jo.getStringOrNull("otherName"),
					url = relativeUrl,
					publicUrl = relativeUrl.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = jo.getString("thumbnail"),
					tags = jo.getJSONArray("tag").mapJSONToSet { tag ->
						MangaTag(
							title = tag.getString("name"),
							key = tag.getInt("id").toString(),
							source = source,
						)
					},
					state = when (jo.getInt("status")) {
						2 -> MangaState.FINISHED
						1, 3, 4 -> MangaState.ONGOING
						5, 6, 7 -> MangaState.ABANDONED
						else -> null
					},
					author = jo.getJSONArray("author")
						.mapJSON { author -> author.getString("name") }
						.joinToString { it },
					description = jo.getStringOrNull("description"),
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val response = webClient.httpGet(manga.url.toAbsoluteUrl(apiDomain)).parseJson()
		val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
		return manga.copy(
			chapters = response.getJSONArray("chapters")
				.asTypedList<JSONObject>()
				.mapChapters(true) { i, jo ->
					val mangaId = jo.getInt("mangaID")
					val chapterId = jo.getInt("id")
					MangaChapter(
						id = generateUid(chapterId.toLong()),
						name = jo.getString("name"),
						number = i + 1f,
						volume = 0,
						scanlator = null,
						url = "/read/$mangaId/$chapterId",
						uploadDate = df.tryParse(jo.getString("date")),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val jsonData = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
			.requireElementById("__NEXT_DATA__")
			.data()
		return JSONObject(jsonData).getJSONObject("props")
			.getJSONObject("pageProps")
			.getJSONObject("chapterData")
			.getJSONArray("url")
			.asTypedList<String>()
			.map { url ->
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		return webClient.httpGet("https://$apiDomain/tag/find?query=")
			.parseJsonArray()
			.mapJSONToSet { jo ->
				MangaTag(
					key = jo.getInt("id").toString(),
					title = jo.getString("name"),
					source = source,
				)
			}
	}
}
