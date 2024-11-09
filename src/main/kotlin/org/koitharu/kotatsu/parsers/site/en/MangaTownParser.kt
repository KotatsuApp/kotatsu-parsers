package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGATOWN", "MangaTown", "en")
internal class MangaTownParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MANGATOWN, 30) {

	override val configKeyDomain = ConfigKey.Domain("www.mangatown.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/search?name=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {
					append("/directory/")
					append("0-")

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
					} else {
						append("0")
					}
					append("-0-")

					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									MangaState.ONGOING -> "ongoing"
									MangaState.FINISHED -> "completed"
									else -> "0"
								},
							)
						}
					} else {
						append("0")
					}

					append("-0-0/")
					append(page.toString())
					append(".htm")

					append(
						when (order) {
							SortOrder.POPULARITY -> ""
							SortOrder.UPDATED -> "?last_chapter_time.za"
							SortOrder.ALPHABETICAL -> "?name.az"
							SortOrder.RATING -> "?rating.za"
							else -> "?last_chapter_time.za"
						},
					)
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().selectFirst("ul.manga_pic_list") ?: return emptyList()
		val manga = root.select("li")

		if (manga.isEmpty()) {
			return emptyList()
		}
		return manga.mapNotNull { li ->
			val a = li.selectFirst("a.manga_cover")
			val href = a?.attrAsRelativeUrlOrNull("href")
				?: return@mapNotNull null
			val views = li.select("p.view")
			val status = views.firstNotNullOfOrNull { it.ownText().takeIf { x -> x.startsWith("Status:") } }
				?.substringAfter(':')?.trim()?.lowercase(Locale.ROOT)
			Manga(
				id = generateUid(href),
				title = a.attr("title"),
				coverUrl = a.selectFirst("img")?.absUrl("src").orEmpty(),
				source = MangaParserSource.MANGATOWN,
				altTitle = null,
				rating = li.selectFirst("p.score")?.selectFirst("b")
					?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				author = views.firstNotNullOfOrNull { it.text().takeIf { x -> x.startsWith("Author:") } }
					?.substringAfter(':')
					?.trim(),
				state = when (status) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					else -> null
				},
				tags = li.selectFirst("p.keyWord")?.select("a")?.mapToSet tags@{ x ->
					MangaTag(
						title = x.attr("title").toTitleCase(),
						key = x.attr("href").substringAfter("/directory/0-").substringBefore("-0-"),
						source = MangaParserSource.MANGATOWN,
					)
				}.orEmpty(),
				url = href,
				isNsfw = false,
				publicUrl = href.toAbsoluteUrl(a.host ?: domain),
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirstOrThrow("section.main")
			.selectFirstOrThrow("div.article_content")
		val info = root.selectFirst("div.detail_info")?.selectFirst("ul")
		val chaptersList = root.selectFirst("div.chapter_content")
			?.selectFirst("ul.chapter_list")?.select("li")?.asReversed()
		val dateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.US)
		return manga.copy(
			tags = manga.tags + info?.select("li")?.find { x ->
				x.selectFirst("b")?.ownText() == "Genre(s):"
			}?.select("a")?.mapNotNull { a ->
				MangaTag(
					title = a.attr("title").toTitleCase(),
					key = a.attr("href").substringAfter("/directory/0-").substringBefore("-0-"),
					source = MangaParserSource.MANGATOWN,
				)
			}.orEmpty(),
			description = info?.getElementById("show")?.ownText(),
			chapters = chaptersList?.mapChapters { i, li ->
				val href = li.selectFirst("a")?.attrAsRelativeUrlOrNull("href")
					?: return@mapChapters null
				val name = li.select("span")
					.filter { x -> x.className().isEmpty() }
					.joinToString(" - ") { it.text() }.trim()
				MangaChapter(
					id = generateUid(href),
					url = href,
					source = MangaParserSource.MANGATOWN,
					number = i + 1f,
					volume = 0,
					uploadDate = parseChapterDate(
						dateFormat,
						li.selectFirst("span.time")?.text(),
					),
					name = name.ifEmpty { "${manga.title} - ${i + 1}" },
					scanlator = null,
					branch = null,
				)
			} ?: bypassLicensedChapters(manga),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.page_select")
		val isManga = root?.select("select")

		if (isManga.isNullOrEmpty()) {//Webtoon
			val imgElements = doc.select("div#viewer.read_img img.image")
			return imgElements.map {
				val href = it.attr("src")
				MangaPage(
					id = generateUid(href),
					url = href,
					preview = null,
					source = MangaParserSource.MANGATOWN,
				)

			}
		} else { //Manga
			return isManga.select("option").mapNotNull {
				val href = it.attrAsRelativeUrlOrNull("value")
				if (href == null || href.endsWith("featured.html")) {
					return@mapNotNull null
				}
				MangaPage(
					id = generateUid(href),
					url = href,
					preview = null,
					source = MangaParserSource.MANGATOWN,
				)
			}
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		if (page.url.startsWith("//")) {//Webtoon
			return page.url.toAbsoluteUrl(domain)
		}

		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.requireElementById("image").attrAsAbsoluteUrl("src")
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("/directory/".toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirst("aside.right")
			?.getElementsContainingOwnText("Genres")
			?.first()
			?.nextElementSibling() ?: doc.parseFailed("Root not found")
		return root.select("li").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val key = a.attr("href").substringAfter("/directory/0-").substringBefore("-0-")
			MangaTag(
				source = MangaParserSource.MANGATOWN,
				key = key,
				title = a.text().toTitleCase(),
			)
		}
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		return when {
			date.isNullOrEmpty() -> 0L
			date.contains("Today") -> Calendar.getInstance().timeInMillis
			date.contains("Yesterday") -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
			else -> dateFormat.tryParse(date)
		}
	}

	private suspend fun bypassLicensedChapters(manga: Manga): List<MangaChapter> {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(getDomain("m"))).parseHtml()
		val list = doc.body().selectFirst("ul.detail-ch-list") ?: return emptyList()
		val dateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.US)
		return list.select("li").asReversed().mapIndexedNotNull { i, li ->
			val a = li.selectFirst("a") ?: return@mapIndexedNotNull null
			val href = a.attrAsRelativeUrl("href")
			val name = a.selectFirst("span.vol")?.text().orEmpty().ifEmpty {
				a.ownText()
			}
			MangaChapter(
				id = generateUid(href),
				url = href,
				source = MangaParserSource.MANGATOWN,
				number = i + 1f,
				volume = 0,
				uploadDate = parseChapterDate(
					dateFormat,
					li.selectFirst("span.time")?.text(),
				),
				name = name.ifEmpty { "${manga.title} - ${i + 1}" },
				scanlator = null,
				branch = null,
			)
		}
	}
}
