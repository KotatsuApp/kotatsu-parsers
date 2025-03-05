package org.koitharu.kotatsu.parsers.site.mangabox.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.Include
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.Match
import org.koitharu.kotatsu.parsers.model.search.SearchCapability
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAKAKALOT", "Mangakakalot.gg", "en")
internal class Mangakakalot(context: MangaLoaderContext) : MangaboxParser(context, MangaParserSource.MANGAKAKALOT) {
		
	override val configKeyDomain = ConfigKey.Domain(
		"www.mangakakalot.gg",
		"mangakakalot.gg",
	)
	
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities(
			SearchCapability(
				field = TAG,
				criteriaTypes = setOf(Include::class),
				isMultiple = false,
			),
			SearchCapability(
				field = TITLE_NAME,
				criteriaTypes = setOf(Match::class),
				isMultiple = false,
				isExclusive = true,
			),
			SearchCapability(
				field = STATE,
				criteriaTypes = setOf(Include::class),
				isMultiple = false,
			),
		)

	override val otherDomain = "chapmanganato.com"
	override val listUrl = "/genre/all"

	private fun SearchableField.toParamName(): String = when (this) {
		TAG -> "category"
		STATE -> "state"
		else -> ""
	}

	private fun Any?.toQueryParam(): String = when (this) {
		is String -> {
			sanitizeTitleNameRegex.replace(this, "")
				.replace(" ", "_")
				.urlEncoded()
		}

		is MangaTag -> key
		is MangaState -> when (this) {
			MangaState.ONGOING -> "ongoing"
			MangaState.FINISHED -> "completed"
			else -> "all"
		}

		is SortOrder -> when (this) {
			SortOrder.POPULARITY -> "topview"
			SortOrder.UPDATED -> "latest"
			SortOrder.NEWEST -> "newest"
			else -> ""
		}

		else -> this.toString().replace(" ", "_").urlEncoded()
	}

	private fun StringBuilder.appendCriterion(field: SearchableField, value: Any?, paramName: String? = null) {
		val param = paramName ?: field.toParamName()
		if (param.isNotBlank()) {
			append("&$param=")
			append(value.toQueryParam())
		}
	}

	private val sanitizeTitleNameRegex by lazy {
		Regex("[^A-Za-z0-9 ]")
	}

	override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
		var titleSearchUrl: String? = null
		val url = buildString {
			val pageQueryParameter = "page=$page"
			append("https://$domain/genre/all?")

			query.criteria.forEach { criterion ->
				when (criterion) {
					is Include<*> -> {
						criterion.field.toParamName().takeIf { it.isNotBlank() }?.let { param ->
							append("&$param=${criterion.values.first().toQueryParam()}")
						}
					}

					is Match<*> -> {
						if (criterion.field == TITLE_NAME) {
							criterion.value.toQueryParam().takeIf { it.isNotBlank() }?.let { titleName ->
								titleSearchUrl = "https://${domain}${searchUrl}${titleName}/" +
									"?$pageQueryParameter"
							}
						}
						appendCriterion(criterion.field, criterion.value)
					}

					else -> {
						// Not supported
					}
				}
			}

			append("&$pageQueryParameter")
			append("&type=${(query.order ?: defaultSortOrder).toQueryParam()}")
		}

		val doc = webClient.httpGet(titleSearchUrl ?: url).parseHtml()

		return doc.select("div.list-truyen-item-wrap").ifEmpty {
			doc.select("div.story_item")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src(),
				title = div.selectFirst("h3")?.text().orEmpty(),
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = null,
			)
		}
	}

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		val tags = doc.select("td a[href*='/genre/']").drop(1)
		val uniqueTags = mutableSetOf<MangaTag>()
		for (a in tags) {
			val key = a.attr("href").substringAfter("/genre/").substringBefore("?")
			val name = a.text().replaceFirstChar { it.uppercaseChar() }
			if (uniqueTags.none { it.key == key }) {
				uniqueTags.add(
					MangaTag(
						key = key,
						title = name,
						source = source,
					)
				)
			}
		}

		return uniqueTags
	}

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.select(selectDate).last()?.text() ?: "0"
			val dateFormat = if (dateText.contains("-")) {
				SimpleDateFormat("MMM-dd-yy", sourceLocale)
			} else {
				SimpleDateFormat(datePattern, sourceLocale)
			}

			MangaChapter(
				id = generateUid(href),
				title = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}
