package org.koitharu.kotatsu.parsers.site.ru

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("ACOMICS", "AComics", "ru", ContentType.COMICS)
internal class AComics(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.ACOMICS, pageSize = 10) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
	)

	override val configKeyDomain = ConfigKey.Domain("acomics.ru")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	init {
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
		context.cookieJar.insertCookies(domain, "ageRestrict=18")
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = getOrCreateTagMap().values.toSet(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					if (page > 0) {
						return emptyList()
					}
					append("/search?keyword=")
					append(filter.query)
				}

				else -> {
					append("/comics?ratings[]=1&ratings[]=2&ratings[]=3&ratings[]=4&ratings[]=5&ratings[]=6&skip=")
					append(page * 10)
					append("&sort=")
					append(
						when (order) {
							SortOrder.UPDATED -> "last_update"
							SortOrder.ALPHABETICAL -> "serial_name"
							SortOrder.POPULARITY -> "subscr_count"
							else -> "last_update"
						},
					)

					if (filter.tags.isNotEmpty()) {
						append("&categories=")
						append(filter.tags.joinToString(separator = ",") { it.key })
					}

					if (filter.states.isNotEmpty()) {
						append("&updatable=")
						append(
							filter.states.oneOrThrowIfMany().let {
								when (it) {
									MangaState.ONGOING -> "yes"
									MangaState.FINISHED -> "no"
									else -> "0"
								}
							},
						)
					}
				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private fun parseMangaList(docs: Document): List<Manga> {
		return docs.select("table.list-loadable").map {
			val a = it.selectFirstOrThrow("a")
			val url = a.attrAsAbsoluteUrl("href") + "/about"
			Manga(
				id = generateUid(url),
				url = url,
				title = it.selectFirstOrThrow(".title").text(),
				altTitle = null,
				publicUrl = url,
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = it.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val tagElements =
			webClient.httpGet("https://$domain/comics").parseHtml().requireElementById("catalog").select(" a.button")
		for (el in tagElements) {
			val name = el.html().substringAfterLast("</span>")
			if (name.isEmpty()) continue
			tagMap[name] = MangaTag(
				title = name,
				key = el.attr("onclick").substringAfterLast("('").substringBefore("')"),
				source = source,
			)
		}
		tagCache = tagMap
		return@withLock tagMap
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val tagMap = getOrCreateTagMap()
		val tags = doc.select("p.serial-about-badges .category").mapNotNullToSet { tagMap[it.text()] }
		return manga.copy(
			tags = tags,
			description = doc.selectFirst("section.serial-about-text p")?.text(),
			author = doc.selectFirst("p:contains(Автор оригинала:)")?.text()?.replace("Автор оригинала: ", ""),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1f,
					volume = 0,
					url = manga.url.replace("/about", "/"),
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url + "1").parseHtml()
		val totalPages = doc.selectFirstOrThrow("span.issueNumber").text().substringAfterLast('/').toInt()
		return (1..totalPages).map {
			val url = chapter.url + it
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.requireElementById("mainImage").requireSrc()
	}
}
