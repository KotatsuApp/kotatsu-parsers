package org.koitharu.kotatsu.parsers.site.madara.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANHWA18CC", "Manhwa18.cc", "", ContentType.HENTAI)
internal class Manhwa18Cc(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHWA18CC, "manhwa18.cc", 24) {
	override val datePattern = "dd MMM yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "webtoons/"
	override val tagPrefix = "webtoon-genre/"
	override val withoutAjax = true
	override val selectTestAsync = "ul.row-content-chapter"
	override val selectDate = "span.chapter-time"
	override val selectChapter = "li.a-h"
	override val selectBodyPage = "div.read-content"

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override val availableContentRating: Set<ContentRating> = emptySet()

	override val availableStates: Set<MangaState> get() = emptySet()

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					append("/search?q=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				is MangaListFilter.Advanced -> {

					val tag = filter.tags.oneOrThrowIfMany()
					if (filter.tags.isNotEmpty()) {
						append("/$tagPrefix")
						append(tag?.key.orEmpty())
					} else {
						append("/$listUrl")
					}

					if (page > 1) {
						append(page.toString())
					}

					append("?orderby=")
					when (filter.sortOrder) {
						SortOrder.POPULARITY -> append("trending")
						SortOrder.UPDATED -> append("latest")
						SortOrder.ALPHABETICAL -> append("alphabet")
						SortOrder.RATING -> append("rating")
						else -> append("latest")
					}
				}

				null -> {
					append("?s&post_type=wp-manga&m_orderby=latest")
				}
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
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
