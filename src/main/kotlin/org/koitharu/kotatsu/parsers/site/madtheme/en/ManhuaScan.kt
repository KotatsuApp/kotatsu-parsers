package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.removeSuffix
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("MANHUASCAN", "kaliscan.io", "en")
internal class ManhuaScan(context: MangaLoaderContext) :
	MadthemeParser(context, MangaParserSource.MANHUASCAN, "kaliscan.io") {

	override val selectDesc = ".summary .content, .summary .content ~ p"
	override val selectState = ".detail .meta > p > strong:contains(Status) ~ a"
	override val selectAlt = ".detail h2"
	override val selectTag = ".detail .meta > p > strong:contains(Genres) ~ a"
	override val selectDate = ".chapter-update"
	override val selectChapter = "#chapter-list > li, #chapter-list-inner .chapter-list > li"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isAuthorSearchSupported = true,
			isTagsExclusionSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = fetchAvailableTags(),
			availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/search")

			append("?page=")
			append(page.toString())

			filter.query?.let {
				append("&q=")
				append(it.urlEncoded())
			}

			append("&sort=")
			when (order) {
				SortOrder.POPULARITY -> append("views")
				SortOrder.UPDATED -> append("updated_at")
				SortOrder.ALPHABETICAL -> append("name")
				SortOrder.NEWEST -> append("created_at")
				SortOrder.RATING -> append("rating")
				else -> append("updated_at")
			}

			if (filter.tags.isNotEmpty()) {
				filter.tags.forEach { tag ->
					append("&include[]=")
					append(tag.key)
				}
			}

			if (filter.tagsExclude.isNotEmpty()) {
				filter.tagsExclude.forEach { tag ->
					append("&exclude[]=")
					append(tag.key)
				}
			}

			append("&include_mode=and")
			append("&bookmark=off")

			filter.states.oneOrThrowIfMany()?.let {
				append("&status=")
				append(
					when (it) {
						MangaState.ONGOING -> "ongoing"
						MangaState.FINISHED -> "completed"
						else -> "all"
					},
				)
			} ?: append("&status=all")

			filter.author?.takeIf { it.isNotBlank() }?.let { author ->
				append("&author=")
				append(author.urlEncoded())
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(".book-detailed-item").map { div ->
			val link = div.selectFirstOrThrow("a")
			val href = link.attrAsRelativeUrl("href")
			val title = link.attr("title").ifEmpty {
				div.selectFirst(".title")?.text() ?: ""
			}

			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = div.selectFirst("img")?.attr("data-src")?.ifEmpty {
					div.selectFirst("img")?.attr("src")
				},
				title = title,
				altTitles = emptySet(),
				rating = div.selectFirst("div.meta span.score")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		val title = doc.selectFirst(".detail h1")?.text() ?: manga.title

		val authors = doc.select(".detail .meta > p > strong:contains(Authors) ~ a")
			.map { it.text().trim(',', ' ') }
			.toSet()

		val tags = doc.select(selectTag).mapToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
				title = a.text().trim(',', ' ').toTitleCase(),
				source = source,
			)
		}

		val altNames = doc.selectFirst(selectAlt)?.text()
			?.split(',', ';')
			?.mapNotNull { it -> it.trim().takeIf { it != title } }
			?.toSet() ?: emptySet()

		val description = doc.select(selectDesc).text()
		val statusText = doc.selectFirst(selectState)?.text()?.lowercase() ?: ""
		val state = when (statusText) {
			"ongoing" -> MangaState.ONGOING
			"completed" -> MangaState.FINISHED
			else -> null
		}
		val nsfw = doc.selectFirst("#adt-warning") != null

		val coverUrl = doc.selectFirst("#cover img")?.attr("data-src")

		val chapters = doc.select(selectChapter).mapChapters(reversed = true)
		{ i, element ->
			val link = element.selectFirst("a") ?: return@mapChapters null
			val href = link.attrAsRelativeUrl("href")
			val chapterTitle = element.selectFirst(".chapter-title")?.text()?.trim() ?: return@mapChapters null
			val dateText = element.selectFirst(selectDate)?.text()?.trim()

			MangaChapter(
				id = generateUid(href),
				url = href,
				title = chapterTitle,
				uploadDate = parseChapterDate(
					SimpleDateFormat(datePattern, sourceLocale),
					dateText,
				),
				source = source,
				number = i + 1f,
				volume = 0,
				scanlator = null,
				branch = null,
			)
		}

		return manga.copy(
			title = title,
			altTitles = altNames,
			authors = authors,
			tags = tags,
			description = description,
			state = state,
			largeCoverUrl = coverUrl,
			chapters = chapters,
			contentRating = if (nsfw || isNsfwSource) ContentRating.ADULT else ContentRating.SAFE,
		)
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/search").parseHtml()

		return doc.selectFirst(".checkbox-group.genres")?.select(".checkbox-wrapper")?.mapNotNullToSet { element ->
			val input = element.selectFirst("input") ?: return@mapNotNullToSet null
			val key = input.attr("value").takeIf { it.isNotEmpty() } ?: return@mapNotNullToSet null
			val label = element.selectFirst(".radio__label")?.text() ?: key

			MangaTag(
				key = key,
				title = label,
				source = source,
			)
		} ?: emptySet()
	}
}
