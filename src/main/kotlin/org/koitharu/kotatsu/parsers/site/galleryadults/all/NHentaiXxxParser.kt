package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("NHENTAI_XXX", "NHentai.xxx", type = ContentType.HENTAI)
internal class NHentaiXxxParser(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.NHENTAI_XXX, "nhentai.xxx", 25) {

	val supportedImageFormats = setOf("jpg", "webp", "jpeg", "png", "bmp")

	override val selectGallery = "div.galleries_box .gallery_item, #related-container .gallery_item"
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".caption"
	override val pathTagUrl = "/tags/popular?page="
	override val selectTitle = "h1"
	override val selectTags = "div.tags_items"
	override val selectTag = ".tags:contains(Tags)"
	override val selectAuthor = ".tags:contains(Artists) span.tag_name"
	override val selectLanguageChapter =
		".tags:contains(Languages) a:not([href=\"/language/translated/\"]) span.tag_name"
	override val idImg = "fimg"

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableLocales = setOf(Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			val tags = filter.tags
			val lang = filter.locale
			if (tags.isNotEmpty() && lang != null) {
				throw IllegalArgumentException(ErrorMessages.FILTER_BOTH_LOCALE_GENRES_NOT_SUPPORTED)
			}

			when {
				lang != null -> {
					append("/language/")
					append(lang.toLanguagePath())
					append("/?")
				}

				else -> {
					append("/search/?key=")

					val joiner = StringUtil.StringJoiner("+")
					tags.forEach { tag ->
						joiner.add(tag.title)
					}

					if (!filter.query.isNullOrEmpty()) {
						joiner.add(filter.query.urlEncoded())
					}
					append(joiner.complete())
				}
			}

			when (order) {
				SortOrder.POPULARITY, SortOrder.RELEVANCE -> append("&sort=popular")
				SortOrder.UPDATED -> {}
				else -> {}
			}

			if (page > 1) {
				append("&page=")
				append(page.toString())
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(selectGallery).map { div ->
			val href = div.selectFirstOrThrow(selectGalleryLink).attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(selectGalleryTitle).text().cleanupTitle(),
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = div.selectFirstOrThrow(selectGalleryImg).src(),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				largeCoverUrl = null,
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val totalPages = doc.selectFirstOrThrow(selectTotalPage).text().toInt()
		val firstPageUrl = doc.requireElementById(idImg).requireSrc()
		return (1..totalPages).map {
			val url = replacePageNumber(firstPageUrl, it)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		return if (hasError(page.url)) {
			findAlternativeUrl(page.url) ?: page.url
		} else {
			page.url
		}
	}

	private suspend fun hasError(url: String): Boolean {
		return try {
			webClient.httpHead(url)
			false
		} catch (e: Exception) {
			true
		}
	}

	private suspend fun findAlternativeUrl(originalUrl: String): String? {
		val lastSegment = originalUrl.substringAfterLast("/")
		val oldFormat = lastSegment.substringAfterLast(".", "")

		return supportedImageFormats.firstNotNullOfOrNull { format ->
			val newUrl = originalUrl.replace(".$oldFormat", ".$format")
			try {
				webClient.httpHead(newUrl)
				newUrl
			} catch (e: Exception) {
				null // Skip errors and continue checking other formats
			}
		}
	}

	override fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".tag_name")?.text() ?: it.text()
		MangaTag(
			key = key,
			title = name.toTitleCase(sourceLocale),
			source = source,
		)
	}

	private fun replacePageNumber(url: String, newPageNumber: Int): String {
		val lastSegment = url.substringAfterLast("/")
		val extension = lastSegment.substringAfterLast(".", "")

		return if (extension.isNotEmpty()) {
			url.substringBeforeLast("/") + "/$newPageNumber.$extension"
		} else {
			url.substringBeforeLast("/") + "/$newPageNumber"
		}
	}
}
