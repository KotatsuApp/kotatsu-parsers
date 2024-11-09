package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DYNASTYSCANS", "DynastyScans", "en")
internal class DynastyScans(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.DYNASTYSCANS, 117) {

	override val configKeyDomain = ConfigKey.Domain("dynasty-scans.com")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		when {
			!filter.query.isNullOrEmpty() -> {
				val url = buildString {
					append("https://")
					append(domain)
					append("/search?q=")
					append(filter.query.urlEncoded())
					append("&")
					append("classes[]=Series&page=")
					append(page.toString())
				}
				return parseMangaListQuery(webClient.httpGet(url).parseHtml())
			}

			else -> {

				val url = buildString {
					append("https://")
					append(domain)
					if (filter.tags.isNotEmpty()) {
						append("/tags/")
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
						append("?view=groupings")
					} else {
						append("/series?view=cover")
					}

					append("&page=")
					append(page.toString())
				}
				return parseMangaList(webClient.httpGet(url).parseHtml())
			}
		}
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("li.span2")
			.map { div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
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

	private fun parseMangaListQuery(doc: Document): List<Manga> {
		return doc.select("dl.chapter-list dd")
			.map { div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				Manga(
					id = generateUid(href),
					title = div.selectFirstOrThrow("a").text(),
					altTitle = null,
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = "",
					tags = div.select("span.tags a").mapToSet { a ->
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
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		return coroutineScope {
			(1..3).map { page ->
				async { getTags(page) }
			}
		}.awaitAll().flattenTo(ArraySet(360))
	}

	private suspend fun getTags(page: Int): Set<MangaTag> {
		val url = "https://$domain/tags?page=$page"
		val root = webClient.httpGet(url).parseHtml()
		return root.selectFirstOrThrow(".tag-list ").parseTags()
	}

	private fun Element.parseTags() = select("a").mapToSet {
		MangaTag(
			key = it.attr("href").removeSuffix('/').substringAfterLast('/'),
			title = it.text(),
			source = source,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chapters = getChapters(doc)
		val root = doc.requireElementById("main")
		val licensedText = root.select("h4")
			.find { it.ownText() == "This manga has been licensed" }
			?.nextElementSibling()?.html()
		return manga.copy(
			altTitle = null,
			state = when (root.select("h2.tag-title small").last()?.text()) {
				"— Ongoing" -> MangaState.ONGOING
				"— Completed", "— Completed and Licensed" -> MangaState.FINISHED
				"— Dropped", "— Licensed and Removed", "— Abandoned" -> MangaState.ABANDONED
				"— On Hiatus" -> MangaState.PAUSED
				else -> null
			},
			coverUrl = root.selectFirst("img.thumbnail")?.src()
				.orEmpty(), // It is needed if the manga was found via the search.
			tags = root.selectFirstOrThrow("div.tag-tags").parseTags(),
			author = null,
			description = licensedText,
			chapters = chapters,
		)
	}

	private fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat("MMM dd yy", sourceLocale)
		return doc.body().select("dl.chapter-list dd").mapChapters { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.select("small").last()?.text()?.replace("released ", "")?.replace("'", "")
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1f,
				volume = 0,
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
