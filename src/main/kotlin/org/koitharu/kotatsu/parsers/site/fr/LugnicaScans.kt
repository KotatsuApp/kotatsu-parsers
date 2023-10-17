package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("LUGNICASCANS", "LugnicaScans", "fr")
internal class LugnicaScans(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.LUGNICASCANS, 10) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
	)

	override val configKeyDomain = ConfigKey.Domain("lugnica-scans.com")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	init {
		context.cookieJar.insertCookies(
			domain,
			"reader_render=continue;",
		)
	}

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon("https://$domain/favicon/favicon-32x32.png", 32, null),
			),
			domain,
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			throw IllegalArgumentException("Search is not supported by this source")
		}
		if (sortOrder == SortOrder.ALPHABETICAL) {
			if (page > 1) {
				return emptyList()
			}
			val url = buildString {
				append("https://")
				append(domain)
				append("/api/get/catalog?page=0&filter=all")
			}
			val json = webClient.httpGet(url).parseJsonArray()
			return json.mapJSON { j ->
				val urlManga = "https://$domain/api/get/card/${j.getString("slug")}"
				val img = "https://$domain/upload/min_cover/${j.getString("image")}"
				Manga(
					id = generateUid(urlManga),
					title = j.getString("title"),
					altTitle = null,
					url = urlManga,
					publicUrl = urlManga.toAbsoluteUrl(domain),
					rating = j.getString("rate").toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = img,
					tags = setOf(),
					state = when (j.getString("status")) {
						"0" -> MangaState.ONGOING
						"1" -> MangaState.FINISHED
						"3" -> MangaState.ABANDONED
						else -> null
					},
					author = null,
					source = source,
				)
			}
		} else {
			val url = buildString {
				append("https://")
				append(domain)
				append("/api/get/homegrid/")
				append(page)
			}
			val json = webClient.httpGet(url).parseJsonArray()
			return json.mapJSON { j ->
				val urlManga = "https://$domain/api/get/card/${j.getString("manga_slug")}"
				val img = "https://$domain/upload/min_cover/${j.getString("manga_image")}"
				Manga(
					id = generateUid(urlManga),
					title = j.getString("manga_title"),
					altTitle = null,
					url = urlManga,
					publicUrl = urlManga.toAbsoluteUrl(domain),
					rating = j.getString("manga_rate").toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = img,
					tags = setOf(),
					state = null,
					author = null,
					source = source,
				)
			}

		}

	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseJson()
		val jsonManga = json.getJSONObject("manga")
		val chapters = json.getJSONObject("chapters").toString().split("{\"id\":").drop(1)
		val slug = manga.url.substringAfterLast("/")
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)
		return manga.copy(
			altTitle = null,
			state = when (jsonManga.getString("status")) {
				"0" -> MangaState.ONGOING
				"1" -> MangaState.FINISHED
				"3" -> MangaState.ABANDONED
				else -> null
			},
			author = jsonManga.getString("author"),
			description = jsonManga.getString("description"),
			chapters = chapters.mapChapters(reversed = true) { i, it ->
				val id = it.substringAfter("\"chapter\":").substringBefore(",")
				val url = "https://$domain/api/get/chapter/$slug/$id"
				val date = getDateString(
					it.substringAfter("\"date\":\"").substringBefore("\",").toLong(),
				)
				MangaChapter(
					id = generateUid(url),
					name = "Chapitre : $id",
					number = i,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(date),
					branch = null,
					source = source,
				)
			},
		)
	}

	private val simpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)
	private fun getDateString(time: Long): String = simpleDateFormat.format(time * 1000L)

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val jsonPage = webClient.httpGet(fullUrl).parseJson()
		val idManga = jsonPage.getJSONObject("manga").getInt("id")
		val slug = jsonPage.getJSONObject("chapter").getInt("chapter")
		val jsonPages = jsonPage.getJSONObject("chapter").getJSONArray("files")
		val pages = ArrayList<MangaPage>(jsonPages.length())
		for (i in 0 until jsonPages.length()) {
			val url = "https://$domain/upload/chapters/$idManga/$slug/${jsonPages.getString(i)}"
			pages.add(
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}

	override suspend fun getTags(): Set<MangaTag> = emptySet()

}
