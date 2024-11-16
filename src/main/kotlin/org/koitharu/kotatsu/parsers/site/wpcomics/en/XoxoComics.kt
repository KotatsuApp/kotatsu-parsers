package org.koitharu.kotatsu.parsers.site.wpcomics.en

import androidx.collection.ArrayMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("XOXOCOMICS", "XoxoComics", "en", ContentType.COMICS)
internal class XoxoComics(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.XOXOCOMICS, "xoxocomic.com", 50) {

	override val listUrl = "/comic-list"
	override val datePattern = "MM/dd/yyyy"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {

				!filter.query.isNullOrEmpty() -> {
					append("/search-comic?keyword=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {

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

					when (order) {
						SortOrder.POPULARITY -> append("/popular")
						SortOrder.UPDATED -> append("/latest")
						SortOrder.NEWEST -> append("/newest")
						SortOrder.ALPHABETICAL -> append("")
						else -> append("/latest")
					}
					append("?page=")
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

	override suspend fun getOrCreateTagMap(): ArrayMap<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val doc = webClient.httpGet("https://$domain$listUrl").parseHtml()
		val list = doc.select("div.genres ul li:not(.active)").mapNotNull { li ->
			val a = li.selectFirst("a") ?: return@mapNotNull null
			val href = a.attr("href").removeSuffix('/').substringAfterLast('/')
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
		val result = list.associateByTo(ArrayMap(list.size)) { it.title }
		tagCache = result
		result
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { loadChapters(fullUrl) }
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
			tags = doc.body().select(selectTag).mapToSet { a ->
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

	private val dateFormat = SimpleDateFormat("MM/dd/yyyy", sourceLocale)

	private suspend fun loadChapters(baseUrl: String): List<MangaChapter> {
		val chapters = ArrayList<MangaChapter>()
		var page = 0
		while (true) {
			++page
			val doc = webClient.httpGet("$baseUrl?page=$page").parseHtml()
			doc.selectFirst("#nt_listchapter nav ul li:not(.heading)") ?: break
			chapters.addAll(
				doc.select("#nt_listchapter nav ul li:not(.heading)").mapChapters { _, li ->
					val a = li.selectFirstOrThrow("a")
					val href = a.attr("href")
					val dateText = li.selectFirst("div.col-xs-3")?.text()
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = 0f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(dateText),
						branch = null,
						source = source,
					)

				},
			)
		}
		chapters.reverse()
		return chapters.mapIndexed { i, x -> x.copy(volume = x.volume, number = (i + 1).toFloat()) }
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain) + "/all"
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).mapNotNull { url ->
			val img = url.src()?.toRelativeUrl(domain) ?: return@mapNotNull null
			val originalImage = img.replace("[", "").replace("]", "")
			MangaPage(
				id = generateUid(img),
				url = originalImage,
				preview = null,
				source = source,
			)
		}
	}
}
