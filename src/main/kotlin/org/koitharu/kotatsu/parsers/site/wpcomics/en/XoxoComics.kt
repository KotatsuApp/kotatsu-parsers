package org.koitharu.kotatsu.parsers.site.wpcomics.en

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("XOXOCOMICS", "XoxoComics", "en", ContentType.COMICS)
internal class XoxoComics(context: MangaLoaderContext) :
	WpComicsParser(context, MangaSource.XOXOCOMICS, "xoxocomic.com", 50) {

	override val listUrl = "/comic-list"
	override val datePattern = "MM/dd/yyyy"

	override val isMultipleTagsSupported = false

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
	)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					append("/search-comic?keyword=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				is MangaListFilter.Advanced -> {

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/")
							append(it.key)
						}
					}

					filter.states.oneOrThrowIfMany()?.let {
						append(
							when (it) {
								MangaState.ONGOING -> "/ongoing"
								MangaState.FINISHED -> "/completed"
								else -> ""
							},
						)
						if (filter.tags.isEmpty()) {
							append("-comic")
						}
					}

					if (filter.states.isEmpty() && filter.tags.isEmpty()) {
						append(listUrl)
					}

					when (filter.sortOrder) {
						SortOrder.POPULARITY -> append("/popular")
						SortOrder.UPDATED -> append("/latest")
						SortOrder.NEWEST -> append("/newest")
						SortOrder.ALPHABETICAL -> append("")
						else -> append("/latest")
					}
					append("?page=")
					append(page.toString())
				}

				null -> {
					append(listUrl)
					append("/?page=")
					append(page.toString())
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.item, #nt_listchapter nav ul li").map { div ->
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain$listUrl").parseHtml()
		return doc.select("div.genres ul li:not(.active)").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix('/').substringAfterLast('/')
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirstOrThrow(selectDesc).html()
		val stateDiv = doc.selectFirst(selectState)
		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		val aut = doc.body().select(selectAut).text()
		manga.copy(
			tags = doc.body().select(selectTag).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = null,
			author = aut,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		val pages = doc.select("ul.pagination > li:not(.active)")
		return if (pages.size <= 1) {
			super.getChapters(doc)
		} else {
			val subPageChapterList = coroutineScope {
				pages.mapNotNull { page ->
					val a = page.selectFirst("a") ?: return@mapNotNull null
					if (a.text().isNumeric()) {
						val href = a.attrAsAbsoluteUrl("href")
						async {
							super.getChapters(webClient.httpGet(href).parseHtml()).asReversed()
						}
					} else {
						null // TODO support pagination with overflow
					}
				}.awaitAll().flatten()
			}
			val firstPageChapterList = super.getChapters(doc).asReversed().toMutableList()
			firstPageChapterList.addAll(subPageChapterList)
			firstPageChapterList.reverse()
			firstPageChapterList.mapIndexed { i, x -> x.copy(volume = x.volume, number = (i + 1).toFloat()) }
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain) + "/all"
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).mapNotNull{ url ->
			val img = url.src()?.toRelativeUrl(domain) ?: return@mapNotNull null
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}
}
