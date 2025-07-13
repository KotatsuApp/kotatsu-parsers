package org.koitharu.kotatsu.parsers.site.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MUITOHENTAI", "MuitoHentai", "pt", ContentType.HENTAI)
internal class MuitoHentai(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.MUITOHENTAI, 24) {

	override val configKeyDomain = ConfigKey.Domain("www.muitohentai.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {

				!filter.query.isNullOrEmpty() -> {
					if (page > 1) return emptyList()
					append("/buscar-manga/?q=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append("/mangas")

					filter.tags.oneOrThrowIfMany()?.let {
						append("/genero/")
						append(it.key)
					}

					append('/')
					append(page.toString())
					append('/')
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.requireElementById("archive-content").select("article").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsAbsoluteUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				title = div.selectLastOrThrow("h3").text(),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = null,
				state = null,
				authors = emptySet(),
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/generos-dos-mangas/").parseHtml()
		return doc.select("div.content a.profileSideBar").mapToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix("/").substringAfterLast("/"),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			description = doc.selectFirstOrThrow(".backgroundpost:contains(Sinopse)").html(),
			tags = doc.select("a.genero_btn").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast("/"),
					title = a.text(),
					source = source,
				)
			},
			chapters = doc.select(".backgroundpost h3 a").mapChapters { i, a ->
				val href = a.attrAsAbsoluteUrl("href")
				MangaChapter(
					id = generateUid(href),
					title = a.text(),
					number = i + 1f,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val script = doc.selectFirstOrThrow("script:containsData(var arr =)").data()
		val src = script.substringAfter("var arr = [").substringBefore("];").replace("\"", "").split(",")
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.map { url ->
				if (url.startsWith("https://$domain/")) {
					url.substringAfter("$domain/")
				} else url
			}

		return src.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url.takeUnless { it.startsWith("https://") }?.let { "https://$domain/$it" } ?: url,
				preview = null,
				source = source,
			)
		}
	}
}
