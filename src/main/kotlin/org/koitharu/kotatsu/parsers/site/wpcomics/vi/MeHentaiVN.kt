package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.net.URL
import java.util.*

@MangaSourceParser("MEHENTAIVN", "MeHentaiVN", "vi", ContentType.HENTAI)
internal class MeHentaiVN(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.MEHENTAIVN, "www.mehentaivn.xyz", 44) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain(
		"www.mehentaivn.xyz",
		"www.hentaivnx.autos",
		"www.hentaivnx.com"
	)

	override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("referer", "https://$domain/")
		.build()

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val response =
			when {
				// url template: https://www.mehentaivn.xyz/tim-truyen?keyword=${query}
				!filter.query.isNullOrEmpty() -> {
					val url = buildString {
						append("https://")
						append(domain)
						append(listUrl)
						append("?keyword=")
						append(filter.query.urlEncoded())
						if (page > 1) {
							append("&page=$page")
						}
					}

					val result = runCatchingCancellable { webClient.httpGet(url) } // execute
					val exception = result.exceptionOrNull()
					if (exception is NotFoundException) {
						return emptyList()
					}
					result.getOrThrow()
				}

				// url tempalte: https://www.mehentaivn.xyz/tim-truyen-nang-cao?{query}
				// Query Structure:
				// genres=19775801,1&                /* tags include */
				// notgenres=19776383,19777327&      /* tags exclude */
				// minchapter=0&                     /* chapter count. Leaves 0 to get everything */
				// sort=15&                          /* Sort order */
				// contain=                          /* Not supported */
				else -> {
					val queries = mutableListOf<String>()

					// tags
					queries.add("genres=${filter.tags.joinToString (",") { it.key }}")

					// tags exclude
					queries.add("notgenres=${filter.tagsExclude.joinToString (",") { it.key }}")

					if (filter.tags.isNotEmpty() or filter.tagsExclude.isNotEmpty()) {
						// This means our query is not empty!
						val url = buildString {
							append("http://$domain/tim-truyen-nang-cao?")
							append(queries.joinToString("&"))

							// order
							when (order) {
								SortOrder.NEWEST ->     append("&sort=15")      // Truyện mới
								SortOrder.POPULARITY -> append("&sort=10")      // Top all
								SortOrder.UPDATED ->    append("&sort=0")       // Truyện mới
								SortOrder.RATING ->     append("&sort=20")      // Theo dõi
								else -> throw IllegalArgumentException("Sort order ${order.name} not supported")
							}

							if (page > 1) {
								append("&page=$page")
							}
						}

						webClient.httpGet(url) // execute
						
					} else {
						val url = buildString {
							append("https://$domain/")
							if (page > 1) {
								append("?page=$page")
							}
						}

						webClient.httpGet(url)
					}
				}
			}

		val tagMap = getOrCreateTagMap()
		return parseSearchList(response.parseHtml(), tagMap)
	}

	private fun parseSearchList(doc: Document, tagMap: ArrayMap<String, MangaTag>): List<Manga> {
		return doc.select("div.items div.item").mapNotNull { item ->
			val tooltipElement = item.selectFirst("div.box_tootip")
			val absUrl = item.selectFirst("div.image > a")?.attrAsAbsoluteUrlOrNull("href") ?: return@mapNotNull null
			val url = absUrl.toRelativeUrl(domain)
			val mangaState =
				when (tooltipElement?.selectFirst("div.message_main > p:contains(Tình trạng)")?.ownText()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			val tagsElement =
				tooltipElement?.selectFirst("div.message_main > p:contains(Thể loại)")?.ownText().orEmpty()
			val mangaTags = tagsElement.split(',').mapNotNullToSet { tagMap[it.trim()] }
			val author = tooltipElement?.selectFirst("div.message_main > p:contains(Tác giả)")?.ownText()
			val coverUrl = checkImgUrl(item.selectFirst("div.image a img")?.requireSrc())
			Manga(
				id = generateUid(url),
				title = item.selectFirst("div.box_tootip div.title, h3 a")?.text().orEmpty(),
				altTitles = emptySet(),
				url = url,
				publicUrl = absUrl,
				rating = RATING_UNKNOWN,
				contentRating = null,
				coverUrl = coverUrl,
				largeCoverUrl = null,
				tags = mangaTags,
				state = mangaState,
				authors = setOfNotNull(author),
				description = tooltipElement?.selectFirst("div.box_text")?.text(),
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val tagsElement = doc.select("li.kind p.col-xs-8 a")
		val mangaTags = tagsElement.mapNotNullToSet {
			val tagTitle = it.text()
			if (tagTitle.isNotEmpty())
				MangaTag(
					title = tagTitle.toTitleCase(sourceLocale),
					key = tagsElement.attr("href").substringAfterLast('/').trim(),
					source = source,
				)
			else null
		}
		val author = doc.body().selectFirst(selectAut)?.textOrNull()

		manga.copy(
			title = doc.select("h1.title-detail").text(),
			description = "", // no more description for manga on this source
			altTitles = setOfNotNull(doc.selectFirst("h2.other-name")?.textOrNull()),
			authors = setOfNotNull(author),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			rating = doc.selectFirst("div.star input")?.attr("value")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			chapters = chaptersDeferred.await(),
			contentRating = ContentRating.ADULT,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select(".page-chapter img").map {
			val url = checkImgUrl(it.requireSrc())
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun checkImgUrl (url: String?) : String {
		if (url.isNullOrEmpty()) return ""
		val urlImage = URL(url)

		// Need updating frequently
		if (urlImage.host.contains("duckduckgo.com")) return url.split("?u=")[1]

		return url
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val tagItems = doc.select("ul.dropdown-menu.megamenu li a")
		val tagSet = ArraySet<MangaTag>(tagItems.size)
		for (item in tagItems) {
			val title = item.attr("data-title").toTitleCase(sourceLocale)
			val key = item.attr("href").substringAfterLast('/').trim()
			if (key.isNotEmpty() && title.isNotEmpty()) {
				tagSet.add(MangaTag(title = title, key = key, source = source))
			}
		}
		return tagSet
	}
}
