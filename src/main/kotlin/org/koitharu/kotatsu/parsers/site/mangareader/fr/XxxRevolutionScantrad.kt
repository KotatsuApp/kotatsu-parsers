package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("XXXREVOLUTIONSCANTRAD", "Xxx.RevolutionScantrad", "fr", ContentType.HENTAI)
internal class XxxRevolutionScantrad(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.XXXREVOLUTIONSCANTRAD,
		"xxx.revolutionscantrad.com",
		pageSize = 100,
		searchPageSize = 10,
	) {
	override val listUrl = "/series.html"
	override val datePattern = "yyyy"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isSearchSupported = false,
			isTagsExclusionSupported = false,
		)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (page > 1) {
			return emptyList()
		}
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)

			append("?order=")
			append(
				when (order) {
					SortOrder.ALPHABETICAL -> "title"
					SortOrder.ALPHABETICAL_DESC -> "titlereverse"
					SortOrder.NEWEST -> "latest"
					SortOrder.POPULARITY -> "popular"
					SortOrder.UPDATED -> "update"
					else -> ""
				},
			)

			filter.tags.forEach {
				append("&")
				append("genre[]".urlEncoded())
				append("=")
				append(it.key)
			}

			filter.tagsExclude.forEach {
				append("&")
				append("genre[]".urlEncoded())
				append("=-")
				append(it.key)
			}

			if (filter.states.isNotEmpty()) {
				filter.states.oneOrThrowIfMany()?.let {
					append("&status=")
					when (it) {
						MangaState.ONGOING -> append("ongoing")
						MangaState.FINISHED -> append("completed")
						MangaState.PAUSED -> append("hiatus")
						else -> append("")
					}
				}
			}

			append("&page=")
			append(page.toString())
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override val selectPage = "div#readerarea img.chapter-image"

	override fun parseMangaList(docs: Document): List<Manga> {
		return docs.select(selectMangaList).mapNotNull {
			val a = it.selectFirst("a") ?: return@mapNotNull null
			val relativeUrl = "/" + a.attrAsRelativeUrl("href")
			val rating = it.selectFirst(".numscore")?.text()
				?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN
			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				title = it.selectFirst(selectMangaListTitle)?.text() ?: a.attr("title"),
				altTitle = null,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = rating,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = it.selectFirst(selectMangaListImg)?.src(),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val urlStart = manga.url.substringBeforeLast('/')
		val chapters = docs.select(selectChapter).mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".chapternum")?.text() ?: "Chapter ${index + 1}",
				url = "$urlStart/$url",
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = dateFormat.tryParse(element.selectFirst(".chapterdate")?.text()),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}


}
