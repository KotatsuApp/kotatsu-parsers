package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.requireSrc
import org.koitharu.kotatsu.parsers.util.textOrNull
import org.koitharu.kotatsu.parsers.util.urlEncoded
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("LANGGEEK", "Làng Geek", "vi", ContentType.COMICS)
internal class LangGeekParser(context: MangaLoaderContext):
	PagedMangaParser(context, MangaParserSource.LANGGEEK, 20, 100) {

	override val configKeyDomain = ConfigKey.Domain("langgeek.net")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

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
					// SortOrder.POPULARITY, only has 1 page
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
				title = Parser.unescapeEntities(item.getString("value"), false),
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
		val root = webClient.httpGet(manga.url).parseHtml()
		val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

		val author = root.selectFirst("li:has(span:contains(Tác giả)) a")?.textOrNull()
		val scanlator = root.selectFirst("li:has(span:contains(Nhóm dịch)) a")?.textOrNull()

		val description = root.selectFirst("li:has(strong:contains(Giới thiệu)) p")?.textOrNull()

		val tags = root.select("li:has(span:contains(Thể Loại)) a").mapToSet { a ->
			val href = a.attr("href")
			val key = href.substringAfter("/the-loai/").removeSuffix("/")
			MangaTag(
				key = key,
				title = a.text(),
				source = source,
			)
		}

		val rows = root.select("div.list_issues > div.row-issue:not(.row-header)")
		val total = rows.size
		val chapters = root.select("div.list_issues > div.row-issue:not(.row-header)")
			.mapIndexed { i, row ->
				val a = row.selectFirst("div.col:first-child a")
					?: throw ParseException("Cant fetch chapter list", manga.url)
				val href = a.attrAsRelativeUrl("href")
				val dateText = row.selectFirst("div.col:last-child")?.text().orEmpty()

				MangaChapter(
					id = generateUid(href),
					title = a.text(),
					number = (total - i).toFloat(),
					volume = 0,
					url = href,
					scanlator = scanlator,
					uploadDate = chapterDateFormat.parseSafe(dateText),
					branch = null,
					source = source,
				)
			}.reversed()

		return manga.copy(
			tags = tags,
			authors = setOfNotNull(author),
			description = description,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("img.lazy").mapNotNull { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
