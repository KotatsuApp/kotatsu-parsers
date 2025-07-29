package org.koitharu.kotatsu.parsers.site.tr

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

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
				url = "https://$domain/",
				publicUrl = "https://$domain/",
				coverUrl = coverUrl,
				title = "Eleceed",
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = setOf("Son Jae Ho", "ZHENA"),
				state = MangaState.ONGOING,
				source = source,
				contentRating = ContentRating.ADULT,
			),
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url).parseHtml()
		return manga.copy(
			description = doc.selectFirst("div.entry-content")?.html(),
			tags = setOf(
				MangaTag("Aksiyon", "", source),
				MangaTag("Drama", "", source),
				MangaTag("Komedi", "", source),
				MangaTag("Süper Güçler", "", source),
			),
			chapters = doc.select("div.eph-num").mapChapters(reversed = true) { _, div ->
				val href = div.selectFirstOrThrow("a").attr("href")
				val title = div.selectFirst("span.chapternum")?.text() ?: "Not found"
				MangaChapter(
					id = generateUid(href),
					title = title,
					number = title?.let { Regex("\\d+").find(it)?.value?.toFloatOrNull() } ?: 0f,
					url = href,
					uploadDate = dateFormat.parseSafe(div.selectFirst("span.chapterdate")?.text()),
					scanlator = null,
					branch = null,
					source = source,
					volume = 0,
				)
			},
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
