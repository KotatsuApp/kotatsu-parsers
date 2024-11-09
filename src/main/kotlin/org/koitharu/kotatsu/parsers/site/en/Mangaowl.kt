package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@Broken
@MangaSourceParser("MANGAOWL", "MangaOwl.to", "en")
internal class Mangaowl(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MANGAOWL, pageSize = 24) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.UPDATED,
		SortOrder.RATING,
	)

	override val configKeyDomain = ConfigKey.Domain("mangaowl.to")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/10-search?q=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				else -> {

					append("/10-comics")
					append("?page=")
					append(page.toString())

					filter.tags.forEach { tag ->
						append("&genres=")
						append(tag.key)
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "completed"
								else -> ""
							},
						)
					}

					append("&ordering=")
					append(
						when (order) {
							SortOrder.POPULARITY -> "view_count"
							SortOrder.UPDATED -> "-modified_at"
							SortOrder.NEWEST -> "created_at"
							SortOrder.RATING -> "rating"
							else -> "modified_at"
						},
					)
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.manga-item.column").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("a.one-line")?.text().orEmpty(),
				altTitle = null,
				rating = div.select("span").last()?.text()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = false,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/10-genres").parseHtml()
		return doc.select("div.genres-container span.genre-item a").mapToSet { a ->
			val key = a.attr("href").removeSuffix('/').substringAfterLast('/').substringBefore('-')
			MangaTag(
				key = key,
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		manga.copy(
			tags = doc.body().select("div.comic-attrs div.column.my-2:contains(Genres) a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/').substringBefore('-'),
					title = a.text().toTitleCase().replace(",", ""),
					source = source,
				)
			},
			description = doc.select("span.story-desc").html(),
			state = when (doc.select("div.section-status:contains(Status) span").last()?.text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			},
			chapters = getChapters(manga.url, doc),
		)
	}

	private fun getChapters(mangaUrl: String, doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", sourceLocale)
		val script = doc.selectFirstOrThrow("script:containsData(chapters:)")
		val json = script.data().substringAfter("chapters:[")
			.substringBeforeLast(')')
			.substringBefore("],latest_chapter:")
			.split("},")
		val slug = mangaUrl.substringAfterLast("/")
		return json.mapChapters { i, t ->
			if (t.contains("Chapter")) {
				val id = t.substringAfter("id:").substringBefore(",created_at")
				val url = "/reading/$slug/$id"
				val date = t.substringAfter("created_at:\"").substringBefore('"')
				val name = t.substringAfter("name:\"").substringBefore('"')
				MangaChapter(
					id = generateUid(url),
					name = name,
					number = i + 1f,
					volume = 0,
					url = url,
					uploadDate = dateFormat.tryParse(date),
					source = source,
					scanlator = null,
					branch = null,
				)
			} else {
				null
			}
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val id = chapter.url.substringAfterLast("/")
		val json = webClient.httpGet("https://api.mangaowl.to/v1/chapters/$id/images?page_size=100").parseJson()
		return json.getJSONArray("results").mapJSON { jo ->
			MangaPage(
				id = generateUid(jo.getString("image")),
				preview = null,
				source = chapter.source,
				url = jo.getString("image"),
			)
		}
	}
}
