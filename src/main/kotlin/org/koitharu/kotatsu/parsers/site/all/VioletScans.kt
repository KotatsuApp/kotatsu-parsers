package org.koitharu.kotatsu.parsers.site.en

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.model.search.SearchCapability
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.*
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.urlEncoded
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.mapChapters
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("VIOLETSCANS", "VioletScans", "en")
internal class VioletScans(context: MangaLoaderContext):
	PagedMangaParser(context, MangaParserSource.VIOLETSCANS, 12) {
	
	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("violetscans.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.NEWEST)

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities(
			SearchCapability(
				field = TITLE_NAME,
				criteriaTypes = setOf(Match::class),
				isMultiple = false,
			),
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
		var searchParameter = ""
		query.criteria.forEach { criterion ->
			when (criterion) {
				is QueryCriteria.Match<*> -> {
					if (criterion.field == SearchableField.TITLE_NAME) {
						searchParameter = criterion.value.toString()
					}
				}
				is QueryCriteria.Exclude<*> -> null
				is QueryCriteria.Range<*> -> null
				is QueryCriteria.Include<*> -> null
			}
		}
		// scrapeNonSearchList has considerable less payload as response so this is a optimization
		return when {
			!searchParameter.isNullOrEmpty() -> scrapeSearchList(searchParameter, page)
			else -> scrapeNonSearchList(page)
		}
	}

	private suspend fun scrapeNonSearchList(page: Int): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/wp-admin/admin-ajax.php")
		}
		val payload = buildString {
			append("action=load_more_manga_posts&page=")
			append((page - 1).toString())
		}

		val doc = webClient.httpPost(url, payload).parseHtml().body()
		return doc.select("div.bsx").map { li ->
			val href = li.selectFirstOrThrow(".info a").attr("href")
			Manga(
				id = generateUid(href),
				title = li.selectFirstOrThrow(".info a .tt").text(),
				altTitles = emptySet(),
				url = href,
				publicUrl = href,
				rating = li.selectFirstOrThrow(".numscore").text().toFloat() / 10f,
				contentRating = null,
				coverUrl = li.selectFirstOrThrow("img").attr("src"),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	private suspend fun scrapeSearchList(searchParameter: String, page: Int): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			if (page > 1) {
				append("/page/")
				append(page)
			}

			append("/?s=")
			append(searchParameter.urlEncoded())
		}

		val doc = webClient.httpGet(url).parseHtml().body()
		return doc.select("div.bsx").map { li ->
			val href = li.selectFirstOrThrow("a").attr("href")
			Manga(
				id = generateUid(href),
				title = li.selectFirstOrThrow(".bigor .tt").text(),
				altTitles = emptySet(),
				url = href,
				publicUrl = href,
				rating = li.selectFirstOrThrow("div .numscore").text().toFloat() / 10f,
				contentRating = null,
				coverUrl = li.selectFirstOrThrow("img").attr("src"),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url).parseHtml().body()
		val root = doc.selectFirstOrThrow(".main-info")
		val coverUrl = root.selectFirstOrThrow(".first-half .thumb img").attr("src")

		val tags = root.selectFirstOrThrow("div .wd-full").map { tag ->
			val tagName = tag.selectFirstOrThrow("a").text()
			val tagKey = tag.selectFirstOrThrow("a").attr("href")
			MangaTag(
				title = tagName,
				key = tagKey,
				source = source,
			)
		}.toSet()

		val description = StringBuilder()
		val descriptionParagraphs = root.select(".summary p")

		descriptionParagraphs.forEach { p ->
			description.append(p.text())
		}

		var scanlator: String? = null
		var status: MangaState? = null
		var dateString: String? = null

		val infoRoot = root.selectFirstOrThrow(".left-side")
		val infos = infoRoot.select(".imptdt")

		infos.forEach { info ->
			//this website is pretty inconsistent thats why we have to do this ugly code
			val data = info.selectFirst("h1")
			if (data != null) {
				when (data.text()) {
					"Status" -> {
						when (info.selectFirstOrThrow("i").text()) {
							"Ongoing" -> {
								status = MangaState.ONGOING
							}

							"Paused" -> {
								status = MangaState.PAUSED
							}

							"Completed" -> {
								status = MangaState.FINISHED
							}

							"Abandoned" -> {
								status = MangaState.ABANDONED
							}
						}
					}

					"Serialization" -> {
						scanlator = info.selectFirstOrThrow("i").text()
					}
				}
			}

			info.let {
				if (info?.text() == "Posted On") dateString = info.text()
			}

		}

		val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
		val date = dateFormat.tryParse(dateString) ?: 0L

		val chaptersList = root.selectFirstOrThrow("#chapterlist ul")
		val chapters = chaptersList.select("li")

		val mangaChapters = chapters.mapNotNull { li ->
			val url = li.getElementsByTag("a").attr("href")

			// if url is empty it means the manga is paid
			if (url.isEmpty()) null else {
				val title = li.selectFirstOrThrow(".chapternum").text()
				val regex = Regex("""\d+""")
				val matchResult = regex.find(title)
				val chapterNumber = matchResult?.value?.toFloat() ?: 0f
				MangaChapter(
					id = generateUid(url),
					title = title,
					number = chapterNumber,
					volume = 0,
					url = url,
					scanlator = scanlator,
					uploadDate = date,
					branch = null,
					source = source,
				)
			}
		}

		return manga.copy(
			coverUrl = coverUrl,
			chapters = mangaChapters.reversed(),
			state = status,
			description = description.toString(),
			tags = tags,
		)

	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = chapter.url
		val doc = webClient.httpGet(url).parseHtml()

		val scriptTags = doc.select("script")
		val pattern = Regex("""ts_reader\.run\((\{.*?\})\);""", RegexOption.DOT_MATCHES_ALL)

		val jsonString = scriptTags.firstNotNullOfOrNull { script ->
			val scriptText = script.data()
			val match = pattern.find(scriptText)
			match?.groups?.get(1)?.value
		} ?: return emptyList()

		val json = JSONObject(jsonString)
		val sources = json.getJSONArray("sources")
		if (sources.length() == 0) return emptyList()

		val images = sources.getJSONObject(0).getJSONArray("images")

		return (0 until images.length()).map { index ->
			val src = images.getString(index)
			MangaPage(
				id = generateUid(src),
				url = src,
				preview = src,
				source = source,
			)
		}
	}
}
