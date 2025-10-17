package org.koitharu.kotatsu.parsers.site.id

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DOUJINDESU", "DoujinDesu.tv", "id")
internal class DoujinDesuParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.DOUJINDESU, pageSize = 18) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("doujindesu.tv")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.NEWEST, SortOrder.ALPHABETICAL, SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
            isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.DOUJINSHI,
		),
	)

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("X-Requested-With", "XMLHttpRequest")
		.add("Referer", "https://$domain/")
		.build()

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = urlBuilder().apply {
            when {
                page > 1 -> addPathSegments("manga/page/$page/")
                else -> addPathSegment("manga/")
            }

            addQueryParameter(
				"title",
				filter.query?.let {
					filter.query
				},
			)

            addQueryParameter(
                name = "author",
                value = filter.author?.let { it
                    space2plus(it).lowercase()
                }
            )

			addQueryParameter(
				"order",
				when (order) {
					SortOrder.UPDATED -> "update"
					SortOrder.POPULARITY -> "popular"
					SortOrder.ALPHABETICAL -> "title"
					SortOrder.NEWEST -> "latest"
					else -> "latest"
				},
			)

			filter.tags.forEach {
				addEncodedQueryParameter("genre[]".urlEncoded(), it.key.urlEncoded())
			}

			filter.states.oneOrThrowIfMany()?.let {
				addEncodedQueryParameter(
					"statusx",
					when (it) {
						MangaState.ONGOING -> "Publishing"
						MangaState.FINISHED -> "Finished"
						else -> ""
					},
				)
			}

			filter.types.oneOrThrowIfMany()?.let {
				addQueryParameter(
					"typex",
					when (it) {
						ContentType.MANGA -> "Manga"
						ContentType.MANHWA -> "Manhwa"
						ContentType.DOUJINSHI -> "Doujinshi"
						else -> ""
					},
				)
			}
		}.build()

		return webClient.httpGet(url).parseHtml()
			.requireElementById("archives")
			.selectFirstOrThrow("div.entries")
			.select(".entry")
			.mapNotNull {
				val href = it.selectFirst(".metadata > a")?.attr("href") ?: return@mapNotNull null
				Manga(
					id = generateUid(href),
					title = it.selectFirst(".metadata > a")?.attr("title").orEmpty(),
					altTitles = emptySet(),
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					contentRating = ContentRating.ADULT,
					coverUrl = it.selectFirst(".thumbnail > img")?.src(),
					tags = emptySet(),
					state = null,
					authors = emptySet(),
					largeCoverUrl = null,
					description = null,
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().selectFirstOrThrow("#archive")
		val chapterDateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", sourceLocale)
		val metadataEl = docs.selectFirst(".wrapper > .metadata tbody")
		val state = when (metadataEl?.selectFirst("tr:contains(Status)")?.selectLast("td")?.text()) {
			"Finished" -> MangaState.FINISHED
			"Publishing" -> MangaState.ONGOING
			else -> null
		}
		val author = metadataEl?.selectFirst("tr:contains(Author)")?.selectLast("td")?.text()
		return manga.copy(
			authors = setOfNotNull(author),
			description = docs.selectFirst(".wrapper > .metadata > .pb-2")?.selectFirst("p")?.html(),
			state = state,
			rating = metadataEl?.selectFirst(".rating-prc")?.ownText()?.toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
			tags = docs.select(".tags > a").mapToSet {
				MangaTag(
					key = it.attr("title"),
					title = it.text(),
					source = source,
				)
			},
			chapters = docs.requireElementById("chapter_list")
				.select("ul > li")
				.mapChapters(reversed = true) { index, element ->
					val titleTag = element.selectFirstOrThrow(".epsleft > .lchx > a")
					val url = titleTag.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(url),
						title = titleTag.text(),
						number = index + 1f,
						volume = 0,
						url = url,
						scanlator = null,
						uploadDate = chapterDateFormat.parseSafe(element.select(".epsleft > .date").text()),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val id = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
			.requireElementById("reader")
			.attr("data-id")
		return webClient.httpPost("/themes/ajax/ch.php".toAbsoluteUrl(domain), "id=$id").parseHtml()
			.select("img")
			.map {
				val url = it.attrAsRelativeUrl("src")
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		return webClient.httpGet("/genre/".toAbsoluteUrl(domain)).parseHtml()
			.requireElementById("taxonomy")
			.selectFirstOrThrow(".entries")
			.select(".entry > a")
			.mapToSet {
				MangaTag(
					key = it.attr("title"),
					title = it.attr("title"),
					source = source,
				)
			}
	}

    private fun space2plus(input: String): String = input.replace(' ', '+')
}
