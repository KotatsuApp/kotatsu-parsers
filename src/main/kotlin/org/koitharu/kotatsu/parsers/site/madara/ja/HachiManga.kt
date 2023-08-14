package org.koitharu.kotatsu.parsers.site.madara.en


import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("HACHIMANGA", "HachiManga", "ja")
internal class HachiManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.HACHIMANGA, "hachiraw.com") {

	override val datePattern = "MMMM dd, yyyy"
	override val selectChapter = "li.a-h"
	override val selectDate = "span.chapter-tim"
	override val selectDesc = "div.dsct"
	override val tagPrefix = "genre/"

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.RATING,
	)

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

					append("/search/")
					append(pages.toString())
					append("/?keyword=")
					append(query.urlEncoded())
				}

				!tags.isNullOrEmpty() -> {
					append("/$tagPrefix")
					append(tag?.key.orEmpty())
					append("/")
					append(pages.toString())
					append("/")
				}

				else -> {

					if (sortOrder == SortOrder.POPULARITY) {
						append("/top-bookmarked")
					}
					if (sortOrder == SortOrder.NEWEST) {
						append("/new-manga")
					}
					if (sortOrder == SortOrder.RATING) {
						append("/top-rating")
					}

					if (sortOrder == SortOrder.UPDATED) {
						append("/latest-updates")
					}
					append("/")
					append(pages.toString())
					append("/")
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.manga-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst("div.data.wleft")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4"))?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = summary?.selectFirst(".mg_genres")?.select("a")?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
						title = a.text().ifEmpty { return@mapNotNullToSet null }.toTitleCase(),
						source = source,
					)
				}.orEmpty(),
				author = summary?.selectFirst(".mg_author")?.selectFirst("a")?.ownText(),
				state = when (summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")?.ownText()?.trim()
					?.lowercase()) {
					"Ongoing" -> MangaState.ONGOING
					"Completed " -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body()

		val chaptersDeferred = async { getChapters(manga, doc) }

		val desc = body.select(selectDesc).html()

		val stateDiv = (body.selectFirst("div.post-content_item:contains(状態)"))?.selectLast("div.summary-content")

		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val alt = doc.body().select(".post-content_item:contains(代替名) .summary-content").firstOrNull()?.tableValue()
			?.text()?.trim()

		manga.copy(
			tags = doc.body().select(selectGenre).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val root2 = doc.body().selectFirstOrThrow("div.manga-content")
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return root2.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylepage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				name = name,
				number = i + 1,
				url = link,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.chapter-pages")
			?: throw ParseException("Root not found", fullUrl)
		return root.select("div.chapter-page").map { div ->
			val img = div.selectFirst("img") ?: div.parseFailed("Page image not found")
			val url = img.src()?.toRelativeUrl(domain) ?: div.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
