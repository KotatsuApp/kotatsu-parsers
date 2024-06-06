package org.koitharu.kotatsu.parsers.site.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MUITOHENTAI", "MuitoHentai", "pt", ContentType.HENTAI)
class MuitoHentai(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.MUITOHENTAI, 24) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY)

	override val configKeyDomain = ConfigKey.Domain("www.muitohentai.com")

	override val isMultipleTagsSupported = false

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					if (page > 1) return emptyList()
					append("/buscar-manga/?q=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {
					append("/mangas")

					filter.tags.oneOrThrowIfMany()?.let {
						append("/genero/")
						append(it.key)
					}

					append('/')
					append(page.toString())
					append('/')
				}

				null -> {
					append("/mangas/")
					append(page.toString())
					append('/')
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.requireElementById("archive-content").select("article").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsAbsoluteUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				title = div.selectLastOrThrow("h3").text(),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = null,
				state = null,
				author = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/generos-dos-mangas/").parseHtml()
		return doc.select("div.content a.profileSideBar").mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix("/").substringAfterLast("/"),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			description = doc.selectFirstOrThrow(".backgroundpost:contains(Sinopse)").html(),
			tags = doc.select("a.genero_btn").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast("/"),
					title = a.text(),
					source = source,
				)
			},
			chapters = doc.select(".backgroundpost h3 a").mapChapters() { i, a ->
				val href = a.attrAsAbsoluteUrl("href")
				MangaChapter(
					id = generateUid(href),
					name = a.text(),
					number = i + 1,
					url = href,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val data = doc.selectFirstOrThrow("script:containsData(var arr = [)").data()
		val images = data.substringAfter("[").substringBefore("];").replace("\"", "").split(",")
		return images.map { img ->
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}
}
