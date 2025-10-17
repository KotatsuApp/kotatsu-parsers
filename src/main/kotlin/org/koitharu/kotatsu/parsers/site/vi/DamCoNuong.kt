package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.Headers
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DAMCONUONG", "Dâm Cô Nương", "vi", type = ContentType.HENTAI)
internal class DamCoNuong(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.DAMCONUONG, 30) {

	override val configKeyDomain = ConfigKey.Domain("damconuong.co")

	private val availableTags = suspendLazy(initializer = ::fetchTags)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("referer", "https://$domain")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = availableTags.get(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/tim-kiem")

			append("?sort=")
			append(
				when (order) {
					SortOrder.UPDATED -> "-updated_at"
					SortOrder.NEWEST -> "-created_at"
					SortOrder.POPULARITY -> "-views"
					SortOrder.ALPHABETICAL -> "name"
					SortOrder.ALPHABETICAL_DESC -> "-name"
					else -> "-updated_at"
				},
			)

			if (filter.states.isNotEmpty()) {
				append("&filter[status]=")
				filter.states.forEach {
					append(
						when (it) {
							MangaState.ONGOING -> "2,"
							MangaState.FINISHED -> "1,"
							else -> "2,1"
						},
					)
				}
			}

			if (filter.tags.isNotEmpty()) {
				append("&filter[accept_genres]=")
				append(filter.tags.joinTo(this, ",") { it.key })
			}

			if (!filter.query.isNullOrEmpty()) {
				append("&filter[name]=")
				append(filter.query.urlEncoded())
			}

			if (filter.tagsExclude.isNotEmpty()) {
				append("&filter[reject_genres]=")
				append(filter.tagsExclude.joinTo(this, ",") { it.key })
			}

			append("&page=$page")
		}

		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(
			"div.border.rounded-xl.border-gray-300.dark\\:border-dark-blue.bg-white.dark\\:bg-fire-blue"
		).map { element ->
			val mainA = element.selectFirstOrThrow("div.relative a")
			val href = mainA.attrAsRelativeUrl("href")
			val title = mainA.selectFirst("div.cover-frame img")?.attr("alt")
				?.takeIf { it.isNotBlank() }
				?: element.selectFirst("div.p-3 h3 a")?.text()?.takeIf { it.isNotBlank() }
				?: "Không có tiêu đề"
			val coverUrl = mainA.select("div.cover-frame img").attr("data-src").takeIf { it.isNotBlank() }
				?: mainA.select("div.cover-frame img").attr("src")

			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(url).parseHtml()

		val altTitles = doc.select("div.mt-2:contains(Tên khác:) span").mapNotNullToSet { it.textOrNull() }
		val allTags = availableTags.getOrNull().orEmpty()
		val tags = doc.select("div.mt-2:contains(Thể loại:) a").mapNotNullToSet { a ->
			val title = a.text().toTitleCase()
			allTags.find { x -> x.title == title }
		}

		val stateText = doc.selectFirst("div.mt-2:contains(Tình trạng:) span")?.text()
		val state = when (stateText) {
			"Đang tiến hành" -> MangaState.ONGOING
			else -> MangaState.FINISHED
		}

		val chapterListDiv = doc.selectFirst("ul#chapterList")
			?: throw ParseException("Chapters list not found!", url)

		val chapterLinks = chapterListDiv.select("a.block")
		val chapters = chapterLinks.mapChapters(reversed = true) { index, a ->
			val title = a.selectFirst("span.text-ellipsis")?.textOrNull()
			val href = a.attrAsRelativeUrl("href")
			val uploadDate = a.selectFirst("span.ml-2.whitespace-nowrap")?.text()

			MangaChapter(
				id = generateUid(href),
				title = title,
				number = index + 1f,
				volume = 0,
				url = href,
				scanlator = null,
				uploadDate = parseChapterDate(uploadDate),
				branch = null,
				source = source,
			)
		}

		return manga.copy(
			altTitles = altTitles,
			tags = tags,
			state = state,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
    val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()

    doc.selectFirst("script:containsData(window.encryptionConfig)")?.data()?.let { scriptContent ->
        val fallbackUrlsRegex = Regex(""""fallbackUrls"\s*:\s*(\[.*?])""")
        val arrayString = fallbackUrlsRegex.find(scriptContent)?.groupValues?.get(1) ?: return@let
        val urlRegex = Regex("""(https?:\\?/\\?[^"]+\.(?:jpg|jpeg|png|webp|gif))""")
        val scriptImages = urlRegex.findAll(arrayString).map {
            it.groupValues[1].replace("\\/", "/")
        }.toList()

        if (scriptImages.isNotEmpty()) {
            return scriptImages.map { url ->
                MangaPage(id = generateUid(url), url = url, preview = null, source = source)
            }
        }
    }

    val tagImagePages = doc.select("div#chapter-content img").mapNotNull { img ->
        val imageUrl = (img.attr("abs:src").takeIf { it.isNotBlank() }
            ?: img.attr("abs:data-src").takeIf { it.isNotBlank() })
            ?.trim()

        imageUrl?.let {
            MangaPage(id = generateUid(it), url = it, preview = null, source = source)
        }
    }

    if (tagImagePages.isNotEmpty()) {
        return tagImagePages
    }

    throw ParseException("Không tìm thấy bất kỳ nguồn ảnh nào (đã thử cả script và thẻ img).", chapter.url)
}

	private fun parseChapterDate(date: String?): Long {
		if (date == null) return 0
		return when {
			date.contains("giây trước") -> System.currentTimeMillis() - date.removeSuffix(" giây trước").toLong() * 1000
			date.contains("phút trước") -> System.currentTimeMillis() - date.removeSuffix(" phút trước")
				.toLong() * 60 * 1000

			date.contains("giờ trước") -> System.currentTimeMillis() - date.removeSuffix(" giờ trước")
				.toLong() * 60 * 60 * 1000

			date.contains("ngày trước") -> System.currentTimeMillis() - date.removeSuffix(" ngày trước")
				.toLong() * 24 * 60 * 60 * 1000

			date.contains("tuần trước") -> System.currentTimeMillis() - date.removeSuffix(" tuần trước")
				.toLong() * 7 * 24 * 60 * 60 * 1000

			date.contains("tháng trước") -> System.currentTimeMillis() - date.removeSuffix(" tháng trước")
				.toLong() * 30 * 24 * 60 * 60 * 1000

			date.contains("năm trước") -> System.currentTimeMillis() - date.removeSuffix(" năm trước")
				.toLong() * 365 * 24 * 60 * 60 * 1000

			else -> SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date)?.time ?: 0L
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem").parseHtml()
		val regex = Regex("toggleGenre\\('([0-9]+)'\\)")
		return doc.body().getElementsByAttribute("@click")
			.mapNotNullToSet { label ->
				// @click="toggleGenre('1')"
				val attr = label.attr("@click")
				val number = attr.findGroupValue(regex) ?: return@mapNotNullToSet null
				MangaTag(
					key = number,
					title = label.textOrNull()?.toTitleCase(sourceLocale) ?: return@mapNotNullToSet null,
					source = source,
				)
			}
	}
}
