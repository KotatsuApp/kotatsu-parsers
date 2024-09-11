package org.koitharu.kotatsu.parsers.site.galleryadults.all

import okhttp3.Headers
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
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

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override val isMultipleTagsSupported = true

	override fun getRequestHeaders(): Headers = super.getRequestHeaders().newBuilder()
		.set("User-Agent", config[userAgentKey])
		.build()

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {

				is MangaListFilter.Search -> {
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

				is MangaListFilter.Advanced -> {
					if (filter.tags.size > 1 || (filter.tags.isNotEmpty() && filter.locale != null)) {
						append("/search/?q=")
						append(buildQuery(filter.tags, filter.locale).urlEncoded())
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("&sort=popular")
						}
						append("&")
					} else if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/tag/")
							append(it.key)
						}
						append("/")
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("popular")
						}
						if (page > 1) {
							append("?")
						}
					} else if (filter.locale != null) {
						append("/language/")
						append(filter.locale.toLanguagePath())
						append("/")
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("popular")
						}
						if (page > 1) {
							append("?")
						}
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
			if (page > 1) {
				append("page=")
				append(page.toString())
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private suspend fun fetchMangaTitle(url: String): String {
		val doc = webClient.httpGet(url).parseHtml()
		return doc.selectFirstOrThrow("h1.title").text().trim()
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(selectGallery).map { div ->
			val href = div.selectFirstOrThrow(selectGalleryLink).attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(selectGalleryTitle).text().trim(),
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

	override suspend fun getAvailableLocales(): Set<Locale> = setOf(
		Locale.ENGLISH,
		Locale.JAPANESE,
		Locale.CHINESE,
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
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
