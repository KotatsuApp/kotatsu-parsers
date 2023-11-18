package org.koitharu.kotatsu.parsers.site.nepnep

import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import java.text.SimpleDateFormat
import java.util.*

internal abstract class NepnepParser(
	context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
) : MangaParser(context, source) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getList(offset: Int, query: String?, tags: Set<MangaTag>?, sortOrder: SortOrder): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}
		val doc = webClient.httpGet("https://$domain/search/").parseHtml()
		val json = JSONArray(
			doc.selectFirstOrThrow("script:containsData(MainFunction)").data()
				.substringAfter("vm.Directory = ")
				.substringBefore("vm.GetIntValue")
				.trim()
				.replace(';', ' '),
		)


		val manga = ArrayList<Manga>(json.length())

		for (i in 0 until json.length()) {
			val m = json.getJSONObject(i)
			val href = "/manga/" + m.getString("i")
			val imgUrl = "https://temp.compsci88.com/cover/" + m.getString("i") + ".jpg"
			when {
				!query.isNullOrEmpty() -> {
					if (m.getString("s").contains(query, ignoreCase = true) || m.getString("al")
							.contains(query, ignoreCase = true)
					) {
						manga.add(
							addManga(href, imgUrl, m),
						)
					}
				}

				!tags.isNullOrEmpty() -> {
					val a = m.getJSONArray("g").toString()
					var found = true
					tags.forEach {
						if (!a.contains(it.key, ignoreCase = true)) {
							found = false
						}
					}
					if (found) {
						manga.add(
							addManga(href, imgUrl, m),
						)
					}
				}

				else -> {
					manga.add(
						addManga(href, imgUrl, m),
					)
				}
			}


		}
		return manga
	}

	private fun addManga(href: String, imgUrl: String, m: JSONObject): Manga {
		return Manga(
			id = generateUid(href),
			title = m.getString("i").replace('-', ' '),
			altTitle = null,
			url = href,
			publicUrl = href.toAbsoluteUrl(domain),
			rating = RATING_UNKNOWN,
			isNsfw = false,
			coverUrl = imgUrl,
			tags = emptySet(),
			state = null,
			author = null,
			source = source,
		)
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/search/").parseHtml()
		val tags = doc.selectFirstOrThrow("script:containsData(vm.AvailableFilters)").data()
			.substringAfter("\"Genre\"")
			.substringAfter('[')
			.substringBefore(']')
			.replace("'", "")
			.split(',')

		return tags.mapToSet { tag ->
			MangaTag(
				key = tag,
				title = tag,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		val chapter = JSONArray(
			JSONArray(
				doc.selectFirstOrThrow("script:containsData(MainFunction)").data()
					.substringAfter("vm.Chapters = ")
					.substringBefore(';'),
			).toJSONList().reversed(),
		)

		val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:SS", sourceLocale)

		return manga.copy(
			altTitle = null,
			state = when (doc.selectFirstOrThrow(".list-group-item:contains(Status:) a").text()) {
				"Ongoing (Scan)", "Ongoing (Publish)",
				-> MangaState.ONGOING

				"Complete (Scan)", "Complete (Publish)",
				-> MangaState.FINISHED

				"Cancelled (Scan)", "Cancelled (Publish)",
				"Discontinued (Scan)", "Discontinued (Publish)",
				-> MangaState.ABANDONED

				"Hiatus (Scan)", "Hiatus (Publish)",
				-> MangaState.PAUSED

				else -> null
			},
			tags = doc.select(".list-group-item:contains(Genre(s):) a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text(),
					source = source,
				)
			},
			author = doc.select(".list-group-item:contains(Author(s):) a").text(),
			description = doc.selectFirstOrThrow(".top-5.Content").text(),

			chapters = chapter.mapJSONIndexed { i, j ->
				val indexChapter = j.getString("Chapter")!!
				val url = "/read-online/" + manga.url.substringAfter("/manga/") + chapterURLEncode(indexChapter)
				val name = j.getStringOrNull("ChapterName").let {
					if (it.isNullOrEmpty() || it == "null") "${j.getString("Type")} ${
						chapterImage(
							indexChapter,
							true,
						)
					}" else it
				}
				val date = j.getStringOrNull("Date")
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

	private fun chapterURLEncode(e: String): String {
		var index = ""
		val t = e.substring(0, 1).toInt()
		if (1 != t) {
			index = "-index-$t"
		}
		val ei = e.toInt()
		val dgt = when {
			ei < 100100 -> 4
			ei < 101000 -> 3
			ei < 110000 -> 2
			else -> 1
		}
		val n = e.substring(dgt, e.length - 1)
		var suffix = ""
		val path = e.substring(e.length - 1).toInt()
		if (0 != path) {
			suffix = ".$path"
		}
		return "-chapter-$n$suffix$index.html"
	}

	private val chapterImageRegex = Regex("""^0+""")

	private fun chapterImage(e: String, cleanString: Boolean = false): String {
		// cleanString will result in an empty string if chapter number is 0, hence the else if below
		val a = e.substring(1, e.length - 1).let { if (cleanString) it.replace(chapterImageRegex, "") else it }
		// If b is not zero, indicates chapter has decimal numbering
		val b = e.substring(e.length - 1).toInt()
		return when {
			b == 0 && a.isNotEmpty() -> a
			b == 0 && a.isEmpty() -> "0"
			else -> "$a.$b"
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val script = doc.selectFirstOrThrow("script:containsData(MainFunction)").data()
		val curChapter = JSONObject(
			doc.selectFirstOrThrow("script:containsData(MainFunction)").data()
				.substringAfter("vm.CurChapter = ")
				.substringBefore(';'),
		)
		val pageTotal = curChapter.getString("Page")!!.toInt()
		val host = "https://" + script
			.substringAfter("vm.CurPathName = \"", "")
			.substringBefore('"')
		check(host.isNotEmpty()) {
			"Manga4Life is overloaded and blocking Kotatsu right now. Wait for unblock."
		}
		val titleURI = script.substringAfter("vm.IndexName = \"").substringBefore("\"")
		val seasonURI = curChapter.getString("Directory")!!.let { if (it.isEmpty()) "" else "$it/" }
		val path = "$host/manga/$titleURI/$seasonURI"
		val chNum = chapterImage(curChapter.getString("Chapter")!!)

		return IntRange(1, pageTotal).mapIndexed { i, _ ->
			val imageNum = (i + 1).toString().let { "000$it" }.let { it.substring(it.length - 3) }
			val url = "$path$chNum-$imageNum.png"
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

}
