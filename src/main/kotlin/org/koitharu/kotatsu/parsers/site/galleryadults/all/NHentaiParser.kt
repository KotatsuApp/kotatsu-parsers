package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("NHENTAI", "NHentai.net", type = ContentType.HENTAI)
internal class NHentaiParser(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.NHENTAI, "nhentai.net", 25) {
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
	override val listLanguage = arrayOf(
		"/english",
		"/japanese",
		"/chinese",
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override val isMultipleTagsSupported = true

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					append("/search/?q=")
					append(filter.query.urlEncoded())
					append("&")
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty() && filter.tags.size > 1) {
						append("/search/?q=")
						append(buildQuery(filter.tags))
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("&sort=popular")
						}
						append("&")
					} else if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							if (it.key == "languageKey") {
								append("/language")
								append(it.title)
							} else {
								append("/tag/")
								append(it.key)
							}
						}
						append("/")
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("popular/")
						}

						append("?")
					} else {
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("/?sort=popular&")
						} else {
							append("/?")
						}
					}
				}

				null -> append("/?")
			}
			append("page=")
			append(page.toString())
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.requireElementById(idImg).selectFirstOrThrow("img").src() ?: root.parseFailed("Image src not found")
	}

	override fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".name")?.text() ?: it.text()
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}

	private fun buildQuery(tags: Collection<MangaTag>) =
		tags.joinToString(separator = " ") { tag ->
			if (tag.key == "languageKey") {
				"language:\"${tag.title.removePrefix("/")}\""
			} else {
				"tag:\"${tag.key}\""
			}
		}
}
