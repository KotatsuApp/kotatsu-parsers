package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("SCANTRADUNION", "Scantrad Union", "fr")
internal class ScantradUnion(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.SCANTRADUNION, 10) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
	)

	override val configKeyDomain = ConfigKey.Domain("scantrad-union.com")

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
			when {
				!query.isNullOrEmpty() -> {
					append("/page/")
					append(page.toString())
					append("/?s=")
					append(query.urlEncoded())

				}

				!tags.isNullOrEmpty() -> {
					append("/tag/")
					for (tag in tags) {
						append(tag.key)
						append(',')
					}
					append("/page/")
					append(page.toString())
				}

				else -> {

					if (sortOrder == SortOrder.ALPHABETICAL) {
						append("/manga/")
						append("/page/")
						append(page.toString())
					}

					if (sortOrder == SortOrder.UPDATED) {
						append("")
					}

				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		if (doc.getElementById("dernierschapitres") != null) {
			val root = doc.requireElementById("dernierschapitres")
			return root.select("div.colonne")
				.map { article ->
					val href = article.selectFirstOrThrow("a.index-top4-a").attrAsAbsoluteUrl("href")
					Manga(
						id = generateUid(href),
						title = article.select(".carteinfos a").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = false,
						coverUrl = article.selectFirstOrThrow("img.attachment-thumbnail").attrAsAbsoluteUrl("src"),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		} else {
			val root = doc.requireElementById("main")
			return root.select("article.post-outer")
				.map { article ->
					val href = article.selectFirstOrThrow("a.thumb-link").attrAsAbsoluteUrl("href")
					Manga(
						id = generateUid(href),
						title = article.select(".index-post-header a").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						isNsfw = false,
						coverUrl = article.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		}


	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("main")
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)

		return manga.copy(
			altTitle = root.select(".divider2:contains(Noms associés :)").firstOrNull()?.text(),
			state = when (root.select(".label.label-primary")[2].text()) {
				"En cours" -> MangaState.ONGOING
				"Terminé", "Abondonné", "One Shot" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("div.project-details a[href*=tag]").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = root.select("div.project-details a[href*=auteur]").text(),
			description = root.selectFirst("p.sContent")?.html(),
			chapters = root.select("div.chapter-list li")
				.mapChapters(reversed = true) { i, li ->

					val href = li.getElementsByTag("a").firstNotNullOf { a ->
						a.attrAsAbsoluteUrlOrNull("href")?.takeIf { it.contains("https://$domain/read/") }
					}

					val name = if (li.select(".chapter-name").isNullOrEmpty()) {
						li.select(".chapter-name").text()
					} else {
						"Chapter $i"
					}
					val date = li.select(".name-chapter").first()!!.children().elementAt(2).text()
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(date),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("#webtoon")
			?: throw ParseException("Root not found", fullUrl)
		return root.select("img").map { img ->
			val url = img.attrAsRelativeUrlOrNull("data-src") ?: img.attrAsRelativeUrlOrNull("src")
			?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val body = doc.body()
		val root = body.select(".asp_gochosen")[1]
		val list = root?.select("option").orEmpty()
		return list.mapToSet { li ->

			MangaTag(
				key = li.text(),
				title = li.text(),
				source = source,
			)
		}
	}
}
