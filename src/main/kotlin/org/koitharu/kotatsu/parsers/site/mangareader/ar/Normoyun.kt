package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("NORMOYUN", "MaxLevelTeam", "ar")
internal class Normoyun(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.NORMOYUN, "swatscans.com", pageSize = 42, searchPageSize = 39) {

	override val datePattern = "MMMM dd, yyyy"
	override val selectMangaList = ".listupd .bs .bsx"
	override val selectMangaListImg = "img"
	override val isNetShieldProtected = true
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			when {
				!filter.query.isNullOrEmpty() -> {
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append(listUrl)

					append("/?order=")
					append(
						when (order) {
							SortOrder.ALPHABETICAL -> "a-z"
							SortOrder.ALPHABETICAL_DESC -> "z-a"
							SortOrder.NEWEST -> "added"
							SortOrder.POPULARITY -> "popular"
							SortOrder.UPDATED -> "update"
							else -> ""
						},
					)

					val tagKey = "genre[]".urlEncoded()
					val tagQuery =
						if (filter.tags.isEmpty()) ""
						else filter.tags.joinToString(separator = "&", prefix = "&") { "$tagKey=${it.key}" }
					append(tagQuery)

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
				}
			}
			append("&page=")
			append(page.toString())
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}


	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select("div.bixbox li").mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst("a")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = dateFormat.tryParse(element.selectFirst(".chapter-date")?.text()),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val states = docs.selectFirst("div.spe span:contains(Ongoing)")?.text()
		val mangaState = if (states.isNullOrEmpty()) {
			MangaState.FINISHED
		} else {
			MangaState.ONGOING
		}
		val author = docs.selectFirst("span.author i")?.text()

		val nsfw = docs.selectFirst(".restrictcontainer") != null
			|| docs.selectFirst(".info-right .alr") != null
			|| docs.selectFirst(".postbody .alr") != null

		return manga.copy(
			description = docs.selectFirst("span.desc")?.html(),
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw || nsfw,
			tags = emptySet(),
			chapters = chapters,
		)
	}
}
