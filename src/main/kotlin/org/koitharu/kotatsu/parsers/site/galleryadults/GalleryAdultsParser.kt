package org.koitharu.kotatsu.parsers.site.galleryadults

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class GalleryAdultsParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val isMultipleTagsSupported = false

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
					val tag = filter.tags.oneOrThrowIfMany()
					val lang = filter.locale
					if (tag != null && lang != null) {
						throw IllegalArgumentException(ErrorMessages.FILTER_BOTH_LOCALE_GENRES_NOT_SUPPORTED)
					}
					if (tag != null) {
						append("/tag/")
						append(tag.key)
						append("/?")
					} else if (filter.locale != null) {
						append("/language/")
						append(filter.locale.toLanguagePath())
						append("/?")
					} else {
						append("/?")
					}
				}

				null -> append("/?")
			}
			append("page=")
			append(page)
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	protected open val selectGallery = ".thumb"
	protected open val selectGalleryLink = ".inner_thumb a"
	protected open val selectGalleryImg = "img"
	protected open val selectGalleryTitle = "h2"

	protected open fun parseMangaList(doc: Document): List<Manga> {
		val regexBrackets = Regex("\\[[^]]+]|\\([^)]+\\)")
		val regexSpaces = Regex("\\s+")
		return doc.select(selectGallery).map { div ->
			val href = div.selectFirstOrThrow(selectGalleryLink).attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(selectGalleryTitle).text().replace(regexBrackets, "")
					.replace(regexSpaces, " ")
					.trim(),
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

	//Tags are deliberately reduced because there are too many and this slows down the application.
	//only the most popular ones are taken.
	override suspend fun getAvailableTags(): Set<MangaTag> {
		return coroutineScope {
			(1..3).map { page ->
				async { getTags(page) }
			}
		}.awaitAll().flattenTo(ArraySet(360))
	}

	override suspend fun getAvailableLocales(): Set<Locale> = setOf(
		Locale.ENGLISH,
		Locale.FRENCH,
		Locale.JAPANESE,
		Locale.CHINESE,
		Locale("es"),
		Locale("ru"),
		Locale("ko"),
		Locale.GERMAN,
		Locale("id"),
		Locale.ITALIAN,
		Locale("pt"),
		Locale("tr"),
		Locale("th"),
		Locale("vi"),
	)

	protected open val pathTagUrl = "/tags/popular/?page="
	protected open val selectTags = ".tags_page ul.tags li"

	private suspend fun getTags(page: Int): Set<MangaTag> {
		val url = "https://$domain$pathTagUrl$page"
		val root = webClient.httpGet(url).parseHtml().selectFirstOrThrow(selectTags)
		return root.parseTags()
	}

	protected open fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.html().substringBefore("<")
		MangaTag(
			key = key,
			title = name.toTitleCase(),
			source = source,
		)
	}

	protected open val selectTag = "div.tags:contains(Tags:) .tag_list"
	protected open val selectAuthor = "ul.artists a.tag_btn"
	protected open val selectLanguageChapter = "div.tags:contains(Languages:) .tag_list a span.tag"
	protected open val selectUrlChapter = "#cover a, .cover a, .left_cover a, .g_thumb a, .gallery_left a, .gt_left a"

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val urlChapters = doc.selectFirstOrThrow(selectUrlChapter).attr("href")
		val tag = doc.selectFirst(selectTag)?.parseTags()
		val branch = doc.select(selectLanguageChapter).joinToString(separator = " / ") {
			it.html().substringBefore("<")
		}
		return manga.copy(
			tags = tag.orEmpty(),
			author = doc.selectFirst(selectAuthor)?.html()?.substringBefore("<span"),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1f,
					volume = 0,
					url = urlChapters,
					scanlator = null,
					uploadDate = 0,
					branch = branch,
					source = source,
				),
			),
		)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return parseMangaList(webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml())
	}

	protected open val selectTotalPage = ".total_pages, .num-pages, .tp"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val totalPages = doc.selectFirstOrThrow(selectTotalPage).text().toInt()
		val rawUrl = chapter.url.removeSuffix("/").substringBeforeLast("/") + "/"
		return (1..totalPages).map {
			val url = "$rawUrl$it/"
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	protected open val idImg = "gimg"

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.requireElementById(idImg).src() ?: doc.parseFailed("Image src not found")
	}

	protected open fun Locale.toLanguagePath() = when (language) {
		else -> getDisplayLanguage(Locale.ENGLISH).lowercase()
	}
}
