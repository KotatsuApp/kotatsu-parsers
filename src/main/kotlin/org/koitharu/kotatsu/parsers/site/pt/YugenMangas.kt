package org.koitharu.kotatsu.parsers.site.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("YUGENMANGAS", "Yugen Mangas", "pt")
class YugenMangas(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.YUGENMANGAS, 28) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL, SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("yugenmangas.org")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			if (page > 1) {
				return emptyList()
			}
			val url = buildString {
				append("https://")
				append(domain)
				append("/api/series/list/?query=")
				append(query.urlEncoded())
			}
			val json = webClient.httpGet(url).parseJsonArray()
			return json.mapJSON { j ->
				val urlManga = "https://$domain/series/${j.getString("slug")}/"
				Manga(
					id = generateUid(urlManga),
					url = urlManga,
					publicUrl = urlManga,
					title = j.getString("name"),
					coverUrl = "",
					altTitle = null,
					rating = RATING_UNKNOWN,
					tags = emptySet(),
					description = null,
					state = null,
					author = null,
					isNsfw = false,
					source = source,
				)
			}
		} else {
			val url = buildString {
				append("https://")
				append(domain)
				when (sortOrder) {
					SortOrder.ALPHABETICAL -> append("/series")
					SortOrder.UPDATED -> append("/updates")
					else -> append("/updates")
				}
				if (page > 1) {
					append("?page=")
					append(page)
				}
			}
			val doc = webClient.httpGet(url).parseHtml()
			return doc.select(".gallery .mangas-gallery, .container-update-series .card-series-updates").map { div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				Manga(
					id = generateUid(href),
					url = href,
					publicUrl = a.attrAsAbsoluteUrl("href"),
					title = div.selectLastOrThrow(".title-serie, .name-manga").text(),
					coverUrl = div.selectFirst("img")?.src().orEmpty(),
					altTitle = null,
					rating = RATING_UNKNOWN,
					tags = emptySet(),
					description = null,
					state = null,
					author = null,
					isNsfw = false,
					source = source,
				)
			}
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
		val chapters = doc.requireElementById("listadecapitulos")
		return manga.copy(
			description = doc.selectFirst(".sinopse .sinopse")?.html(),
			author = doc.selectFirst(".author")?.text(),
			coverUrl = doc.selectFirst(".side img")?.src().orEmpty(),
			state = doc.selectFirst(".lancamento p")?.let {
				when (it.text().lowercase()) {
					"ongoing" -> MangaState.ONGOING
					"completed", "finished" -> MangaState.FINISHED
					else -> null
				}
			},
			chapters = chapters.select(".chapter").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val title = div.selectFirstOrThrow(".chapter-title").text()
				val dateText = div.selectFirstOrThrow(".chapter-lancado").text()
				MangaChapter(
					id = generateUid(href),
					name = title,
					number = i + 1,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(dateText),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val urlApi = fullUrl.replace("/series/", "/api/serie/").replace("/capitulo", "/chapter/capitulo") + "images/"
		val json = webClient.httpGet(urlApi).parseJson().getJSONArray("chapter_images")
		val pages = ArrayList<MangaPage>(json.length())
		for (i in 0 until json.length()) {
			val img = "https://media.$domain/${json.getString(i)}"
			pages.add(
				MangaPage(
					id = generateUid(img),
					url = img,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}

	override suspend fun getTags(): Set<MangaTag> = emptySet()
}
