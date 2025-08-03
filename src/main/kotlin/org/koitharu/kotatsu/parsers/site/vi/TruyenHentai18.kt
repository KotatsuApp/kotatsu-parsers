package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRUYENHENTAI18", "TruyenHentai18", "vi", ContentType.HENTAI)
internal class TruyenHentai18(context: MangaLoaderContext):
	PagedMangaParser(context, MangaParserSource.TRUYENHENTAI18, 18) {

	override val configKeyDomain = ConfigKey.Domain("truyenhentai18.app")

	private val apiSuffix = "api.th18.app"
	private val cdnSuffix = "vi-api.th18.app"

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = emptySet(), // cant find any URLs for fetch tags
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = when {
			filter.tags.isNotEmpty() -> {
				buildString {
					append(domain)
					append("/vi/the-loai/")
					append(filter.tags.first().key)
					append("/page/")
					append(page)
				}
			}

			else -> {
				buildString {
					append("$apiSuffix/posts")
					append("?language=vi")
					
					append("&order=")
					append(
						when (order) {
							SortOrder.UPDATED -> "latest"
							SortOrder.NEWEST -> "newest"
							SortOrder.NEWEST_ASC -> "oldest"
							else -> "latest" // default
						}
					)

					append("&limit=24")
					append("&page=")
					append(page)

					if (!filter.query.isNullOrEmpty()) {
						append("&query=${filter.query}")
					}
				}
			}
		}

		return when {
			filter.tags.isNotEmpty() -> parseNextList(webClient.httpGet("https://$url").parseHtml())
			else -> {
				val doc = webClient.httpGet("https://$url").parseJson()
				parseJSONList(doc)
			}
		}
	}

	private fun parseJSONList(json: JSONObject): List<Manga> {
		return json.getJSONArray("data").mapJSON { mangaItem ->
			Manga(
				id = mangaItem.getLong("id"),
				title = mangaItem.getString("title"),
				altTitles = setOfNotNull(
					mangaItem.optString("official_name").takeIf { !it.isNullOrBlank() }
				),
				url = mangaItem.getString("slug"),
				publicUrl = mangaItem.getString("slug").toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = "https://$cdnSuffix/uploads/${mangaItem.getString("thumbnail")}",
				tags = mangaItem.optJSONArray("genres")?.mapJSON { genreItem ->
					MangaTag(
						key = genreItem.getString("slug"),
						title = genreItem.getString("name"),
						source = source
					)
				}?.toSet() ?: emptySet(),
				state = when (mangaItem.optString("post_status")) {
					"completed" -> MangaState.FINISHED
					else -> MangaState.ONGOING
				},
				authors = mangaItem.optJSONArray("authors")?.mapJSON { authorItem ->
					authorItem.optString("name")
				}?.filterNotNull()?.toSet() ?: emptySet(),
				source = source,
				description = mangaItem.optString("content").orEmpty(),
			)
		}
	}

	private fun parseNextList(doc: Document): List<Manga> {
		val script = doc.select("script").firstOrNull { it.data().contains("response") }
			?: throw Exception("Không tìm thấy script chứa dữ liệu manga")

		val scriptContent = script.data()
		val cleanedScript = scriptContent
			.replace("self.__next_f.push([1,", "")
			.replace("\"5:", "")
			.replace("[[\"$\",\"script\",null,{\"type\":\"application/ld+json\",\"dangerouslySetInnerHTML\":{\"__html\":\"$1a\"}}],", "")
			.replace("[[\"$\",\"script\",null,{\"type\":\"application/ld+json\",\"dangerouslySetInnerHTML\":{\"__html\":", "")
			.replace("\\\\\",", ",")
			.replace("\\\"", "\"")
			.replace("\\\\", "\\")
			.replace("[\\n\\r\\t]", "")

		val responseStart = cleanedScript.indexOf("{\"response\":")
		if (responseStart == -1) throw Exception("Không tìm thấy object 'response' trong script")

		val jsonStr = extractJsonString(cleanedScript, responseStart)
		val responseObj = JSONObject(jsonStr)
		val dataArray = responseObj.getJSONObject("response").optJSONArray("data")
			?: throw Exception("Không tìm thấy trường 'data' trong object 'response'")

		return (0 until dataArray.length()).map { idx ->
			val item = dataArray.getJSONObject(idx)
			val genres = extractGenres(item)
			val authors = extractAuthors(item)

			Manga(
				id = item.getLong("id"),
				title = item.getString("title"),
				altTitles = setOfNotNull(item.optString("official_name").takeIf { it.isNotBlank() }),
				url = item.getString("slug"),
				publicUrl = item.getString("slug").toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = "https://$cdnSuffix/uploads/${item.getString("thumbnail")}",
				tags = genres,
				state = when (item.optString("post_status")) {
					"completed" -> MangaState.FINISHED
					else -> MangaState.ONGOING
				},
				authors = authors,
				source = source,
				description = item.optString("content").orEmpty()
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
        val fullUrl = "https://$domain/vi/" + manga.url + ".html"
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return manga.copy(
			chapters = doc.select("div.grid.grid-cols-1.md\\:grid-cols-2.gap-4 a.block")
				.mapChapters(reversed = false) { i, e ->
					val name = e.selectFirst("span.truncate")?.text() ?: e.attr("title") ?: ""
					val href = e.selectFirst("a")?.attrAsRelativeUrl("href") ?: ""
					val dateText = e.selectFirst("div.text-xs.text-gray-500")?.text()
					MangaChapter(
						id = generateUid(href),
						title = name,
						url = href,
						number = i + 1f,
						volume = 0,
						uploadDate = parseChapterDate(dateText),
						scanlator = null,
						branch = null,
						source = source,
					)
				}
			)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val scriptContent = doc.select("script")
			.firstOrNull { it.data().contains("img src") }
			?.data()
			?: return emptyList()

		val decoded = scriptContent
			.replace("\\u003c", "<")
			.replace("\\u003e", ">")
			.replace("\\\"", "\"")
			.replace("\\/", "/")

		val regex = Regex("""img\s+src=["'](https?://[^"']+)["']""")
		val imageUrls = regex.findAll(decoded).map { it.groupValues[1] }.toList()

		return imageUrls.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseChapterDate(date: String?): Long {
		if (date == null) return 0
		return when {
			date.contains("giây trước") -> System.currentTimeMillis() - date.removeSuffix(" giây trước").toLong() * 1000
			date.contains("phút trước") -> System.currentTimeMillis() - date.removeSuffix(" phút trước")
				.toLong() * 60 * 1000

			date.contains("giờ trước") -> System.currentTimeMillis() - date.removeSuffix(" giờ trước")
				.toLong() * 60 * 60 * 1000

			date.contains("ngày trước") -> System.currentTimeMillis() - date.removeSuffix(" ngày trước")
				.toLong() * 24 * 60 * 60 * 1000

			date.contains("tuần trước") -> System.currentTimeMillis() - date.removeSuffix(" tuần trước")
				.toLong() * 7 * 24 * 60 * 60 * 1000

			date.contains("tháng trước") -> System.currentTimeMillis() - date.removeSuffix(" tháng trước")
				.toLong() * 30 * 24 * 60 * 60 * 1000

			date.contains("năm trước") -> System.currentTimeMillis() - date.removeSuffix(" năm trước")
				.toLong() * 365 * 24 * 60 * 60 * 1000

			else -> SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date)?.time ?: 0L
		}
	}

	private fun extractJsonString(script: String, responseStart: Int): String {
		val stringBuilder = StringBuilder()
		var bracketCount = 0
		var i = responseStart

		while (i < script.length) {
			val c = script[i]
			stringBuilder.append(c)
			if (c == '{') bracketCount++
			if (c == '}') bracketCount--
			if (bracketCount == 0) break
			i++
		}
		return stringBuilder.toString()
	}

	private fun extractGenres(item: JSONObject): Set<MangaTag> {
		return item.optJSONArray("genres")?.let { genresArray ->
			(0 until genresArray.length()).mapNotNull { gIdx ->
				genresArray.optJSONObject(gIdx)?.let { genreItem ->
					MangaTag(
						key = genreItem.optString("slug"),
						title = genreItem.optString("name"),
						source = source
					)
				}
			}.toSet()
		} ?: emptySet()
	}

	private fun extractAuthors(item: JSONObject): Set<String> {
		return item.optJSONArray("authors")?.let { authorsArray ->
			(0 until authorsArray.length()).mapNotNull { aIdx ->
				authorsArray.optJSONObject(aIdx)?.optString("name")
			}.toSet()
		} ?: emptySet()
	}
}
