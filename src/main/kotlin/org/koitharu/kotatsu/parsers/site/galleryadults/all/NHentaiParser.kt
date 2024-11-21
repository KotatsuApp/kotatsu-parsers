package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("NHENTAI", "NHentai.net", type = ContentType.HENTAI)
internal class NHentaiParser(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.NHENTAI, "nhentai.net", 25) {
	override val selectGallery = "div.index-container:not(.index-popular) .gallery, #related-container .gallery"
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".caption"
	override val pathTagUrl = "/tags/popular?page="
	override val selectTags = "#tag-container"
	override val selectTag = ".tag-container:contains(Tags:) span.tags"
	override val selectAuthor = "#tags div.tag-container:contains(Artists:) span.name"
	override val selectLanguageChapter =
		".tag-container:contains(Languages:) span.tags a:not(.tag-17249) span.name" // tag-17249 = translated
	override val idImg = "image-container"

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.POPULARITY_TODAY, SortOrder.POPULARITY_WEEK)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableLocales = setOf(Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					// Check if the query is all numbers
					val numericQuery = filter.query.trim()
					if (numericQuery.matches("\\d+".toRegex())) {
						val title = fetchMangaTitle("$this/g/$numericQuery/")
						append("/search/?q=pages:>0 ")
						append(title)
						append("&")
					} else {
						append("/search/?q=pages:>0 ")
						append(filter.query.urlEncoded())
						append("&")
					}
				}

				else -> {
					append("/search/?q=pages:>0 ")
					// for Search with query
					// append(filter.query.urlEncoded())
					// append(' ')
					append(buildQuery(filter.tags, filter.locale).urlEncoded())
					when (order) {
						SortOrder.POPULARITY -> append("&sort=popular")
						SortOrder.POPULARITY_TODAY -> append("&sort=popular-today")
						SortOrder.POPULARITY_WEEK -> append("&sort=popular-week")
						SortOrder.UPDATED -> {}
						else -> {}
					}
				}
			}
			if (page > 1) {
				append("&page=")
				append(page.toString())
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private suspend fun fetchMangaTitle(url: String): String {
		val doc = webClient.httpGet(url).parseHtml()
		return doc.selectFirstOrThrow("h1.title").text()
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(selectGallery).map { div ->
			val href = div.selectFirstOrThrow(selectGalleryLink).attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(selectGalleryTitle).text().cleanupTitle(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = div.selectFirstOrThrow(selectGalleryImg).src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.requireElementById(idImg).selectFirstOrThrow("img").requireSrc()
	}

	override fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".name")?.text() ?: it.text()
		MangaTag(
			key = key,
			title = name.toTitleCase(sourceLocale),
			source = source,
		)
	}

	private fun buildQuery(tags: Collection<MangaTag>, language: Locale?): String {
		val joiner = StringUtil.StringJoiner(" ")
		tags.forEach { tag ->
			joiner.add("tag:\"")
			joiner.append(tag.key)
			joiner.append("\"")
		}
		language?.let { lc ->
			joiner.add("language:\"")
			joiner.append(lc.toLanguagePath())
			joiner.append("\"")
		}
		return joiner.complete()
	}
}
