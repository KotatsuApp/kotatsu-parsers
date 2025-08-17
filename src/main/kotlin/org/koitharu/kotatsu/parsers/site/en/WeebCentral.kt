package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.AbstractMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating.SAFE
import org.koitharu.kotatsu.parsers.model.ContentRating.SUGGESTIVE
import org.koitharu.kotatsu.parsers.model.ContentType.COMICS
import org.koitharu.kotatsu.parsers.model.ContentType.MANGA
import org.koitharu.kotatsu.parsers.model.ContentType.MANHUA
import org.koitharu.kotatsu.parsers.model.ContentType.MANHWA
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState.ABANDONED
import org.koitharu.kotatsu.parsers.model.MangaState.FINISHED
import org.koitharu.kotatsu.parsers.model.MangaState.ONGOING
import org.koitharu.kotatsu.parsers.model.MangaState.PAUSED
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.SortOrder.ADDED
import org.koitharu.kotatsu.parsers.model.SortOrder.ADDED_ASC
import org.koitharu.kotatsu.parsers.model.SortOrder.ALPHABETICAL
import org.koitharu.kotatsu.parsers.model.SortOrder.ALPHABETICAL_DESC
import org.koitharu.kotatsu.parsers.model.SortOrder.POPULARITY
import org.koitharu.kotatsu.parsers.model.SortOrder.POPULARITY_ASC
import org.koitharu.kotatsu.parsers.model.SortOrder.RATING
import org.koitharu.kotatsu.parsers.model.SortOrder.RATING_ASC
import org.koitharu.kotatsu.parsers.model.SortOrder.RELEVANCE
import org.koitharu.kotatsu.parsers.model.SortOrder.UPDATED
import org.koitharu.kotatsu.parsers.model.SortOrder.UPDATED_ASC
import org.koitharu.kotatsu.parsers.util.LinkResolver
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.getCookies
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("WEEBCENTRAL", "Weeb Central", "en")
internal class WeebCentral(context: MangaLoaderContext) : AbstractMangaParser(context, MangaParserSource.WEEBCENTRAL),
	MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain("weebcentral.com")

	override val authUrl: String
		get() = "https://$domain"

	override suspend fun isAuthorized(): Boolean =
		context.cookieJar.getCookies(domain).any { it.name == "access_token" }

	override suspend fun getUsername(): String {
		return webClient.httpGet("https://$domain/users/me/profiles")
			.parseHtml()
			.selectFirstOrThrow("div:has(section > .avatar) .text-4xl")
			.text()
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		RELEVANCE,
		ALPHABETICAL,
		ALPHABETICAL_DESC,
		POPULARITY,
		POPULARITY_ASC,
		RATING,
		RATING_ASC,
		ADDED,
		ADDED_ASC,
		UPDATED,
		UPDATED_ASC,
	)

	override val filterCapabilities: MangaListFilterCapabilities =
		MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		val document = webClient.httpGet("https://$domain/search")
			.parseHtml()

		val tags = document.select("section[x-show=show_filter] div:contains(tags) fieldset label").mapToSet {
			MangaTag(
				title = it.selectFirstOrThrow("span").text(),
				key = it.selectFirstOrThrow("input[id$=value]").attr("value"),
				source = source,
			)
		}

		val states = EnumSet.of(
			ONGOING, FINISHED, ABANDONED, PAUSED,
		)

		val types = EnumSet.of(
			MANGA, MANHWA, MANHUA, COMICS,
		)

		val rating = EnumSet.of(
			SAFE, SUGGESTIVE,
		)

		return MangaListFilterOptions(
			availableTags = tags,
			availableStates = states,
			availableContentTypes = types,
			availableContentRating = rating,
		)
	}

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = "https://$domain/search/data".toHttpUrl().newBuilder().apply {
			addQueryParameter("limit", "32")
			addQueryParameter("offset", offset.toString())
			filter.query?.let {
				val query = it
					.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
					.replace(Regex("""\s+"""), " ")
					.trim()
				addQueryParameter("text", query)
			}
			addQueryParameter(
				name = "sort",
				value = when (order) {
					RELEVANCE -> "Best Match"
					ALPHABETICAL, ALPHABETICAL_DESC -> "Alphabet"
					POPULARITY, POPULARITY_ASC -> "Popularity"
					RATING, RATING_ASC -> "Subscribers"
					ADDED, ADDED_ASC -> "Recently Added"
					UPDATED, UPDATED_ASC -> "Latest Updates"
					else -> throw UnsupportedOperationException("unsupported order: $order")
				},
			)
			addQueryParameter(
				name = "order",
				value = when (order) {
					RELEVANCE, ALPHABETICAL, POPULARITY_ASC, RATING_ASC, ADDED_ASC, UPDATED_ASC -> "Ascending"
					ALPHABETICAL_DESC, POPULARITY, RATING, ADDED, UPDATED -> "Descending"
					else -> throw UnsupportedOperationException("unsupported order: $order")
				},
			)
			addQueryParameter("official", "Any")
			addQueryParameter("anime", "Any")
			with(filter.contentRating) {
				addQueryParameter(
					name = "adult",
					value = when {
						isEmpty() -> "Any"
						SAFE in this && SUGGESTIVE in this -> "Any"
						SAFE in this -> "False"
						SUGGESTIVE in this -> "True"
						else -> throw UnsupportedOperationException("unsupported content rating: $this")
					},
				)
			}
			filter.states.forEach { state ->
				addQueryParameter(
					name = "included_status",
					value = when (state) {
						ONGOING -> "Ongoing"
						FINISHED -> "Complete"
						ABANDONED -> "Canceled"
						PAUSED -> "Hiatus"
						else -> throw UnsupportedOperationException("unsupported state: $state")
					},
				)
			}
			filter.types.forEach { type ->
				addQueryParameter(
					name = "included_type",
					value = when (type) {
						MANGA -> "Manga"
						MANHWA -> "Manhwa"
						MANHUA -> "Manhua"
						COMICS -> "OEL"
						else -> throw UnsupportedOperationException("unsupported type: $type")
					},
				)
			}
			filter.tags.forEach { tag ->
				addQueryParameter("included_tag", tag.key)
			}
			filter.tagsExclude.forEach { tag ->
				addQueryParameter("excluded_tag", tag.key)
			}
			addQueryParameter("display_mode", "Full Display")
		}.build()

		val document = webClient.httpGet(url).parseHtml()

		return document.select("article:has(section)").map { element ->
			val mangaId = element.selectFirstOrThrow("a")
				.attrAsAbsoluteUrl("href")
				.toHttpUrl()
				.pathSegments[1]
			val author = document.select("div:contains(author) a").eachText().joinToString().nullIfEmpty()
			val title =
				element.selectFirst("div.text-ellipsis.truncate.text-white.text-center.text-lg.z-20.w-\\[90\\%\\]")
					?.text()
					?: "No name"
			Manga(
				id = generateUid(mangaId),
				url = mangaId,
				publicUrl = "https://$domain/series/$mangaId",
				title = title,
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				contentRating = if (element.selectFirst("svg:has(style:containsData(ff0000))") == null) {
					SAFE
				} else {
					SUGGESTIVE
				},
				coverUrl = element.selectFirst("picture img")?.attrAsAbsoluteUrlOrNull("src"),
				tags = element.selectFirst("div:contains(Tag(s): )")?.text()
					?.substringAfter("Tag(s): ")
					?.split(", ")
					?.mapToSet {
						MangaTag(
							title = it,
							key = it,
							source = source,
						)
					}
					.orEmpty(),
				state = when (document.selectFirst("div:contains(status) span")?.text()) {
					"Ongoing" -> ONGOING
					"Complete" -> FINISHED
					"Canceled" -> ABANDONED
					"Hiatus" -> PAUSED
					else -> null
				},
				authors = setOfNotNull(author),
				largeCoverUrl = null,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val document = webClient.httpGet("https://$domain/series/${manga.url}")
			.parseHtml()

		val chapters = async { getChapters(manga.url, document) }

		val sectionLeft = document.select("section[x-data] > section")[0]
		val sectionRight = document.select("section[x-data] > section")[1]
		val author = sectionLeft.select("ul > li:has(strong:contains(Author)) > span > a")
			.eachText().joinToString()

		manga.copy(
			title = sectionRight.selectFirstOrThrow("h1").text(),
			altTitles = sectionRight.select("li:has(strong:contains(Associated Name)) li")
				.eachText().toSet(),
			publicUrl = "https://$domain/series/${manga.url}",
			rating = RATING_UNKNOWN,
			contentRating = if (sectionLeft.selectFirst("ul > li > strong:contains(Official Translation) + a:contains(Yes)") != null) {
				SUGGESTIVE
			} else {
				SAFE
			},
			coverUrl = sectionLeft.selectFirst("img")?.attrAsAbsoluteUrlOrNull("src"),
			tags = sectionLeft.select("ul > li:has(strong:contains(Tag)) a").mapToSet {
				MangaTag(
					title = it.text(),
					key = it.text(),
					source = source,
				)
			},
			state = when (sectionLeft.selectFirst("ul > li:has(strong:contains(Status)) > a")?.text()) {
				"Ongoing" -> ONGOING
				"Complete" -> FINISHED
				"Canceled" -> ABANDONED
				"Hiatus" -> PAUSED
				else -> null
			},
			authors = setOf(author),
			description = Element("div").also { desc ->
				sectionRight.selectFirst("li:has(strong:contains(Description)) > p")?.let {
					desc.appendChild(it)
				}

				val ul = Element("ul")
				sectionLeft.select("ul > li:has(strong:contains(Track)) abbr").stream().forEach { abbr ->
					abbr.selectFirst("a")?.attr("href")?.let { url ->
						val a = Element("a")
							.text(
								abbr.attr("title"),
							)
							.attr("href", url)

						ul.appendChild(
							Element("li").appendChild(a),
						)
					}
				}

				if (ul.children().isNotEmpty()) {
					desc.append("<br><strong>Links:</strong>")
					desc.appendChild(ul)
				}
			}.outerHtml(),
			chapters = chapters.await(),
			source = source,
		)
	}

	private suspend fun getChapters(mangaId: String, mangaDocument: Document): List<MangaChapter> {
		val document = if (mangaDocument.selectFirst("#chapter-list > button[hx-get*=full-chapter-list]") != null) {
			webClient.httpGet("https://$domain/series/$mangaId/full-chapter-list").parseHtml()
		} else {
			mangaDocument
		}

		return document.select("div[x-data] > a").mapChapters(reversed = true) { i, element ->
			val chapterId = element.attrAsAbsoluteUrl("href")
				.toHttpUrl()
				.pathSegments[1]
			val name = element.selectFirstOrThrow("span.flex > span").text()

			MangaChapter(
				id = generateUid(chapterId),
				url = chapterId,
				title = name,
				number = Regex("""(?<!S)\b(\d+(\.\d+)?)\b""").find(name)
					?.groupValues?.get(1)?.toFloatOrNull()
					?: i.toFloat(),
				volume = Regex("""(?:S|vol(?:ume)?)\s*(\d+)""").find(name)
					?.groupValues?.get(1)?.toInt()
					?: 0,
				scanlator = when (element.selectFirst("svg")?.attr("stroke")) {
					"#d8b4fe" -> "Official"
					else -> null
				},
				uploadDate = dateFormat.parseSafe(
					element.selectFirst("time[datetime]")?.attr("datetime"),
				),
				branch = null,
				source = source,
			)
		}
	}

	private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = "https://$domain".toHttpUrl().newBuilder().apply {
			addPathSegment("chapters")
			addPathSegment(chapter.url)
			addPathSegment("images")
			addQueryParameter("is_prev", "False")
			addQueryParameter("reading_style", "long_strip")
		}.build()

		val document = webClient.httpGet(url).parseHtml()

		return document.select("section[x-data~=scroll] > img").map { element ->
			val pageUrl = element.attrAsAbsoluteUrl("src")

			MangaPage(
				id = generateUid(pageUrl),
				url = pageUrl,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val mangaId = link.pathSegments[1]

		return resolver.resolveManga(this, mangaId)
	}
}
