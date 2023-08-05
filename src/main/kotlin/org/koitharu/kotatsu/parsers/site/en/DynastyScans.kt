package org.koitharu.kotatsu.parsers.site.en

import okhttp3.Headers
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DYNASTYSCANS", "Dynasty Scans", "en")
internal class DynastyScans(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.DYNASTYSCANS, 117) {
	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)
	override val configKeyDomain = ConfigKey.Domain("dynasty-scans.com")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			if (!query.isNullOrEmpty()) {
				append("/search?q=")
				append(query.urlEncoded())
				append("&")
				append("classes[]".urlEncoded())
				append("=Serie&page=")
				append(page.toString())
			} else if (!tags.isNullOrEmpty()) {
				append("/tags/")
				for (tag in tags) {
					append(tag.key)
				}
				append("?view=groupings&page=")
				append(page.toString())
			} else {
				append("/series?view=cover&page=")
				append(page.toString())
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		//There are no images on the search page
		if (!query.isNullOrEmpty()) {
			return doc.select("dl.chapter-list dd")
				.map { div ->
					val href = div.selectFirstOrThrow("a").attrAsAbsoluteUrl("href")
					Manga(
						id = generateUid(href),
						title = div.selectFirstOrThrow("a").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = false,
						coverUrl = "",
						tags = div.select("span.tags a").mapNotNullToSet { a ->
							MangaTag(
								key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
								title = a.text(),
								source = source,
							)
						},
						state = null,
						author = null,
						source = source,
					)
				}
		} else {
			return doc.select("li.span2")
				.map { div ->
					val href = div.selectFirstOrThrow("a").attrAsAbsoluteUrl("href")
					Manga(
						id = generateUid(href),
						title = div.selectFirstOrThrow("div.caption").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = false,
						coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		}

	}

	override suspend fun getTags(): Set<MangaTag> = emptySet()

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chapters = getChapters(doc)
		val root = doc.requireElementById("main")
		return manga.copy(
			altTitle = null,
			state = when (root.select("h2.tag-title small").last()?.text()) {
				"— Ongoing" -> MangaState.ONGOING
				"— Completed" -> MangaState.FINISHED
				else -> null
			},
			coverUrl = root.selectFirst("img.thumbnail")?.src()
				.orEmpty(), // It is needed if the manga was found via the search.
			tags = root.select("div.tag-tags a").mapNotNullToSet { a ->
				val href = a.attr("href").removeSuffix('/').substringAfterLast('/')
				MangaTag(
					key = href,
					title = a.text(),
					source = source,
				)
			},
			author = null,
			description = null,
			chapters = chapters,
		)
	}

	private fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat("MMM dd yy", sourceLocale)
		return doc.body().select("dl.chapter-list dd").mapChapters { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val dateText = li.select("small").last()?.text()?.replace("released ", "")?.replace("'", "")
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1,
				url = href,
				uploadDate = dateFormat.tryParse(dateText),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow("script:containsData(var pages =)")
		val json = JSONArray(script.data().substringAfter('=').substringBeforeLast(';'))
		val pages = ArrayList<MangaPage>(json.length())
		for (i in 0 until json.length()) {
			val url = "https://" + domain + json.getString(i).substringAfter(":\"").substringBefore("\",")
			pages.add(
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}

