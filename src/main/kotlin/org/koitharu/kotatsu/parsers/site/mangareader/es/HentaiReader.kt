package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("HENTAIREADER", "HentaiReader", "es", ContentType.HENTAI)
internal class HentaiReader(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.HENTAIREADER, "hentaireader.org", pageSize = 25, searchPageSize = 25) {
	override val listUrl = "/tipo/all"
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
					append(listUrl)
					append("?s=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {
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
						append("tags[]".urlEncoded())
						append("=")
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
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override val selectChapter = ".releases"
	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select(selectChapter).mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst("h2")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = dateFormat.tryParse(docs.selectFirst("time")?.text()),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow(selectTestScript)
		val images = JSONObject(script.data().substringAfter('(').substringBeforeLast(')').replace(", ] }]", " ] }]"))
			.getJSONArray("sources")
			.getJSONObject(0)
			.getJSONArray("images")

		val pages = ArrayList<MangaPage>(images.length())
		for (i in 0 until images.length()) {
			pages.add(
				MangaPage(
					id = generateUid(images.getString(i)),
					url = images.getString(i),
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}

