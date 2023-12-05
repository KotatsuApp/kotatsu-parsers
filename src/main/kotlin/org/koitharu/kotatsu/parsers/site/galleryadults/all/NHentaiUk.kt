package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("NHENTAIUK", "NHentai.uk", type = ContentType.HENTAI)
internal class NHentaiUk(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.NHENTAIUK, "nhentai.uk", pageSize = 50) {
	override val selectGallery = ".gallery"
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".caption"
	override val pathTagUrl = "/tags/popular?p="
	override val selectTags = "#tag-container"
	override val selectTag = "div.tag-container:contains(Tags:) span.tags"
	override val selectAuthor = "div.tag-container:contains(Artists:) a"
	override val selectLanguageChapter = "div.tag-container:contains(Languages:) a"
	override val idImg = "image-container"

	override suspend fun getAvailableLocales(): Set<Locale> = setOf(
		Locale.ENGLISH,
		Locale.FRENCH,
		Locale.JAPANESE,
		Locale.CHINESE,
		Locale("es"),
		Locale("ru"),
		Locale("ko"),
		Locale.GERMAN,
		Locale("pt"),
		Locale.ITALIAN,
		Locale("tr"),
	)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
					throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED)
				}

				is MangaListFilter.Advanced -> {
					when {
						filter.locale != null && filter.tags.isNotEmpty() -> {
							throw IllegalArgumentException(ErrorMessages.FILTER_BOTH_LOCALE_GENRES_NOT_SUPPORTED)
						}

						filter.locale != null -> {
							append("/language")
							append(filter.locale.toLanguagePath())
							append("/?p=")
						}

						filter.tags.isNotEmpty() -> {
							filter.tags.oneOrThrowIfMany()?.let {
								append("/tag/")
								append(it.key)
							}
							append("/?p=")
						}

						else -> {
							append("/home?p=")
						}
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
