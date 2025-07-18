package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.jsoup.nodes.Document
import java.util.*

@MangaSourceParser("MANGAPILL", "MangaPill", "en")
internal class MangaPill(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MANGAPILL, 50) {

	override val configKeyDomain = ConfigKey.Domain("mangapill.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED, MangaState.UPCOMING),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		// bruh
		val url = if (filter.types.isNotEmpty() || filter.states.isNotEmpty() || !filter.query.isNullOrEmpty() || filter.tags.isNotEmpty()) {
			buildString {
				append("/search")
				append("?type=")
				append(filter.types.firstOrNull()?.let {
					when (it) {
						ContentType.MANGA -> "manga"
						ContentType.MANHWA -> "manhwa"
						ContentType.MANHUA -> "manhua"
						else -> ""
					}
				} ?: "")

				append("&status=")
				append(filter.states.firstOrNull()?.let {
					when (it) {
						MangaState.FINISHED -> "finished"
						MangaState.ABANDONED -> "discontinued"
						MangaState.ONGOING -> "publishing"
						MangaState.UPCOMING -> "not+yet+published"
						else -> ""
					}
				} ?: "")

				if (!filter.query.isNullOrEmpty()) {
					append("&q=")
					append(filter.query.urlEncoded())
				}

				if (filter.tags.isNotEmpty()) {
					filter.tags.forEach { tag ->
						append("&genre=${tag.key}")
					}
				}

				append("&page=$page")
			}
		} else {
			buildString {
				append("/search?status=publishing") // Avoid empty results, for "UPDATED" order
				append("&page=$page")
			}
		}

		val doc = webClient.httpGet(url.toAbsoluteUrl(domain)).parseHtml()
		return parseMangaList(doc)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("a.relative.block").mapNotNull { element ->
			val href = element.attrAsRelativeUrl("href") ?: return@mapNotNull null
			val img = element.selectFirst("img") ?: return@mapNotNull null
			val coverUrl = img.attr("data-src").orEmpty()
			val title = element.parent()?.selectFirst("div.mt-3.font-black.leading-tight.line-clamp-2")?.text() ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val altTitle = doc.selectFirst("div.text-sm.text-secondary")?.text()
		val description = doc.selectFirst("p.text-sm.text--secondary")?.text()
		val status = doc.select("label.text-secondary").firstOrNull { it.text() == "Status" }
			?.nextElementSibling()?.text()

		val tags = doc.select("div").firstOrNull {
			it.selectFirst("label.text-secondary")?.text() == "Genres" 
		}?.select("a.text-sm.mr-1.text-brand")?.mapToSet { element ->
			MangaTag(
				key = element.attr("href").substringAfter("/search?genre="),
				title = element.text(),
				source = source,
			)
		} ?: emptySet()

		val chapters = doc.select("div#chapters a").map { element ->
			val href = element.attrAsRelativeUrl("href")
			val name = element.text()
			val chapterNumber = name.substringAfter("Chapter ").toFloatOrNull() ?: 0f

			MangaChapter(
				id = generateUid(href),
				title = name,
				url = href,
				number = chapterNumber,
				volume = 0,
				scanlator = null,
				uploadDate = 0,
				branch = null,
				source = source,
			)
		}.reversed()

		return manga.copy(
			description = description,
			state = when (status) {
				"publishing" -> MangaState.ONGOING
				"finished" -> MangaState.FINISHED
				"discontinued" -> MangaState.ABANDONED
				"not yet published" -> MangaState.UPCOMING
				else -> null
			},
			tags = tags,
			altTitles = altTitle?.let { setOf(it) } ?: emptySet(),
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("img.js-page").map { img ->
			val url = img.attr("data-src")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/search").parseHtml()
		return doc.select("div.m-1 label input").mapNotNull { element ->
			val title = element.attr("value")
			val key = title.replace(" ", "+")
			MangaTag( key = key, title = title, source = source, )
		}.toSet()
	}
}
