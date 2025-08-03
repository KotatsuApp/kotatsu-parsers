package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*
import org.koitharu.kotatsu.parsers.Broken

@Broken("Debugging...")
@MangaSourceParser("LANGGEEK", "Làng Geek", "vi")
internal class LangGeekParser(context: MangaLoaderContext):
	PagedMangaParser(context, MangaParserSource.LANGGEEK, 20, 100) {

	override val configKeyDomain = ConfigKey.Domain("langgeek.net")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return when {
			!filter.query.isNullOrEmpty() -> {
				if (page > 1) {
					return emptyList()
				}

				val keyword = filter.query.urlEncoded()
				val url = "https://$domain/wp-admin/admin-ajax.php?action=flatsome_ajax_search_products&query=${keyword}"
				val response = webClient.httpGet(url.toHttpUrl()).parseJson()
				parseMangaSearch(response)
			}

			filter.tags.isNotEmpty() -> {
				val tag = filter.tags.first()
				val url = buildString {
					append("https://")
					append(domain)
					append("/the-loai/")
					append(tag.key)
					if (page > 1) {
						append("/page/")
						append(page)
					}
				}
				val response = webClient.httpGet(url).parseHtml()
				parseMangaList(response)
			}

			else -> {
				val url = buildString {
					append("https://")
					append(domain)
					// SortOrder.POPULARITY, only 1 page
					append("/top-truyen/")
				}

				val response = webClient.httpGet(url).parseHtml()
				parseMangaList(response)
			}
		}
	}

	private fun parseMangaSearch(json: JSONObject): List<Manga> {
		val suggestions = json.getJSONArray("suggestions")

		return (0 until suggestions.length()).map { index ->
			val item = suggestions.getJSONObject(index)
			val href = item.getString("url")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				title = item.getString("value"),
				altTitles = emptySet(),
				authors = emptySet(),
				tags = emptySet(),
				rating = RATING_UNKNOWN,
				state = null,
				coverUrl = item.getString("img"),
				contentRating = null,
				source = source,
			)
		}
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.col.post-item").mapNotNull { div ->
			val a = div.selectFirst("a.plain") ?: return@mapNotNull null
			val img = div.selectFirst("img.wp-post-image")?.requireSrc()
			val titleElement = div.selectFirst("h5.post-title")
			val mangaUrl = a.attr("href")
			val title = titleElement?.text().orEmpty()

			Manga(
				id = generateUid(mangaUrl),
				publicUrl = mangaUrl,
				url = mangaUrl,
				title = title,
				altTitles = emptySet(),
				authors = emptySet(),
				tags = emptySet(),
				rating = RATING_UNKNOWN,
				state = null,
				coverUrl = img,
				contentRating = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).apply {
			timeZone = TimeZone.getTimeZone("GMT+7")
		}
		val author = root.selectFirst("div.mt-2:contains(Tác giả) span a")?.textOrNull()
		val altTitles = root.selectFirst("div.grow div:contains(Tên khác)")
			?.select("span a")?.mapToSet { it.text() }
			?: emptySet()

		return manga.copy(
			altTitles = altTitles,
			state = when (root.selectFirst("div.mt-2:contains(Tình trạng) span.text-blue-500")?.text()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Đã hoàn thành" -> MangaState.FINISHED
				else -> null
			},
			tags = root.selectFirst("div.mt-2:contains(Thể loại)")?.select("a.bg-gray-500")?.mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			} ?: emptySet(),
			authors = setOfNotNull(author),
			description = root.selectFirst("meta[name=description]")?.attrOrNull("content"),
			chapters = root.select("div.justify-between ul.overflow-y-auto.overflow-x-hidden a")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirst("span.text-ellipsis")?.text().orEmpty()
					val dateText = a.parent()?.selectFirst("span.timeago")?.attr("datetime").orEmpty()
					val scanlator = root.selectFirst("div.mt-2:has(span:first-child:contains(Thực hiện:)) span:last-child")?.textOrNull()

					MangaChapter(
						id = generateUid(href),
						title = name,
						number = (i + 1).toFloat(),
						volume = 0,
						url = href,
						scanlator = scanlator,
						uploadDate = chapterDateFormat.parseSafe(dateText),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.text-center div.lazy")
			.mapNotNull { div ->
				val url = div.attr("data-src")
				if (url.endsWith(".jpg", ignoreCase = true) ||
					url.endsWith(".png", ignoreCase = true)
				) {
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					)
				} else {
					null
				}
			}
	}
}
