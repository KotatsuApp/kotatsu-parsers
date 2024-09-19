package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("HENTAIERA", "HentaiEra", type = ContentType.HENTAI)
internal class HentaiEra(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.HENTAIERA, "hentaiera.com", 25) {
	override val selectTags = ".tags_section"
	override val selectTag = ".galleries_info li:contains(Tags) div.info_tags"
	override val selectAuthor = ".galleries_info li:contains(Artists) span.item_name"
	override val selectLanguageChapter = ".galleries_info li:contains(Languages) div.info_tags .item_name"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableLocales = setOf(
			Locale.ENGLISH,
			Locale.FRENCH,
			Locale.JAPANESE,
			Locale("es"),
			Locale("ru"),
			Locale("ko"),
			Locale.GERMAN,
		),
	)

	override fun Element.parseTags() = select("a.tag, .gallery_title a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".item_name")?.text() ?: it.text()
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {

				!filter.query.isNullOrEmpty() -> {
					append("/search/?key=")
					append(filter.query.urlEncoded())
					append("&")
				}

				else -> {
					if (filter.tags.size > 1 || (filter.tags.isNotEmpty() && filter.locale != null)) {
						append("/search/?key=")
						if (order == SortOrder.POPULARITY) {
							append(
								buildQuery(filter.tags, filter.locale)
									.replace("&lt=1&dl=0&pp=0&tr=0", "&lt=0&dl=0&pp=1&tr=0"),
							)
						} else {
							append(buildQuery(filter.tags, filter.locale))
						}
						append("&")
					} else if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("/tag/")
							append(it.key)
						}
						append("/")

						if (order == SortOrder.POPULARITY) {
							append("popular/")
						}
						append("?")
					} else if (filter.locale != null) {
						append("/language/")
						append(filter.locale.toLanguagePath())
						append("/")

						if (order == SortOrder.POPULARITY) {
							append("popular/")
						}
						append("?")
					} else {
						append("/?")
					}
				}
			}
			append("page=")
			append(page.toString())
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private fun buildQuery(tags: Collection<MangaTag>, locale: Locale?): String {
		val queryDefault =
			"&search=&mg=1&dj=1&ws=1&is=1&ac=1&gc=1&en=0&jp=0&es=0&fr=0&kr=0&de=0&ru=0&lt=1&dl=0&pp=0&tr=0"
		val tag = tags.joinToString(" ", postfix = " ") { it.key }
		val queryMod = when (val lp = locale?.toLanguagePath()) {
			"english" -> queryDefault.replace("en=0", "en=1")
			"japanese" -> queryDefault.replace("jp=0", "jp=1")
			"spanish" -> queryDefault.replace("es=0", "es=1")
			"french" -> queryDefault.replace("fr=0", "fr=1")
			"korean" -> queryDefault.replace("kr=0", "kr=1")
			"russian" -> queryDefault.replace("ru=0", "ru=1")
			"german" -> queryDefault.replace("de=0", "de=1")
			null -> "&search=&mg=1&dj=1&ws=1&is=1&ac=1&gc=1&en=1&jp=1&es=1&fr=1&kr=1&de=1&ru=1&lt=1&dl=0&pp=0&tr=0"
			else -> throw IllegalArgumentException("Unsupported locale: $lp")
		}
		return tag + queryMod
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val urlChapters = doc.selectFirstOrThrow("#cover a, .cover a, .left_cover a").attr("href")
		val tag = doc.selectFirst(selectTag)?.parseTags()
		val branch = doc.select(selectLanguageChapter).joinToString(separator = " / ") {
			it.text()
		}
		return manga.copy(
			tags = tag.orEmpty(),
			author = doc.selectFirst(selectAuthor)?.text(),
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
}
