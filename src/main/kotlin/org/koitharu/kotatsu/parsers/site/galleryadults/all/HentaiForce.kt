package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("HENTAIFORCE", "HentaiForce", type = ContentType.HENTAI)
internal class HentaiForce(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAIFORCE, "hentaiforce.net") {
	override val selectGallery = ".gallery"
	override val selectGalleryLink = "a.gallery-thumb"
	override val pathTagUrl = "/tags/popular/"
	override val selectTags = ".tag-listing"
	override val selectUrlChapter = "#gallery-main-cover a"
	override val selectTag = "div.tag-container:contains(Tags:)"
	override val selectAuthor = "div.tag-container:contains(Artists:) a"
	override val selectLanguageChapter = "div.tag-container:contains(Languages:) a"
	override val idImg = ".gallery-reader-img-wrapper img"
	override val listLanguage = arrayOf(
		"/english",
		"/french",
		"/japanese",
		"/chinese",
		"/spanish",
		"/russian",
		"/korean",
		"/german",
		"/indonesian",
		"/italian",
		"/portuguese",
		"/thai",
		"/vietnamese",
	)

	override val isMultipleTagsSupported = true

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.selectFirstOrThrow(idImg).src() ?: doc.parseFailed("Image src not found")
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append("/search?q=")
					append(filter.query.urlEncoded())
					append("&page=")
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty() && filter.tags.size > 1) {
						append("/search?q=")
						append(buildQuery(filter.tags))
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("&sort=popular")
						}
						append("&page=")
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
						append("/page/")
					}
				}

				null -> append("/page/")
			}
			append(page.toString())
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private fun buildQuery(tags: Collection<MangaTag>) =
		tags.joinToString(separator = " ") { tag ->
			if (tag.key == "languageKey") {
				"language:${tag.title.removePrefix("/")}"
			} else {
				"tag:${tag.title}"
			}
		}
}
