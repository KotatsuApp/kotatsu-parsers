package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.coroutineScope
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
import java.util.*

@MangaSourceParser("SCANS_MANGAS_ME", "ScansMangas.me", "fr")
internal class ScansMangasMe(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.SCANS_MANGAS_ME, 0) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val configKeyDomain = ConfigKey.Domain("scansmangas.me")

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
			if (page == 1) {
				if (!query.isNullOrEmpty()) {
					append("/?s=")
					append(query.urlEncoded())
					append("&post_type=manga")

				} else if (!tags.isNullOrEmpty()) {
					append("/genres/")
					for (tag in tags) {
						append(tag.key)
					}
				} else {
					append("/tous-nos-mangas/")
					append("?order=")
					when (sortOrder) {
						SortOrder.POPULARITY -> append("popular")
						SortOrder.UPDATED -> append("update")
						SortOrder.ALPHABETICAL -> append("title")
						SortOrder.NEWEST -> append("create")
						else -> append("update")
					}
				}
			} else {
				return emptyList()
			}

		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.postbody .bs .bsx").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("div.bigor div.tt").text().orEmpty(),
				altTitle = null,
				rating = div.selectFirstOrThrow("div.rating i").ownText().toFloatOrNull()?.div(10f)
					?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}


	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tous-nos-mangas/").parseHtml()
		return doc.select("ul.genre li").mapNotNullToSet { li ->
			val key = li.selectFirstOrThrow("a").attr("href").removeSuffix('/').substringAfterLast('/')
			val name = li.selectFirstOrThrow("a").text()
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}


	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chaptersDeferred = getChapters(doc)

		val desc = doc.selectFirstOrThrow("div.desc").html()

		val state = if (doc.select("div.spe span:contains(En cours)").isNullOrEmpty()) {
			MangaState.FINISHED
		} else {
			MangaState.ONGOING
		}

		val alt = doc.body().select("div.infox span.alter").text()

		val aut = doc.select("div.spe span")[2].text().replace("Auteur:", "")

		manga.copy(
			tags = doc.select("div.spe span:contains(Genres) a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = aut,
			state = state,
			chapters = chaptersDeferred,
			isNsfw = manga.isNsfw,
		)
	}


	private fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().requireElementById("chapter_list").select("li").mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			MangaChapter(
				id = generateUid(href),
				name = li.selectFirstOrThrow("span.mobile chapter").text(),
				number = i + 1,
				url = href,
				uploadDate = 0,
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val script = doc.selectFirstOrThrow("script:containsData(page_image)")
		val images = JSONArray(script.data().substringAfterLast("var pages = ").substringBefore(';'))

		val pages = ArrayList<MangaPage>(images.length())
		for (i in 0 until images.length()) {
			val pageTake = images.getJSONObject(i)
			pages.add(
				MangaPage(
					id = generateUid(pageTake.getString("page_image")),
					url = pageTake.getString("page_image"),
					preview = null,
					source = source,
				),
			)
		}

		return pages
	}
}
