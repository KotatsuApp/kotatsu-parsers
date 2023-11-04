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

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val url = buildString {
			append("https://")
			append(domain)
			if (!tags.isNullOrEmpty()) {
				if (tag?.key == "languageKey") {
					append("/language")
					append(tag.title)
					append("/?p=")
				} else {
					append("/tag/")
					append(tag?.key)
					append("/?p=")
				}
			} else if (!query.isNullOrEmpty()) {
				throw IllegalArgumentException("Search is not supported by this source")
			} else {
				append("/home?p=")
			}
			append(page)
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.requireElementById(idImg).selectFirstOrThrow("img").src() ?: root.parseFailed("Image src not found")
	}
}
