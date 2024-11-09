package org.koitharu.kotatsu.parsers.site.animebootstrap

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class AnimeBootstrapParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 24,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.NEWEST,
	)

	protected open val listUrl = "/manga"
	protected open val datePattern = "dd MMM. yyyy"

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("?page=")
			append(page.toString())
			append("&type=all")

			filter.query?.let {
				append("&search=")
				append(filter.query.urlEncoded())
			}

			filter.tags.oneOrThrowIfMany()?.let {
				append("&categorie=")
				append(it.key)
			}

			filter.types.oneOrThrowIfMany()?.let {
				append("&type=")
				append(
					when (it) {
						ContentType.MANGA -> "manga"
						ContentType.MANHWA -> "manhwa"
						ContentType.MANHUA -> "manhua"
						else -> "all"
					},
				)
			}

			append("&sort=")
			when (order) {
				SortOrder.POPULARITY -> append("view")
				SortOrder.UPDATED -> append("updated")
				SortOrder.ALPHABETICAL -> append("default")
				SortOrder.NEWEST -> append("published")
				else -> append("updated")
			}

		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.col-6 div.product__item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirstOrThrow("div.product__item__pic").attr("data-setbg").orEmpty(),
				title = div.selectFirstOrThrow("div.product__item__text").text().orEmpty(),
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
		val doc = webClient.httpGet("https://$domain$listUrl").parseHtml()
		return doc.select("div.product__page__filter div:contains(Genre:) option ").mapNotNullToSet { option ->
			val key = option.attr("value") ?: return@mapNotNullToSet null
			val name = option.text().toTitleCase(sourceLocale)
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.anime__details__text p"
	protected open val selectState = "div.anime__details__widget li:contains(Ongoing)"
	protected open val selectTag = "div.anime__details__widget li:contains(Categorie) a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirstOrThrow(selectDesc).html()
		val state = if (doc.select(selectState).isNullOrEmpty()) {
			MangaState.FINISHED
		} else {
			MangaState.ONGOING
		}

		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text().toTitleCase().replace(",", ""),
					source = source,
				)
			},
			description = desc,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}


	protected open val selectChapter = "div.anime__details__episodes a"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, a ->
			val href = a.attr("href")
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = 0,
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}


	protected open val selectPage = "div.read-img img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		if (doc.select("script:containsData(page_image)").isNullOrEmpty()) {
			return doc.select(selectPage).map { img ->
				val url = img.attr("onerror").replace("this.onerror=null;this.src=`", "").replace("`;", "")
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		} else {
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
}
