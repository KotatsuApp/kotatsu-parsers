package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.ArrayMap
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRUYENHENTAIVN", "TruyenHentaiVN", "vi", type = ContentType.HENTAI)
internal class TruyenHentaiVN(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.TRUYENHENTAIVN, 30) {

	private var cacheTags = suspendLazy(initializer = ::fetchTags)
	override val configKeyDomain = ConfigKey.Domain("truyenhentaivn.life")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = false,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = cacheTags.get().values.toSet())

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			when {
				filter.tags.isNotEmpty() -> {
					val tag = filter.tags.first()
					append(tag.key)
					if (page > 1) {
						append("?page=")
						append(page)
					}
				}

				!filter.query.isNullOrEmpty() -> {
					append("/tim-kiem-truyen/?q=")
					append(filter.query.urlEncoded())
					if (page > 1) {
						append("&page=")
						append(page)
					}
				}

				else -> {
					append("/chap-moi")
					if (page > 1) {
						append("?page=")
						append(page)
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.entry").map { element ->
			val href = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: ""
			val title = element.selectFirst("a.name")?.text() ?: ""
			val cover = element.selectFirst("img")?.src()
			element.selectFirst("span.date-time")?.text()

			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				title = title,
				altTitles = emptySet(),
				authors = emptySet(),
				tags = emptySet(),
				rating = RATING_UNKNOWN,
				state = null,
				coverUrl = cover,
				contentRating = ContentRating.ADULT,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
		return manga.copy(
			authors = setOfNotNull(doc.selectFirst("div.author i")?.textOrNull()),
			tags = doc.select("div.genre.mb-3.mgen a").mapNotNullToSet { a ->
				val key = a.attr("href").substringAfterLast("-")
				val title = a.text().trim()
				if (key.isNotEmpty() && title.isNotEmpty()) {
					MangaTag(
						key = key,
						title = title,
						source = source,
					)
				} else null
			},
			description = doc.selectFirst("div.inner.mb-1.full")?.let { div ->
				div.select("p").joinToString("\n") { it.wholeText() }
			},
			coverUrl = doc.selectFirst("div.book img")?.src(),
			state = when (doc.selectFirst("div.tsinfo .imptdt i")?.text()?.trim()) {
				"Đã hoàn thành" -> MangaState.FINISHED
				"Đang tiến hành" -> MangaState.ONGOING
				else -> null
			},
			chapters = doc.select("div.chap-list .d-flex").mapChapters(reversed = true) { i, div ->
				val url = div.selectFirst("a")?.attrAsRelativeUrl("href") ?: ""
				val name = div.selectFirst("a .name")?.text() ?: ""
				val dateStr = div.selectFirst("a span:last-child")?.text()

				val uploadDate = dateFormat.tryParse(dateStr)

				MangaChapter(
					id = generateUid(url),
					title = name,
					number = i + 1f,
					url = url,
					scanlator = null,
					uploadDate = uploadDate,
					branch = null,
					source = source,
					volume = 0,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div.content-text img").mapNotNull { img ->
			val url = img.src() ?: return@mapNotNull null
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Map<String, MangaTag> {
		// Remove cached tags, avoid "Connection errors"
		// Remake this function from site/vi/BlogTruyenVN.kt
		val doc = webClient.httpGet("https://$domain").parseHtml()
		val tagItems = doc.select("a.py-2[href^=/the-loai-]")
		val tagMap = ArrayMap<String, MangaTag>(tagItems.size)
		for (tag in tagItems) {
			val key = tag.attr("href")
			val title = tag.text()
			tagMap[key] = MangaTag(key = key, title = title, source = source)
		}
		return tagMap
	}
}
