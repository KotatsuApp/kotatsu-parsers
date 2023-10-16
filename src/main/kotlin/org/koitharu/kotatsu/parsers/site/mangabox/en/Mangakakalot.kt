package org.koitharu.kotatsu.parsers.site.mangabox.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("MANGAKAKALOT", "Mangakakalot", "en")
internal class Mangakakalot(context: MangaLoaderContext) :
	MangaboxParser(context, MangaSource.MANGAKAKALOT) {

	override val configKeyDomain = ConfigKey.Domain("mangakakalot.com", "chapmanganato.com")

	override val otherDomain = "chapmanganato.com"

	override val listUrl = "/manga_list"

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

			if (!query.isNullOrEmpty()) {
				append(searchUrl)
				append(query.replace(" ", "_").urlEncoded())
				append("?page=")
				append(page.toString())

			} else {
				append("$listUrl/")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("?type=topview")
					SortOrder.UPDATED -> append("?type=latest")
					SortOrder.NEWEST -> append("?type=newest")
					else -> append("?type=latest")
				}
				if (!tags.isNullOrEmpty()) {
					append("&category=")
					append(tag?.key.orEmpty())
				} else {
					append("&category=all")
				}
				append("&state=all&page=")
				append(page)

			}


		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.list-truyen-item-wrap").ifEmpty {
			doc.select("div.story_item")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text().orEmpty(),
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

	override suspend fun getChapters(doc: Document): List<MangaChapter> {

		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.select(selectDate).last()?.text() ?: "0"
			val dateFormat = if (dateText.contains("-")) {
				SimpleDateFormat("MMM-dd-yy", sourceLocale)
			} else {
				SimpleDateFormat(datePattern, sourceLocale)
			}

			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1,
				url = href,
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
}
