package org.koitharu.kotatsu.parsers.site.fmreader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.fmreader.FmreaderParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("OLIMPOSCANS", "OlimpoScans", "es")
internal class OlimpoScans(context: MangaLoaderContext) :
	FmreaderParser(context, MangaParserSource.OLIMPOSCANS, "leerolimpo.com") {

	override val selectState = "ul.manga-info li:contains(Estado) a"
	override val selectAlt = "ul.manga-info li:contains(Otros nombres)"
	override val selectTag = "ul.manga-info li:contains(Género) a"
	override val tagPrefix = "lista-de-comics-genero-"
	override val isMultipleTagsSupported = false
	override val isTagsExclusionSupported = false

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append(listUrl)
					append("?page=")
					append(page.toString())
					append("&name=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/lista-de-comics-genero-")
							append(it.key)
							append(".html")
						}
					} else {
						append(listUrl)
						append("?page=")
						append(page.toString())
						append("&sort=")
						when (filter.sortOrder) {
							SortOrder.POPULARITY -> append("views")
							SortOrder.UPDATED -> append("last_update")
							SortOrder.ALPHABETICAL -> append("name&sort_type=ASC")
							SortOrder.ALPHABETICAL_DESC -> append("name&sort_type=DESC")
							else -> append("last_update")
						}
					}

					append("&m_status=")
					filter.states.oneOrThrowIfMany()?.let {
						append(
							when (it) {
								MangaState.ONGOING -> "2"
								MangaState.FINISHED -> "1"
								MangaState.ABANDONED -> "3"
								else -> ""
							},
						)
					}
				}

				null -> {
					append(listUrl)
					append("?page=")
					append(page.toString())
					append("&sort=last_update")
				}
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
