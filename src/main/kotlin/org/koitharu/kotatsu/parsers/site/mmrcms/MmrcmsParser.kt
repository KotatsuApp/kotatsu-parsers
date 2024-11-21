package org.koitharu.kotatsu.parsers.site.mmrcms

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MmrcmsParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	@JvmField
	protected val ongoing: Set<String> = hashSetOf(
		"On Going",
		"Ongoing",
		"En cours",
		"En curso",
		"DEVAM EDİYOR",
		"مستمرة",
	)

	@JvmField
	protected val finished: Set<String> = hashSetOf(
		"Completed",
		"Completo",
		"Complete",
		"Terminé",
		"TAMAMLANDI",
		"مكتملة",
	)

	protected open val imgUpdated = "/cover/cover_250x350.jpg"
	protected open val listUrl = "filterList"
	protected open val tagUrl = "manga-list"
	protected open val datePattern = "dd MMM. yyyy"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {

		if (order == SortOrder.UPDATED) {
			if (filter.query != null || filter.tags.isNotEmpty()) {
				throw IllegalArgumentException("Sorting by update with filters is not supported by this source.")

			}
			val url = buildString {
				append("https://")
				append(domain)
				append("/latest-release?page=")
				append(page.toString())
			}
			return parseMangaListUpdated(webClient.httpGet(url).parseHtml())
		}

		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			append(listUrl)
			append("/?page=")
			append(page.toString())

			append("&author=&tag=&alpha=")
			filter.query?.let {
				append(filter.query.urlEncoded())
			}

			append("&cat=")
			filter.tags.oneOrThrowIfMany()?.let {
				append(it.key)
			}

			append("&sortBy=")
			when (order) {
				SortOrder.POPULARITY -> append("views&asc=false")
				SortOrder.POPULARITY_ASC -> append("views&asc=true")
				SortOrder.ALPHABETICAL -> append("name&asc=true")
				SortOrder.ALPHABETICAL_DESC -> append("name&asc=false")
				else -> append("name&asc=true")
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	protected open fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.media").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("div.media-body h5")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	protected open fun parseMangaListUpdated(doc: Document): List<Manga> {
		return doc.select("div.manga-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			val deeplink = href.substringAfterLast("/")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = "https://$domain/uploads/manga/$deeplink$imgUpdated",
				title = div.selectFirst("h3 a")?.text().orEmpty(),
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

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$tagUrl/").parseHtml()
		return doc.select("ul.list-category li").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").substringAfterLast("cat=")
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.well"
	protected open val selectState = "dt:contains(Statut)"
	protected open val selectAlt = "dt:contains(Autres noms)"
	protected open val selectAut = "dt:contains(Auteur(s))"
	protected open val selectTag = "dt:contains(Catégories)"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body().selectFirstOrThrow("dl.dl-horizontal")
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirst(selectDesc)?.text().orEmpty()
		val stateDiv = body.selectFirst(selectState)?.nextElementSibling()
		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		val alt = doc.body().selectFirst(selectAlt)?.nextElementSibling()?.text()
		val auth = doc.body().selectFirst(selectAut)?.nextElementSibling()?.text()
		val tags = doc.body().selectFirst(selectTag)?.nextElementSibling()?.select("a") ?: emptySet()
		manga.copy(
			tags = tags.mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = auth,
			description = desc,
			altTitle = alt,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}


	protected open val selectDate = "div.date-chapter-title-rtl"
	protected open val selectChapter = "ul.chapters > li:not(.btn)"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = li.selectFirst("h5")?.text() ?: "Chapter : ${i + 1f}",
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

	protected open val selectPage = "div#all img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPage).map { url ->
			val img = url.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}
}
