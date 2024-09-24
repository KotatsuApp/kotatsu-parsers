package org.koitharu.kotatsu.parsers.site.foolslide

import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class FoolSlideParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 25,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	protected open val listUrl = "directory/"
	protected open val searchUrl = "search/"
	protected open val pagination = true // false if the manga list has no pages
	protected open val datePattern = "yyyy.MM.dd"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val doc = when {
			!filter.query.isNullOrEmpty() -> {
				if (page > 1) {
					return emptyList()
				}

				val url = buildString {
					append("https://")
					append(domain)
					append("/")
					append(searchUrl)
				}

				webClient.httpPost(url, "search=${filter.query.urlEncoded()}").parseHtml()
			}

			else -> {

				val url = buildString {
					append("https://")
					append(domain)
					append('/')
					append(listUrl)
					// For some sites that don't have enough manga and page 2 links to page 1
					if (!pagination) {
						if (page > 1) {
							return emptyList()
						}
					} else {
						append(page.toString())
					}
				}
				webClient.httpGet(url).parseHtml()

			}
		}

		return doc.select("div.list div.group").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),// in search no img
				title = div.selectFirst(".title a")?.text().orEmpty(),
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

	protected open val selectInfo = "div.info"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val testAdultPage = webClient.httpGet(fullUrl).parseHtml()
		val doc = if (testAdultPage.selectFirst("div.info form") != null) {
			webClient.httpPost(fullUrl, "adult=true").parseHtml()
		} else {
			testAdultPage
		}
		val chapters = getChapters(doc)
		val desc = if (doc.selectFirst(selectInfo)?.html()?.contains("</b>") == true) {
			doc.selectFirst(selectInfo)?.text()?.substringAfterLast(": ")
		} else {
			doc.selectFirst(selectInfo)?.text()
		}
		val author = if (doc.selectFirst(selectInfo)?.html()?.contains("</b>") == true) {
			doc.selectFirst(selectInfo)?.text()?.substringAfter(": ")?.substringBefore("Art")
		} else {
			null
		}
		manga.copy(
			coverUrl = doc.selectFirst(".thumbnail img")?.src() ?: manga.coverUrl,
			description = desc.orEmpty(),
			altTitle = null,
			author = author.orEmpty(),
			state = null,
			chapters = chapters,
		)
	}


	protected open val selectDate = ".meta_r"
	protected open val selectChapter = "div.list div.element"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, div ->
			val a = div.selectFirstOrThrow(".title a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = div.selectFirst(selectDate)?.text()?.substringAfter(", ")
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = if (div.selectFirst(selectDate)?.text()?.contains(", ") == true) {
					dateFormat.tryParse(dateText)
				} else {
					0
				},
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val script = doc.selectFirstOrThrow("script:containsData(var pages = )")
		val images = JSONArray(script.data().substringAfterLast("var pages = ").substringBefore(';'))
		val pages = ArrayList<MangaPage>(images.length())
		for (i in 0 until images.length()) {
			val pageTake = images.getJSONObject(i)
			pages.add(
				MangaPage(
					id = generateUid(pageTake.getString("url")),
					url = pageTake.getString("url"),
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
