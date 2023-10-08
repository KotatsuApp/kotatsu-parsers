package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("HENTAIFOX", "Hentai Fox", type = ContentType.HENTAI)
internal class HentaiFox(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.HENTAIFOX, 20) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)
	override val configKeyDomain = ConfigKey.Domain("hentaifox.com")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()

		val url = buildString {
			append("https://$domain")
			if (!tags.isNullOrEmpty()) {
				append("/tag/")
				append(tag?.key.orEmpty())
				if (page > 1) {
					append("/pag/")
					append(page)
					append("/")
				}
			} else if (!query.isNullOrEmpty()) {
				append("/search/?q=")
				append(query.urlEncoded())
				if (page > 1) {
					append("&page=")
					append(page)
				}
			} else {
				if (page > 2) {
					append("/pag/")
					append(page)
					append("/")
				} else if (page > 1) {
					append("/page/")
					append(page)
					append("/")
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".lc_galleries .thumb").map { div ->
			val href = div.selectFirstOrThrow(".inner_thumb a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select("h2.g_title").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = div.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	//Tags are deliberately reduced because there are too many and this slows down the application.
	//only the most popular ones are taken.
	override suspend fun getTags(): Set<MangaTag> {
		return coroutineScope {
			(1..3).map { page ->
				async { getTags(page) }
			}
		}.awaitAll().flattenTo(ArraySet(360))
	}

	private suspend fun getTags(page: Int): Set<MangaTag> {
		val url = "https://$domain/tags/popular/pag/$page/"
		val root = webClient.httpGet(url).parseHtml()
		return root.parseTags()
	}

	private fun Element.parseTags() = select(".list_tags a.tag_btn").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		MangaTag(
			key = key,
			title = it.selectFirstOrThrow("h3").text(),
			source = source,
		)
	}


	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val urlChapters = manga.url.replace("/gallery/", "/g/") + "1/"
		return manga.copy(
			altTitle = null,
			tags = doc.select("ul.tags a.tag_btn ").mapNotNullToSet {
				val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
				MangaTag(
					key = key,
					title = it.html().substringBefore("<span"),
					source = source,
				)
			},
			author = doc.selectFirst("ul.artists a.tag_btn")?.html()?.substringBefore("<span"),
			description = null,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1,
					url = urlChapters,
					scanlator = null,
					uploadDate = 0,
					branch = doc.selectFirstOrThrow("ul.languages a.tag_btn").html().substringBefore("<span"),
					source = source,
				),
			),
		)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val doc = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirstOrThrow(".related_galleries")
		return root.select("div.thumb").mapNotNull { div ->
			val a = div.selectFirst(".inner_thumb a") ?: return@mapNotNull null
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(a.host ?: domain),
				altTitle = null,
				title = div.selectFirstOrThrow("h2.g_title").text(),
				author = null,
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				tags = emptySet(),
				rating = RATING_UNKNOWN,
				state = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val totalPages = doc.selectFirstOrThrow(".total_pages").text().toInt()
		val rawUrl = chapter.url.replace("/1/", "/")
		return (1..totalPages).map {
			val url = "$rawUrl$it/"
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.requireElementById("gimg").attr("data-src") ?: doc.parseFailed("Page image not found")
	}
}
