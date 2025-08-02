package org.koitharu.kotatsu.parsers.site.galleryadults.en

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.YEAR_UNKNOWN
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("HENTAIREAD", "HentaiRead", "en", type = ContentType.HENTAI)
internal class HentaiRead(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.HENTAIREAD, "hentairead.com", 24) {


	override fun getRequestHeaders(): Headers {
		return super.getRequestHeaders().newBuilder()
			.add("referer", "https://$domain/")
			.build()

	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isAuthorSearchSupported = true,
			isYearSupported = true,
			isTagsExclusionSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = getOrCreateTagMap().values.toSet(),
			availableContentTypes = setOf(
				ContentType.DOUJINSHI,
				ContentType.HENTAI,
				ContentType.COMICS,
				ContentType.ARTIST_CG,
			),
		)
	}

	private var tagCache: Map<String, MangaTag>? = null
	private val tagMutex = Mutex()

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = tagMutex.withLock {
		tagCache?.let { return@withLock it }

		val tags = mutableSetOf<MangaTag>()
		var offset = 0

		val mainPageDoc = webClient.httpGet("https://$domain/?s=").parseHtml()
		val tagsList = mainPageDoc.select("ul.tags-list[data-tax=manga_tag]").firstOrNull()
		val totalTags = tagsList?.attr("data-total")?.toIntOrNull() ?: 710

		while (offset < totalTags) {
			val url = "https://$domain/wp-admin/admin-ajax.php?" + buildString {
				append("action=search_manga_terms")
				append("&search=")
				append("&taxonomy=manga_tag")
				append("&offset=$offset")
				append("&extra_fields=")
				append("&hide_empty=1")
			}

			try {
				val response = webClient.httpGet(url).parseJson()
				val results = response.optJSONArray("results")

				if (results == null || results.length() == 0) {
					break
				}
				for (i in 0 until results.length()) {
					val item = results.getJSONObject(i)
					val id = item.getInt("id")
					val text = item.getString("text")

					tags.add(
						MangaTag(
							title = text.toTitleCase(),
							key = id.toString(),
							source = source,
						),
					)
				}

				offset += results.length()

			} catch (e: Exception) {
				offset += 40
			}
		}
		val tagMap = tags.associateBy { it.title }
		tagCache = tagMap
		tagMap
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.RATING,
		SortOrder.RATING_ASC,
	)

	override val selectGallery = ".manga-grid .manga-item"
	override val selectGalleryLink = "a.btn-read"
	override val selectGalleryTitle = ".manga-item__wrapper div:nth-child(3) a"
	override val selectTitle = ".manga-titles h1"
	override val selectTag = "div.text-primary:contains(Tags:)"
	override val selectAuthor = "div.text-primary:contains(Artist:)"
	override val selectLanguageChapter = ""
	override val selectUrlChapter = ""
	override val selectTotalPage = "[data-page]"

	val selectDetailsRating = ".rating__current"

	val selectAltTitle = ".manga-titles h2"
	val selectParody = "div.text-primary:contains(Parody:)"
	val selectUploadedDate = "div.text-primary:contains(Uploaded:)"

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		// Query structure:
		// ?s=&
		// title-type=contains&     /* ?s= query types: contain, start-with, end-with */
		// search-mode=AND&         /* AND | OR */
		// release-type=in&         /* release year query-types: in, before, after */
		// release=&                /* release year */
		// categories[]=4&          /* category include: Doujinshi */
		// categories[]=52&         /* category include: Manga */
		// categories[]=4798&       /* category include: Artist CG */
		// categories[]=36278&      /* category include: Western */
		// artists[]=1223&          /* author search */
		// including[]=2928&        /* tags include */
		// including[]=600&
		// excluding[]=2928&        /* tags exclude */
		// pages=                   /* search range (leaves empty or 1-1000 for max-range) */
		val url = buildString {
			append("https://$domain")

			// ANY filter (including single tag) uses search format
			val isSearch = filter.query != null ||
				filter.tags.isNotEmpty() ||
				filter.types.isNotEmpty() ||
				filter.year != YEAR_UNKNOWN ||
				filter.tagsExclude.isNotEmpty() ||
				!filter.author.isNullOrEmpty()

			when {
				// Search with any filters
				isSearch -> {
					if (page > 1) {
						append("/page/$page")
					}
					append("/?")

					val queries = mutableListOf<String>()

					// Search query (can be empty)
					queries.add("s=${filter.query?.trim()?.urlEncoded() ?: ""}")
					queries.add("title-type=contains")
					queries.add("search-mode=AND")
					queries.add("release-type=in")
					queries.add("release=${if (filter.year != YEAR_UNKNOWN) filter.year else ""}")

					// Content types
					if (filter.types.isNotEmpty()) {
						filter.types.forEach {
							when (it) {
								ContentType.DOUJINSHI -> queries.add("categories[]=4")
								ContentType.HENTAI -> queries.add("categories[]=52")
								ContentType.ARTIST_CG -> queries.add("categories[]=4798")
								ContentType.COMICS -> queries.add("categories[]=36278")
								else -> {}
							}
						}
					} else {
						// Add all categories by default
						queries.add("categories[]=4")
						queries.add("categories[]=52")
						queries.add("categories[]=4798")
						queries.add("categories[]=36278")
					}

					// Author (using artist ID for search)
					if (!filter.author.isNullOrEmpty()) {
						val authorId = getAuthorId(filter.author)
						if (authorId != null) {
							queries.add("artists[]=$authorId")
						}
					}

					filter.tags.forEach {
						queries.add("including[]=${it.key}")
					}

					filter.tagsExclude.forEach {
						queries.add("excluding[]=${it.key}")
					}

					queries.add("pages=0-1000")

					// Sort order
					when (order) {
						SortOrder.UPDATED -> queries.add("sortby=new")
						SortOrder.UPDATED_ASC -> {
							queries.add("sortby=new")
							queries.add("order=asc")
						}

						SortOrder.POPULARITY -> queries.add("sortby=all_top")
						SortOrder.POPULARITY_ASC -> {
							queries.add("sortby=all_top")
							queries.add("order=asc")
						}

						SortOrder.ALPHABETICAL -> queries.add("sortby=alphabet")
						SortOrder.ALPHABETICAL_DESC -> {
							queries.add("sortby=alphabet")
							queries.add("order=desc")
						}

						SortOrder.RATING -> queries.add("sortby=rating")
						SortOrder.RATING_ASC -> {
							queries.add("sortby=rating")
							queries.add("order=asc")
						}

						else -> {} // Default order
					}

					append(queries.joinToString("&"))
				}

				// Default
				else -> {
					if (page > 1) {
						append("/page/$page")
					}
					append("/?s=")

					when (order) {
						SortOrder.UPDATED -> append("&sortby=new")
						SortOrder.UPDATED_ASC -> append("&sortby=new&order=asc")
						SortOrder.POPULARITY -> append("&sortby=all_top")
						SortOrder.POPULARITY_ASC -> append("&sortby=all_top&order=asc")
						SortOrder.ALPHABETICAL -> append("&sortby=alphabet")
						SortOrder.ALPHABETICAL_DESC -> append("&sortby=alphabet&order=desc")
						SortOrder.RATING -> append("&sortby=rating")
						SortOrder.RATING_ASC -> append("&sortby=rating&order=asc")
						else -> {}
					}
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private suspend fun getAuthorId(authorName: String): String? {
		val jsonResponse = webClient.httpGet(
			"/wp-admin/admin-ajax.php?action=search_manga_terms&search=${authorName.urlEncoded()}&taxonomy=manga_artist"
				.toAbsoluteUrl(domain),
		).parseJson()

		val results = jsonResponse.get("results") as JSONArray
		for (i in 0 until results.length()) {
			val item = results.get(i) as JSONObject
			if (authorName.equals(item.get("text") as String, ignoreCase = true)) {
				return item.getString("id")
			}
		}
		return null
	}


	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(selectGallery).map { div ->
			val href = div.selectFirstOrThrow(selectGalleryLink).attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(selectGalleryTitle).text().cleanupTitle(),
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = div.selectFirst(selectGalleryImg)?.src(),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val tagMap = getOrCreateTagMap()

		val tags = doc.selectFirstOrThrow(selectTag).parent()!!.select("a").mapNotNullToSet { link ->
			val title = link.select("span:first-child").text()
			tagMap[title.toTitleCase()]
		}

		val authors = mutableSetOf<String>()
		doc.selectFirst(selectAuthor)?.nextElementSibling()?.parent()?.select("a")?.forEach {
			authors.add(
				it.select("span:first-child").text()
			)
		}

		var description = ""
		val parody = doc.selectFirst(selectParody)?.nextElementSibling()?.select("span:first-child")?.text()
		if (!parody.isNullOrEmpty() && !parody.contentEquals("Original")) {
			description = "Parody: $parody"
		}

		val dateFormat = SimpleDateFormat("MMMM d, yyyy h:mm a", Locale.ENGLISH)
		val uploadDateString = doc.selectFirst(selectUploadedDate)?.nextElementSibling()?.text()

		return manga.copy(
			title = doc.select(selectTitle).text().cleanupTitle(),
			altTitles = doc.selectFirst(selectAltTitle)?.text()?.let { setOf(it) } ?: emptySet(),
			contentRating = if (isNsfwSource) ContentRating.ADULT else null,
			largeCoverUrl = doc.selectFirst("#mangaSummary a.image--hover img")!!.src(),
			tags = tags,
			rating = doc.selectFirstOrThrow(selectDetailsRating).text().toFloatOrNull()?.div(5f) ?: 0f,
			authors = authors,
			description = description,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					title = manga.title,
					number = 0f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = dateFormat.parseSafe(uploadDateString),
					branch = "English",
					source = source,
				)
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select(selectTotalPage).map {
			val previewImgUrl = it.selectFirstOrThrow("img").attr("src")
			val index = it.attr("data-page")
			val url = "${chapter.url}/english/p/$index"

			MangaPage(
				id = generateUid(url),
				url = url,
				preview = previewImgUrl,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		if (page.preview.isNullOrEmpty()) {
			throw Exception("It should be not null. Something wrong!")
		}

		// preview page url: https://hencover.xyz/preview/${mangaId}/${chapterId}/hr_${index.padLeft(4)}.jpg
		// page url: https://henread.xyz/${mangaId}/${chapterId}/hr_${index.padLeft(4)}.jpg
		val index = page.url.split("/").last()
		val t = page.preview.split("/")
		val mangaId = t[4]
		val chapterId = t[5]

		return "https://henread.xyz/$mangaId/$chapterId/hr_${index.padStart(4, '0')}.jpg"
	}
}
