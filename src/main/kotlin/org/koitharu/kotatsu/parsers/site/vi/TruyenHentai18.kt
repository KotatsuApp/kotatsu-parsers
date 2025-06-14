package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRUYENHENTAI18", "TruyenHentai18", "vi", ContentType.HENTAI)
internal class TruyenHentai18(context: MangaLoaderContext):
      LegacyPagedMangaParser(context, MangaParserSource.TRUYENHENTAI18, 18) {

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
					append(apiSuffix + "/posts")
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

		val fullUrl = "https://" + url
		return when {
			filter.tags.isNotEmpty() -> parseNextList(webClient.httpGet(fullUrl).parseHtml())
			else -> {
				val doc = webClient.httpGet(fullUrl).parseJson()
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
				description = mangaItem.optString("content", ""),
			)
		}
	}

	private fun parseNextList(doc: Document): List<Manga> { // need to clean code
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
			.replace("\\n", "")
			.replace("\\t", "")
			.replace("\\r", "")
			
		val responseStart = cleanedScript.indexOf("{\"response\":")
		if (responseStart == -1) throw Exception("Không tìm thấy object 'response' trong script")
		
		var bracketCount = 0
		var i = responseStart
		var jsonStr = ""
		
		while (i < cleanedScript.length) {
			val c = cleanedScript[i]
			when (c) {
				'{' -> bracketCount++
				'}' -> bracketCount--
			}
			jsonStr += c
			if (bracketCount == 0 && jsonStr.isNotEmpty()) break
			i++
		}

		val responseObj = org.json.JSONObject(jsonStr)
		val dataArray = responseObj.getJSONObject("response").optJSONArray("data")
			?: throw Exception("Không tìm thấy trường 'data' trong object 'response'")

		return (0 until dataArray.length()).map { idx ->
			val item = dataArray.getJSONObject(idx)
			val genres = item.optJSONArray("genres")?.let { genresArray ->
				(0 until genresArray.length()).mapNotNull { gIdx ->
					val genreItem = genresArray.optJSONObject(gIdx) ?: return@mapNotNull null
					MangaTag(
						key = genreItem.optString("slug"),
						title = genreItem.optString("name"),
						source = source
					)
				}.toSet()
			} ?: emptySet()

			val authors = item.optJSONArray("authors")?.let { authorsArray ->
				(0 until authorsArray.length()).mapNotNull { aIdx ->
					authorsArray.optJSONObject(aIdx)?.optString("name")
				}.toSet()
			} ?: emptySet()

			Manga(
				id = item.getLong("id"),
				title = item.getString("title"),
				altTitles = setOfNotNull(
					item.optString("official_name").takeIf { it.isNotBlank() }
				),
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
				description = item.optString("content", "")
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val rating = doc.selectFirst("div.kksr-stars")?.attr("data-rating")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN
        val description = doc.selectFirst("div.mt-3.desc-text")?.text()
		
		val author = doc.select("div.attr-item").firstOrNull { 
			it.selectFirst("b")?.text() == "Tác giả:" 
		}?.selectFirst("a")?.text()

		val tags = doc.select("ul.post-categories li a").mapNotNull { element ->
			val name = element.text()
			val key = element.attr("href").substringAfter("/category/")
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}.toSet()

		val chapters = doc.select("div.p-2.d-flex.flex-column.flex-md-row.item").reversed()
			.mapChapters(reversed = false) { i, e ->
				val name = e.selectFirst("b")?.text() ?: ""
				val href = e.selectFirst("a")?.attrAsRelativeUrl("href") ?: ""
				val dateText = e.selectFirst("i.ps-3")?.text()
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

		return manga.copy(
			rating = rating,
			authors = setOfNotNull(author),
			description = description,
			chapters = chapters,
			tags = tags,
			contentRating = ContentRating.ADULT,
		)
	}

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div#viewer p img").mapNotNull { img -> // Need debug
			val url = img.attr("src") ?: return@mapNotNull null
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateText: String?): Long {
		if (dateText == null) return 0

		val relativeTimePattern = Regex("(\\d+)\\s*(ngày|tuần|tháng|năm) trước")
		val absoluteTimePattern = Regex("(\\d{2}-\\d{2}-\\d{4})")

		return when {
			dateText.contains("ngày trước") -> {
				val match = relativeTimePattern.find(dateText)
				val days = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - days * 86400 * 1000
			}

			dateText.contains("tuần trước") -> {
				val match = relativeTimePattern.find(dateText)
				val weeks = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - weeks * 7 * 86400 * 1000
			}

			dateText.contains("tháng trước") -> {
				val match = relativeTimePattern.find(dateText)
				val months = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - months * 30 * 86400 * 1000
			}

			dateText.contains("năm trước") -> {
				val match = relativeTimePattern.find(dateText)
				val years = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - years * 365 * 86400 * 1000
			}

			absoluteTimePattern.matches(dateText) -> {
				val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
				formatter.tryParse(dateText)
			}

			else -> 0L
		}
	}
}
