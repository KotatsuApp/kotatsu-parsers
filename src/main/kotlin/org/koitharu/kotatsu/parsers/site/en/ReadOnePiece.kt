package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacySinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("READONEPIECE", "ReadOnePiece", "en")
internal class ReadOnePiece(context: MangaLoaderContext) :
	LegacySinglePageMangaParser(context, MangaParserSource.READONEPIECE) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("ww11.readonepiece.com")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.RELEVANCE)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(isSearchSupported = false)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		val root = doc.body().selectFirstOrThrow("nav ul")
		val manga = root.select("li")
		
		return manga.mapNotNull { li ->
			val a = li.selectFirst("a") ?: return@mapNotNull null
			val href = a.attrAsRelativeUrlOrNull("href").takeIf { ref -> ref != "/" }
				?: return@mapNotNull null

			Manga(
				id = generateUid(href),
				title = a.text(),
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(a.host ?: domain),
				rating = RATING_UNKNOWN,
				contentRating = null,
				coverUrl = null,
				state = null,
				tags = emptySet(),
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirstOrThrow("div>div.container.px-3")
		val content = root.selectFirstOrThrow("div")
		val info = content.selectFirstOrThrow("div.w-full.mb-3")
		val chapterList = content.selectFirstOrThrow("div.w-full:not(.mb-3)")

		val cover = info.selectFirst("img")
		val description = info.selectFirst("div.text-text-muted")
		val chapters = chapterList.selectOrThrow("div.bg-bg-secondary.p-3").asReversed()

		return manga.copy(
			coverUrl = cover?.attrAsAbsoluteUrlOrNull("src"),
			description = description?.textOrNull(),
			chapters = chapters.mapNotNull { div ->
				val a = div.selectFirst("a")
				val href = a?.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
				val chapterTitle = div.selectFirst("div.text-xs")
				val number = a.text().substringAfterLast(" ").toFloatOrNull()

				MangaChapter(
					id = generateUid(href),
					title = chapterTitle?.text(),
					number = number ?: 0f,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val content = doc.body().selectFirstOrThrow("div#top>div.my-3>div.js-pages-container")
		val pages = content.select("div.text-center img")

		return pages.mapNotNull { img ->
			val url = img.attrAsRelativeUrlOrNull("src") ?: return@mapNotNull null
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
