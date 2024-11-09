package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGAKAWAII", "MangaKawaii Fr", "fr")
internal class MangaKawaii(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MANGAKAWAII, 50) {

	override val configKeyDomain = ConfigKey.Domain("www.mangakawaii.io")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.ALPHABETICAL)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("Accept-Language", "fr")
		.build()

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/search?query=")
					append(filter.query.urlEncoded())
					append("&search_type=manga&page=")
					append(page)
				}

				else -> {

					if (order == SortOrder.UPDATED && filter.tags.isNotEmpty()) {
						throw IllegalArgumentException("Filtrer par tag n'est pas  avec le tri pas mis à jour")
					}

					if (order == SortOrder.ALPHABETICAL) {
						append("/manga-list")
						filter.tags.oneOrThrowIfMany()?.let {
							append("/category/")
							append(it.key)
						}
					}

					if (page > 1) {
						return emptyList()
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("li.section__list-group-item").ifEmpty {
			doc.select("div.media-thumbnail")
		}.map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = (div.selectFirst("img")?.src() ?: a.attr("data-bg")).orEmpty(),
				title = div.selectFirst("h4, .media-thumbnail__name")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}


	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val firstChapter = doc.selectFirst("tr[class*='volume-'] a")?.attr("href")
		val chaptersDeferred = async { loadChapters(firstChapter) }
		manga.copy(
			description = doc.selectFirst("dd.text-justify.text-break")?.text().orEmpty(),
			altTitle = doc.select("span[itemprop*=alternativeHeadline]").joinToString { ", " },
			author = doc.select("a[href*=author]").text(),
			state = when (doc.selectFirst("span.badge.bg-success.text-uppercase")?.text()) {
				"En Cours" -> MangaState.ONGOING
				"Terminé" -> MangaState.FINISHED
				else -> null
			},
			tags = doc.select("a[href*=category]").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			chapters = chaptersDeferred.await(),
		)
	}

	private suspend fun loadChapters(chapterUrl: String?): List<MangaChapter> {
		if (chapterUrl.isNullOrEmpty()) {
			return emptyList()
		}

		val doc = webClient.httpGet(chapterUrl.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("#dropdownMenuOffset+ul li")
			.mapChapters(reversed = true) { i, li ->
				val a = li.selectFirstOrThrow("a")
				val url = a.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = a.text(),
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chapterSlug = Regex("""var chapter_slug = "([^"]*)";""").find(doc.toString())?.groupValues?.get(1)
		val mangaSlug = Regex("""var oeuvre_slug = "([^"]*)";""").find(doc.toString())?.groupValues?.get(1)
		val cdn = Regex("""var chapter_server = "([^"]*)";""").find(doc.toString())?.groupValues?.get(1)
		val cdnDomain = cdn + domain.removePrefix("www")
		return Regex(""""page_image":"([^"]*)"""").findAll(doc.toString()).asIterable().map {
			val url =
				"https://" + cdnDomain + "/uploads/manga/" + mangaSlug + "/chapters_fr/" + chapterSlug + "/" + it.groupValues[1]
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/manga-list/").parseHtml()
		return doc.select("ul li a.category").mapToSet { a ->
			val name = a.text()
			val key = name.lowercase().replace(" ", "-").replace("é", "e").replace("è", "e")
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}
}
