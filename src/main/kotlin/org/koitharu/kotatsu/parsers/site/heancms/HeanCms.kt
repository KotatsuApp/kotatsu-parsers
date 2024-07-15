package org.koitharu.kotatsu.parsers.site.heancms

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

internal abstract class HeanCms(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	private val userAgentKey = ConfigKey.UserAgent(context.getDefaultUserAgent())

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

	override val availableStates: Set<MangaState> =
		EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED)


	protected open val pathManga = "series"
	protected open val apiPath
		get() = getDomain("api")

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(apiPath)
			append("/query?query_string=")
			when (filter) {
				is MangaListFilter.Search -> {
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					filter.states.oneOrThrowIfMany()?.let {
						append("&series_status=")
						append(
							when (it) {
								MangaState.ONGOING -> "Ongoing"
								MangaState.FINISHED -> "Completed"
								MangaState.ABANDONED -> "Dropped"
								MangaState.PAUSED -> "Hiatus"
								else -> ""
							},
						)

					}
					append("&orderBy=")
					when (filter.sortOrder) {
						SortOrder.POPULARITY -> append("total_views&order=desc")
						SortOrder.UPDATED -> append("latest&order=desc")
						SortOrder.NEWEST -> append("created_at&order=desc")
						SortOrder.ALPHABETICAL -> append("title&order=desc")
						SortOrder.ALPHABETICAL_DESC -> append("title&order=asc")
						else -> append("latest&order=desc")
					}
					append("&series_type=Comic&perPage=12")
					append("&tags_ids=")
					append("[".urlEncoded())
					append(filter.tags.joinToString(",") { it.key })
					append("]".urlEncoded())

				}

				null -> {}
			}
			append("&page=")
			append(page.toString())
		}
		val json = webClient.httpGet(url).parseJson()

		return json.getJSONArray("data").mapJSON { j ->
			val slug = j.getString("series_slug")
			val urlManga = "https://$domain/$pathManga/$slug"
			val cover = if (j.getString("thumbnail").contains('/')) {
				j.getString("thumbnail")
			} else {
				"https://api.$domain/${j.getString("thumbnail")}"
			}
			Manga(
				id = generateUid(urlManga),
				title = j.getString("title"),
				altTitle = null,
				url = urlManga.toRelativeUrl(domain),
				publicUrl = urlManga,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = cover,
				tags = setOf(),
				state = when (j.getString("status")) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Dropped" -> MangaState.ABANDONED
					"Hiatus" -> MangaState.PAUSED
					else -> null
				},
				author = null,
				source = source,
			)
		}

	}


	protected open val datePattern = "yyyy-MM-dd"
	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, Locale.ENGLISH)

		val slug = manga.url.substringAfterLast('/')
		val chapter = root.selectFirstOrThrow("script:containsData(chapter_slug)").data()
			.replace("\\", "")
			.substringAfter("\"seasons\":")
			.substringBefore("}]}],\"children\"")
			.split("chapter_name")
			.drop(1)

		return manga.copy(
			altTitle = root.selectFirst("p.text-center.text-gray-400")?.text(),
			tags = emptySet(),
			author = root.select("div.flex.flex-col.gap-y-2 p:contains(Autor:) strong").text(),
			description = root.selectFirst("h5:contains(Desc) + .bg-gray-800")?.html(),
			chapters = chapter.mapChapters(reversed = true) { i, it ->
				val slugChapter = it.substringAfter("chapter_slug\":\"").substringBefore("\",\"")
				val url = "https://$domain/$pathManga/$slug/$slugChapter"
				val date = it.substringAfter("created_at\":\"").substringBefore("\",\"").substringBefore("T")
				val name = slugChapter.replace("-", " ")
				MangaChapter(
					id = generateUid(url),
					name = name,
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(date),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".flex > img:not([alt])").map { img ->
			val url = img.src() ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comics").parseHtml()

		val tags = doc.selectFirstOrThrow("script:containsData(Genres)").data()
			.replace("\\", "")
			.substringAfterLast("\"Genres\"")
			.split("\",{\"")
			.drop(1)

		return tags.mapNotNullToSet {
			MangaTag(
				key = it.substringAfter("id\":").substringBefore(",\""),
				title = it.substringAfter("name\":\"").substringBefore("\"}]").toTitleCase(sourceLocale),
				source = source,
			)
		}
	}
}
