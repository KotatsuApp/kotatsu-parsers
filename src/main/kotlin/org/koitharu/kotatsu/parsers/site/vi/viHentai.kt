package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.arraySetOf
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("VIHENTAI", "viHentai", "vi", type = ContentType.HENTAI)
internal class viHentai(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.VIHENTAI, 60) {

	override val configKeyDomain = ConfigKey.Domain("vi-hentai.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = availableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			when {

				!filter.query.isNullOrEmpty() -> {
					append("/tim-kiem")
					append("?filter[name]=")
					append(filter.query.urlEncoded())

					if (page > 1) {
						append("&page=")
						append(page)
					}

					append("&sort=")
					append(
						when (order) {
							SortOrder.POPULARITY -> "-views"
							SortOrder.UPDATED -> "-updated_at"
							SortOrder.NEWEST -> "-created_at"
							SortOrder.ALPHABETICAL -> "name"
							SortOrder.ALPHABETICAL_DESC -> "-name"
							else -> "-updated_at"
						},
					)
				}

				filter.tags.isNotEmpty() -> {
					val tag = filter.tags.first()
					append("/the-loai/")
					append(tag.key)

					append("?page=")
					append(page)
				}

				else -> {
					append("/danh-sach")
					append("?sort=")
					append(
						when (order) {
							SortOrder.POPULARITY -> "-views"
							SortOrder.UPDATED -> "-updated_at"
							SortOrder.NEWEST -> "-created_at"
							SortOrder.ALPHABETICAL -> "name"
							SortOrder.ALPHABETICAL_DESC -> "-name"
							else -> "-updated_at"
						},
					)
					append("&page=")
					append(page)
				}
			}

			if (filter.query.isNullOrEmpty()) {
				append("&sort=")
				when (order) {
					SortOrder.POPULARITY -> append("-views")
					SortOrder.UPDATED -> append("-updated_at")
					SortOrder.NEWEST -> append("-created_at")
					SortOrder.ALPHABETICAL -> append("name")
					SortOrder.ALPHABETICAL_DESC -> append("-name")
					else -> append("-updated_at")
				}
			}

			if (filter.states.isNotEmpty()) {
				append("&filter[status]=")
				filter.states.forEach {
					append(
						when (it) {
							MangaState.ONGOING -> "2,"
							MangaState.FINISHED -> "1,"
							else -> "1,2"
						},
					)
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.grid div.relative").map { div ->
			val href = div.selectFirst("a[href^=/truyen/]")?.attrOrNull("href")
				?: div.parseFailed("Không thể tìm thấy nguồn ảnh của Manga này!")
			val coverUrl = div.selectFirst("div.cover")?.attr("style")
				?.substringAfter("url('")?.substringBefore("')")

			Manga(
				id = generateUid(href),
				title = div.select("div.p-2 a.text-ellipsis").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = coverUrl.orEmpty(),
				tags = setOf(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		return manga.copy(
			altTitle = root.selectLast("div.grow div:contains(Tên khác) span")?.textOrNull(),
			state = when (root.selectFirst("div.mt-2:contains(Tình trạng) span.text-blue-500")?.text()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Đã hoàn thành" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("div.mt-2:contains(Thể loại) a.bg-gray-500").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			author = root.selectFirst("div.mt-2:contains(Tác giả) span a")?.textOrNull(),
			description = root.selectFirst("meta[name=description]")?.attrOrNull("content"),
			chapters = root.select("div.justify-between ul.overflow-y-auto.overflow-x-hidden a")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirst("span.text-ellipsis")?.text().orEmpty()
					val dateText = a.parent()?.selectFirst("span.timeago")?.attr("datetime").orEmpty()
					val scanlator = root.selectFirst("div.mt-2:contains(Nhóm dịch) span a")?.textOrNull()

					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i.toFloat(),
						volume = 0,
						url = href,
						scanlator = scanlator,
						uploadDate = parseDateTime(dateText),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.text-center img.lazy").mapNotNull { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseDateTime(dateStr: String): Long = runCatching {
		val parts = dateStr.split(' ')
		val dateParts = parts[0].split('-')
		val timeParts = parts[1].split(':')

		val calendar = Calendar.getInstance()
		calendar.set(
			dateParts[0].toInt(),
			dateParts[1].toInt() - 1,
			dateParts[2].toInt(),
			timeParts[0].toInt(),
			timeParts[1].toInt(),
			timeParts[2].toInt(),
		)
		calendar.timeInMillis
	}.getOrDefault(0L)

    private suspend fun availableTags(): Set<MangaTag> {
        val doc = webClient.httpGet("https://$domain").parseHtml()
        return doc.select("ul.grid.grid-cols-2 a").mapToSet { a ->
            MangaTag(
                key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
                title = a.text(),
                source = source,
            )
        }
    }
}
