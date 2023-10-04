package org.koitharu.kotatsu.parsers.site.fmreader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.fmreader.FmreaderParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("OLIMPOSCANS", "Olimpo Scans", "es")
internal class OlimpoScans(context: MangaLoaderContext) :
	FmreaderParser(context, MangaSource.OLIMPOSCANS, "olimposcans.com") {

	override val selectState = "ul.manga-info li:contains(Estado) a"
	override val selectAlt = "ul.manga-info li:contains(Otros nombres)"
	override val selectTag = "ul.manga-info li:contains(Género) a"
	override val tagPrefix = "lista-de-comics-genero-"

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
			append(listeurl)
			append("?page=")
			append(page.toString())
			when {
				!query.isNullOrEmpty() -> {
					append("&name=")
					append(query.urlEncoded())
				}

				!tags.isNullOrEmpty() -> {
					append("&genre=")
					append(tag?.key.orEmpty())
				}
			}
			append("&sort=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("views")
				SortOrder.UPDATED -> append("last_update")
				SortOrder.ALPHABETICAL -> append("name")
				else -> append("last_update")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		val lastPage =
			doc.selectLast(".pagination a")?.attr("href")?.substringAfterLast("page=")?.substringBeforeLast("&artist")
				?.toInt() ?: 1
		if (lastPage < page) {
			return emptyList()
		}
		return doc.select("div.thumb-item-flow").map { div ->
			val href = "/" + div.selectFirstOrThrow("div.series-title a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirstOrThrow("div.img-in-ratio").attr("data-bg").toAbsoluteUrl(domain),
				title = div.selectFirstOrThrow("div.series-title").text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = ("/" + chapter.url).toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { img ->
			val url = ("/proxy.php?link=" + img.src()).toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
