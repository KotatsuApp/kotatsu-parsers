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

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.selectFirstOrThrow(idImg).src() ?: doc.parseFailed("Image src not found")
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (query.isNullOrEmpty() && tags != null && tags.size > 1) {
			return getListPage(page, buildQuery(tags), emptySet(), sortOrder)
		}
		val url = buildString {
			append("https://")
			append(domain)
			if (!tags.isNullOrEmpty()) {
				val tag = tags.single()
				if (tag.key == "languageKey") {
					append("/language")
					append(tag.title)
				} else {
					append("/tag/")
					append(tag.key)
				}
				if (sortOrder == SortOrder.POPULARITY) {
					append("/popular")
				}
				append("/")
			} else if (!query.isNullOrEmpty()) {
				append("/search?q=")
				append(query.urlEncoded())
				if (sortOrder == SortOrder.POPULARITY) {
					append("&sort=popular")
				}
				append("&page=")
			} else {
				append("/page/")
			}
			append(page)
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
