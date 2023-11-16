package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("HENTAI3", "3Hentai", type = ContentType.HENTAI)
internal class Hentai3(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAI3, "3hentai.net") {

	override val selectGallery = ".doujin "
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".title"
	override val pathTagUrl = "/tags-popular/"
	override val selectTags = "span.filter-elem"
	override val selectTag = "div.tag-container:contains(Tags :) .filter-elem"
	override val selectAuthor = "div.tag-container:contains(Artistes :) .filter-elem"
	override val selectLanguageChapter = "div.tag-container:contains(Langues :) .filter-elem"
	override val selectUrlChapter = "#main-cover a"
	override val idImg = ".js-main-img"
	override val listLanguage = arrayOf(
		"/english",
		"/spanish",
		"/french",
		"/italian",
		"/portuguese",
		"/russian",
		"/japanese",
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
					append("/")
					append(page)
				} else {
					append("/tags/")
					append(tag?.key.orEmpty())
					append("/")
					append(page)
				}
			} else if (!query.isNullOrEmpty()) {
				append("/search/?q=")
				append(query.urlEncoded())
				append("&page=")
				append(page)
			} else {
				append("/")
				append(page)
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.selectFirstOrThrow(idImg).src() ?: doc.parseFailed("Image src not found")
	}
}
