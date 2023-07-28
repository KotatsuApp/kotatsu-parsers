package org.koitharu.kotatsu.parsers.site.mmrcms

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MmrcmsParser(
	context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
	)

	protected open val listUrl = "filterList"
	protected open val tagUrl = "manga-list"
	protected open val datePattern = "dd MMM. yyyy"


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
	)

	@JvmField
	protected val finished: Set<String> = hashSetOf(
		"Completed",
		"Completo",
		"Complete",
		"Terminé",
	)

	protected open val imgUpdated = "/cover/cover_250x350.jpg"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		val url = if (sortOrder == SortOrder.UPDATED) {
			//the Updated page doesn't really exist, we just use the home page to weight the latest chapters, so it doesn't include tag and page management.
			buildString {
				append("https://")
				append(domain)
				append("/latest-release")
				append("?page=")
				append(page.toString())
			}
		} else {
			buildString {
				append("https://")
				append(domain)

				append("/$listUrl/")
				append("?page=")
				append(page.toString())
				append("&asc=true&author=&tag=")

				append("&alpha=")
				if (!query.isNullOrEmpty()) {
					append(query.urlEncoded())
				}

				append("&cat=")
				if (!tags.isNullOrEmpty()) {

					for (tag in tags) {
						append(tag.key)
					}
				}

				append("&sortBy=")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("views")
					SortOrder.ALPHABETICAL -> append("name")
					else -> append("views")
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		if (sortOrder == SortOrder.UPDATED) {

			return doc.select("div.manga-item").map { div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				val deeplink = href.substringAfterLast("/")
				Manga(
					id = generateUid(href),
					url = href,
					publicUrl = href.toAbsoluteUrl(div.host ?: domain),
					coverUrl = "https://$domain/uploads/manga/$deeplink$imgUpdated",
					title = div.selectFirstOrThrow("a").text().orEmpty(),
					altTitle = null,
					rating = RATING_UNKNOWN,
					tags = emptySet(),
					author = null,
					state = null,
					source = source,
					isNsfw = isNsfwSource,
				)
			}
		} else {
			return doc.select("div.media").map { div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				Manga(
					id = generateUid(href),
					url = href,
					publicUrl = href.toAbsoluteUrl(div.host ?: domain),
					coverUrl = div.selectFirst("img")?.src().orEmpty(),
					title = div.selectFirstOrThrow("div.media-body h5").text().orEmpty(),
					altTitle = null,
					rating = div.selectFirstOrThrow("span").ownText().toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
					tags = emptySet(),
					author = null,
					state = null,
					source = source,
					isNsfw = isNsfwSource,
				)
			}
		}

	}

	override suspend fun getTags(): Set<MangaTag> {
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

		val chaptersDeferred = async { getChapters(manga, doc) }

		val desc = doc.selectFirstOrThrow(selectDesc).text()

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
			tags = tags.mapNotNullToSet { a ->
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

	protected open suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = li.selectFirstOrThrow("h5").text(),
				number = i + 1,
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
			val img = url.src()?.toRelativeUrl(domain) ?: url.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}


	protected fun Element.src(): String? {
		var result = absUrl("data-src")
		if (result.isEmpty()) result = absUrl("data-cfsrc")
		if (result.isEmpty()) result = absUrl("src")
		return result.ifEmpty { null }
	}

}
