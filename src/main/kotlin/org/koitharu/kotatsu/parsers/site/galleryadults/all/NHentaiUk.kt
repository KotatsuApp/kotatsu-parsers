package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.lang.IllegalArgumentException

@MangaSourceParser("NHENTAIUK", "NHentai.uk", type = ContentType.HENTAI)
internal class NHentaiUk(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.NHENTAIUK, "nhentai.uk", 50) {
	override val selectGallery = ".gallery"
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".caption"
	override val pathTagUrl = "/tags/popular?p="
	override val selectTags = "#tag-container"
	override val selectTag = "div.tag-container:contains(Tags:) span.tags"
	override val selectAuthor = "div.tag-container:contains(Artists:) a"
	override val selectLanguageChapter = "div.tag-container:contains(Languages:) a"
	override val idImg = "image-container"
	override val listLanguage = arrayOf(
		"/english",
		"/french",
		"/japanese",
		"/chinese",
		"/spanish",
		"/russian",
		"/korean",
		"/german",
		"/italian",
		"/portuguese",
		"/turkish",
	)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					throw IllegalArgumentException("Search is not supported by this source")
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							if (it.key == "languageKey") {
								append("/language")
								append(it.title)
							} else {
								append("/tag/")
								append(it.key)
							}
						}
						append("/?p=")
					} else {
						append("/home?p=")
					}
				}

				null -> append("/?")
			}
			append(page.toString())
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.requireElementById(idImg).selectFirstOrThrow("img").src() ?: root.parseFailed("Image src not found")
	}
}
