package org.koitharu.kotatsu.parsers.site.all

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("VIOLETSCANS", "VioletScans")
class  VioletScansParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.VIOLETSCANS, pageSize = 3) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("violetscans.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.NEWEST)

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities()

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

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
		val doc = webClient.httpPost(url, payload).parseHtml();

		val root = doc.body();
		val mangaList = root.select("div .bsx")

		if (mangaList.isEmpty()) return emptyList();

		val mangas: List<Manga> = mangaList.mapNotNull { li ->
			val coverUrl = li.selectFirstOrThrow("img").attr("src");
			val publicUrl = li.selectFirstOrThrow(".info a").attr("href");
			val title = li.selectFirstOrThrow(".info a .tt").text();
			val ratingString = li.selectFirstOrThrow(".numscore").text();
			val rating = ratingString.toFloat() / 10f
			val relativeURL = URL(publicUrl).path

			Manga(
				id = generateUid(relativeURL),
				title = title,
				altTitles = setOf(),
				url = relativeURL,
				publicUrl = publicUrl,
				rating = rating,
				contentRating = null,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = setOf(),
				source = source,
				description = null,
			)
		}
		return mangas
	}

	private suspend fun scrapeSearchList(searchParameter: String): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/?s=")
			append(URLEncoder.encode(searchParameter, StandardCharsets.UTF_8.toString()))
		}

		val doc = webClient.httpGet(url).parseHtml();

		val root = doc.body();
		val mangaList = root.select("div .bsx")

		if (mangaList.isEmpty()) return emptyList();

		val mangas: List<Manga> = mangaList.mapNotNull { li ->
			val coverUrl = li.selectFirstOrThrow("img").attr("src");
			val publicUrl = li.selectFirstOrThrow("a").attr("href");
			val title = li.selectFirstOrThrow(".bigor .tt").text();
			val ratingString = li.selectFirstOrThrow("div .numscore").text();
			val rating = ratingString.toFloat() / 10f
			val relativeURL = URL(publicUrl).path

			Manga(
				id = generateUid(relativeURL),
				title = title,
				altTitles = setOf(),
				url = relativeURL,
				publicUrl = publicUrl,
				rating = rating,
				contentRating = null,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = setOf(),
				source = source,
				description = null,
			)
		}
		return mangas
	}

	override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
		var searchParameter = "";
		query.criteria.forEach { criterion ->

			when (criterion) {
				is QueryCriteria.Match<*> -> {
					if (criterion.field == SearchableField.TITLE_NAME) {
						val title = criterion.value as String
						if (title != "") {
							searchParameter = title
						}
					}
				}
				is QueryCriteria.Exclude<*> -> null
				is QueryCriteria.Range<*> -> null
				is QueryCriteria.Include<*> -> null
			}
		}
		//srapeNonSearchList has considerable less payload as response so this is a optimization
		return if (searchParameter != "") scrapeSearchList(searchParameter) else scrapeNonSearchList(page)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.publicUrl;
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().selectFirstOrThrow(".main-info")
		val coverUrl = root.selectFirstOrThrow(".first-half .thumb img").attr("src");

		val tagsContainer = root.selectFirstOrThrow("div .wd-full")
		val tags = tagsContainer.map { tag ->
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
			description.append(p.text().trim())
		}
		var scanlator: String? = null
		var status: MangaState? = null
		var dateString: String? = null

		val infoRoot = root.selectFirstOrThrow(".left-side");
		val infos = infoRoot.select(".imptdt")

		infos.forEach { info ->
			//this website is pretty inconsistent thats why we have to do this ugly code
			val data = info.selectFirst("h1")
			if (data != null) {
				when (data.text()) {
					"Status" -> {
						when (info.selectFirstOrThrow("i").text().trim()) {
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
						scanlator = info.selectFirstOrThrow("i").text().trim()
					}
				}
			}

			info.let {
				if (info?.text()?.trim() == "Posted On") dateString = info.text().trim()
			}

		}

		var date = 0L;
		if (dateString != null) {
			val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
			date = formatter.parse(dateString).time
		}


		val chaptersList = root.selectFirstOrThrow("#chapterlist ul")
		val chapters = chaptersList.select("li")

		val mangaChapters = chapters.mapNotNull { li ->
			val url = li.getElementsByTag("a").attr("href")

			//if url is empty it means the manga is paid
			if (url == "") return@mapNotNull null

			val title = li.selectFirstOrThrow(".chapternum").text().trim();

			MangaChapter(
				id = generateUid(url),
				title = title,
				number = 0F,
				volume = 0,
				url = url,
				scanlator = scanlator,
				uploadDate = date,
				branch = null,
				source = source,
			)
		}

		return manga.copy(
			coverUrl = coverUrl,
			chapters = mangaChapters,
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

