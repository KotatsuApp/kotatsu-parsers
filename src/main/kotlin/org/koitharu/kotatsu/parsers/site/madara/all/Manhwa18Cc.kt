package org.koitharu.kotatsu.parsers.site.madara.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.HashSet
import java.util.Locale

@MangaSourceParser("MANHWA18CC", "Manhwa 18 .Cc", "", ContentType.HENTAI)
internal class Manhwa18Cc(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWA18CC, "manhwa18.cc", 24) {
	override val datePattern = "dd MMM yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "webtoons/"
	override val tagPrefix = "webtoon-genre/"
	override val withoutAjax = true
	override val selectTestAsync = "ul.row-content-chapter"
	override val selectDate = "span.chapter-time"
	override val selectChapter = "li.a-h"
	override val selectBodyPage = "div.read-content"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()

		val url = buildString {
			append("https://")
			append(domain)
			val pages = page + 1
			when {
				!query.isNullOrEmpty() -> {
					append("/page/")
					append(pages.toString())
					append("/?s=")
					append(query.urlEncoded())
					append("&post_type=wp-manga&")
				}

				!tags.isNullOrEmpty() -> {
					append("/$tagPrefix")
					append(tag?.key.orEmpty())
					if (pages > 1) {
						append("/page/")
						append(pages.toString())
					}
					append("?")
				}

				else -> {

					append("/$listUrl")
					if (pages > 1) {
						append("page/")
						append(pages)
					}
					append("?")
				}
			}
			append("m_orderby=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("trending")
				SortOrder.UPDATED -> append("latest")
				SortOrder.ALPHABETICAL -> append("alphabet")
				SortOrder.RATING -> append("rating")
				else -> append("latest")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.manga-lists div.manga-item").map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst(".item-rate span")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		val list = doc.body().selectFirstOrThrow("div.sub-menu").select("ul li").orEmpty()
		val keySet = HashSet<String>(list.size)
		return list.mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix("/").substringAfterLast(tagPrefix, "")
			if (href.isEmpty() || !keySet.add(href)) {
				return@mapNotNullToSet null
			}
			MangaTag(
				key = href,
				title = a.ownText().trim().ifEmpty {
					a.selectFirst(".menu-image-title")?.text()?.trim() ?: return@mapNotNullToSet null
				}.toTitleCase(),
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirstOrThrow(selectBodyPage)
		return root.select("img").map { img ->
			val url = img.src() ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

}
