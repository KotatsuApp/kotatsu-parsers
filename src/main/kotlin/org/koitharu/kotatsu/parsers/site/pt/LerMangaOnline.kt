package org.koitharu.kotatsu.parsers.site.pt

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("LERMANGAONLINE", "Ler Manga Online", "pt")
class LerMangaOnline(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.LERMANGAONLINE, 20) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("lermangaonline.com.br")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			throw IllegalArgumentException("Search is not supported by this source")
		}
		val tag = tags.oneOrThrowIfMany()
		val url = buildString {
			append("https://")
			append(domain)
			append("/")
			if (!tags.isNullOrEmpty()) {
				append(tag?.key.orEmpty())
				append("/")
			}
			if (page > 1) {
				append("page/")
				append(page)
				append("/")
			}
		}
		return parseManga(webClient.httpGet(url).parseHtml())
	}

	private fun parseManga(docs: Document): List<Manga> {
		return docs.select(".materias .article").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				title = div.selectLastOrThrow("section h3").text(),
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

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml().requireElementById("sub-menu")
		return doc.select("ul.container li a").mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").removePrefix("/"),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT)
		return manga.copy(
			description = doc.selectFirst(".sinopse")?.html(),
			tags = doc.selectFirst(".categorias-blog")?.select("a")?.mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removePrefix("/"),
					title = a.text().ifEmpty { return@mapNotNullToSet null }.toTitleCase(),
					source = source,
				)
			}.orEmpty(),
			chapters = doc.select(".capitulos a").mapChapters(reversed = true) { i, a ->
				val href = a.attrAsRelativeUrl("href")
				val title = a.selectFirstOrThrow(".capitulo").html().substringBeforeLast("<span")
				val dateText = a.selectFirstOrThrow("span").text()
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

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return parseManga(webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml())
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".images img").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
