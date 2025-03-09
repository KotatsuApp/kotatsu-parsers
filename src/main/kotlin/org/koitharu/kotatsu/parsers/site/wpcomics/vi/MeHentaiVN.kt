package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MEHENTAIVN", "MeHentaiVN", "vi", ContentType.HENTAI)
internal class MeHentaiVN(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.MEHENTAIVN, "www.mehentaivn.xyz", 44) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("www.mehentaivn.xyz", "www.hentaivnx.autos")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("referer", "no-referrer")
		.build()

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val response =
			when {
				!filter.query.isNullOrEmpty() -> {
					val url = buildString {
						append("https://")
						append(domain)
						append(listUrl)
						append("?keyword=")
						append(filter.query.urlEncoded())
						append("&page=")
						append(page.toString())
					}

					val result = runCatchingCancellable { webClient.httpGet(url) }
					val exception = result.exceptionOrNull()
					if (exception is NotFoundException) {
						return emptyList()
					}
					result.getOrThrow()
				}

				else -> {
					val url = buildString {
						append("https://")
						append(domain)
						append(listUrl)
						if (filter.tags.isNotEmpty()) {
							append('/')
							filter.tags.oneOrThrowIfMany()?.let {
								append(it.key)
							}
						}
						append("?sort=")
						append(
							when (order) {
								SortOrder.UPDATED -> 0
								SortOrder.POPULARITY -> 10
								SortOrder.NEWEST -> 15
								SortOrder.RATING -> 20
								else -> throw IllegalArgumentException("Sort order ${order.name} not supported")
							},
						)
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							append(
								when (it) {
									MangaState.ONGOING -> "1"
									MangaState.FINISHED -> "2"
									else -> "-1"
								},
							)
						}
						append("&page=")
						append(page.toString())
					}

					webClient.httpGet(url)
				}
			}

		val tagMap = getOrCreateTagMap()
		return parseSearchList(response.parseHtml(), tagMap)
	}

	private suspend fun parseSearchList(doc: Document, tagMap: ArrayMap<String, MangaTag>): List<Manga> {
		return doc.select("div.items div.item").mapNotNull { item ->
			val tooltipElement = item.selectFirst("div.box_tootip")
			val absUrl = item.selectFirst("div.image > a")?.attrAsAbsoluteUrlOrNull("href") ?: return@mapNotNull null
			val slug = absUrl.substringAfterLast('/')
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
			val coverUrl = item.selectFirst("div.image a img")?.requireSrc()
			val largeCoverUrl = null
			Manga(
				id = generateUid(slug),
				title = item.selectFirst("div.box_tootip div.title, h3 a")?.text().orEmpty(),
				altTitles = emptySet(),
				url = absUrl.toRelativeUrl(domain),
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
			description = doc.selectFirst(selectDesc)?.html(),
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
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		
		val imageUrls = doc.select("div.page-chapter").flatMap { div ->
			div.select("img").mapNotNull { img ->
				val src = img.attr("src").takeIf { it.isNotEmpty() }
				val dataSrc = img.attr("data-src").takeIf { it.isNotEmpty() }
				val imageUrl = src ?: dataSrc
				
				if (imageUrl != null && checkMangaImgs(imageUrl)) {
					imageUrl
				} else {
					null
				}
			}
		}

		return imageUrls.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun checkMangaImgs(url: String): Boolean {
		return try {
			val response = webClient.httpHead(url)
			val contentType = response.header("Content-Type") ?: ""
			contentType.startsWith("image/")
		} catch (e: Exception) {
			false
		}
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
