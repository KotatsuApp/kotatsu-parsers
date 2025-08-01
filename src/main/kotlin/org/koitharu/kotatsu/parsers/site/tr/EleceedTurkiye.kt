package org.koitharu.kotatsu.parsers.site.tr

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("ELECEEDTURKIYE", "Eleceed Türkiye", "tr")
internal class EleceedTurkiye(context: MangaLoaderContext) :
	SinglePageMangaParser(context, MangaParserSource.ELECEEDTURKIYE) {

	override val configKeyDomain = ConfigKey.Domain(
		"eleceedturkiye.com",
		"www.eleceedturkiye.com",
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val raw = webClient.httpGet("https://$domain").parseHtml()
		val coverUrl = raw.select("div.thumb img").attr("src")
		return listOf(
			Manga(
				id = generateUid(mangaId),
				url = "$domain/",
				publicUrl = "https://$domain/",
				coverUrl = coverUrl,
				title = "Eleceed",
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = setOf("Son Jae Ho", "ZHENA"),
				state = MangaState.ONGOING,
				source = source,
				contentRating = ContentRating.SAFE,
			),
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet("https://${manga.url}").parseHtml()
		val description = doc.selectFirst("div.entry-content.entry-content-single")?.html()
		val tags = doc.select("span.mgen a").mapToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast("/").removeSuffix("/"),
				title = a.text(),
				source = source,
			)
		}
		val chapters = doc.select("div.eplister ul li").mapChapters(reversed = true) { _, li ->
			val chapterDiv = li.selectFirstOrThrow("div.eph-num")
			val link = chapterDiv.selectFirstOrThrow("a")
			val href = link.attr("href")

			val chapterTitle = link.selectFirst("span.chapternum")?.text() ?: ""
			val chapterNumber = chapterTitle.substringAfter("Bölüm ").toFloatOrNull() ?: 0f

			MangaChapter(
				id = generateUid(href),
				title = chapterTitle,
				number = chapterNumber,
				url = href,
				uploadDate = dateFormat.parseSafe(li.selectFirst("span.chapterdate")?.text()),
				scanlator = null,
				branch = null,
				source = source,
				volume = 0,
			)
		}

		return manga.copy(
			description = description,
			tags = tags,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url).parseHtml()
		val scriptData = doc.select("script")
			.firstOrNull { it.data().contains("ts_reader.run(") }
			?.data()
			?.substringAfter("ts_reader.run(")
			?.substringBeforeLast(");")
			?: error("Script data not found")

		val json = JSONObject(scriptData)
		val sources = json.getJSONArray("sources")
		if (sources.length() == 0) error("No sources found")

		val firstSource = sources.getJSONObject(0)
		val images = firstSource.getJSONArray("images")

		return List(images.length()) { index ->
			val imageUrl = images.getString(index)
			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl.toAbsoluteUrl(domain),
				preview = null,
				source = source,
			)
		}
	}

	private val mangaId = "eleceed"
	private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr"))
}
