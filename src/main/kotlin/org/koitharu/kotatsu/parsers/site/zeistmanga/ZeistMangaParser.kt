package org.koitharu.kotatsu.parsers.site.zeistmanga

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

internal abstract class ZeistMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 12,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED),
	)

	@JvmField
	protected val ongoing: Set<String> = hashSetOf(
		"ongoing",
		"en curso",
		"ativo",
		"lançando",
		"مستمر",
		"devam ediyor",
		"güncel",
		"en emisión",
	)

	@JvmField
	protected val finished: Set<String> = hashSetOf(
		"completed",
		"completo",
		"tamamlandı",
		"finalizado",
	)

	@JvmField
	protected val abandoned: Set<String> = hashSetOf(
		"cancelled",
		"dropped",
		"dropado",
		"abandonado",
		"cancelado",
		"suspendido",
	)

	@JvmField
	protected val paused: Set<String> = hashSetOf(
		"hiatus",
	)

	protected open val sateOngoing: String = "Ongoing"
	protected open val sateFinished: String = "Completed"
	protected open val sateAbandoned: String = "Cancelled"

	protected open val maxMangaResults: Int = 20

	protected open val mangaCategory: String = "Series"

	protected open val datePattern = "yyyy-MM-dd"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val startIndex = maxMangaResults * (page - 1) + 1

		val url = buildString {
			append("https://")
			append(domain)
			append("/feeds/posts/default/-/")
			when {

				!filter.query.isNullOrEmpty() -> {
					append(mangaCategory)
					append("?alt=json&orderby=published&max-results=")
					append((maxMangaResults + 1).toString())
					append("&start-index=")
					append(startIndex.toString())
					append("&q=label:")
					append(mangaCategory)
					append("+")
					append(filter.query.urlEncoded())
				}

				else -> {


					if (filter.tags.isNotEmpty() && filter.states.isNotEmpty()) {
						throw IllegalArgumentException(ErrorMessages.FILTER_BOTH_STATES_GENRES_NOT_SUPPORTED)
					}

					if (filter.tags.isNotEmpty()) {
						append(filter.tags.oneOrThrowIfMany()?.key.orEmpty())
					} else if (filter.states.isNotEmpty()) {
						append(
							filter.states.oneOrThrowIfMany().let {
								when (it) {
									MangaState.ONGOING -> sateOngoing
									MangaState.FINISHED -> sateFinished
									MangaState.ABANDONED -> sateAbandoned
									else -> mangaCategory
								}
							},
						)
					} else {
						append(mangaCategory)
					}

					append("?alt=json&orderby=published&max-results=")
					append((maxMangaResults + 1).toString())
					append("&start-index=")
					append(startIndex.toString())
				}
			}
		}

		val json = webClient.httpGet(url).parseJson().getJSONObject("feed")

		return if (json.toString().contains("\"entry\":")) {
			parseMangaList(json.getJSONArray("entry"))
		} else {
			emptyList()
		}
	}

	protected open fun parseMangaList(json: JSONArray): List<Manga> {
		return json.mapJSON { j ->
			val name = j.getJSONObject("title").getString("\$t")
			val href =
				j.getJSONArray("link").asTypedList<JSONObject>().first { it.getString("rel") == "alternate" }
					.getString("href")
			val urlImg = if (j.toString().contains("media\$thumbnail")) {
				j.getJSONObject("media\$thumbnail").getStringOrNull("url")
					?.replace("""/s.+?-c/""".toRegex(), "/w600/")
					?.replace("""=s(?!.*=s).+?-c$""".toRegex(), "=w600")
					?.replace("""/s.+?-c-rw/""".toRegex(), "/w600/")
					?.replace("""=s(?!.*=s).+?-c-rw$""".toRegex(), "=w600")
			} else {
				Jsoup.parse(j.getJSONObject("content").getString("\$t")).selectFirst("img")?.attr("src")
			}
			Manga(
				id = generateUid(href),
				url = href.toRelativeUrl(domain),
				publicUrl = href,
				coverUrl = urlImg.orEmpty(),
				title = name,
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

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		return doc.selectFirstOrThrow("div.filter").select("ul li").mapToSet {
			MangaTag(
				key = it.selectFirstOrThrow("input").attr("value"),
				title = it.selectFirstOrThrow("label").text().toTitleCase(sourceLocale),
				source = source,
			)
		}
	}

	protected open val selectTags = "article div.mt-15 a, .info-genre a, dl:contains(Genre) dd a"
	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val state =
			doc.selectFirst("div.y6x11p:contains(Status) .dt")
				?: doc.selectFirst("div.y6x11p:contains(Estado) .dt")
				?: doc.selectFirst("ul.infonime li:contains(Status) span")
				?: doc.selectFirst("ul.infonime li:contains(Estado) span")
				?: doc.selectFirst("span.status-novel")
				?: doc.selectFirst("span[data-status]")
		val mangaState = state?.text()?.lowercase().let {
			when (it) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in abandoned -> MangaState.ABANDONED
				in paused -> MangaState.PAUSED
				else -> null
			}
		}
		val author = doc.selectFirst("div.y6x11p:contains(الكاتب) .dt")
			?: doc.selectFirst("div.y6x11p:contains(Author) .dt")
			?: doc.selectFirst("dl:contains(Author) dd")
			?: doc.selectFirst("div.y6x11p:contains(Autor) .dt")
			?: doc.selectFirst("div.y6x11p:contains(Yazar) .dt")
			?: doc.selectFirst("ul.infonime li:contains(Author) span")

		val desc = doc.getElementById("synopsis") ?: doc.getElementById("Sinopse") ?: doc.getElementById("sinopas")
		?: doc.selectFirst(".sinopsis") ?: doc.selectFirst(".sinopas")
		val chaptersDeferred = async { loadChapters(manga.url, doc) }
		manga.copy(
			author = author?.text(),
			tags = doc.select(selectTags).mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("label/").substringBefore("?"),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc?.text().orEmpty(),
			state = mangaState,
			chapters = chaptersDeferred.await(),
		)
	}

	protected open suspend fun loadChapters(mangaUrl: String, doc: Document): List<MangaChapter> {

		val feed = if (doc.getElementById("myUL") != null) {
			doc.requireElementById("myUL").selectFirstOrThrow("script").attr("src")
				.substringAfterLast("/-/").substringBefore("?").urlDecode()

		} else if (doc.selectFirst("#latest > script") != null) {
			val chapterRegex = """label\s*=\s*'([^']+)'""".toRegex()
			val scriptSelector = "#latest > script"
			val script = doc.selectFirstOrThrow(scriptSelector)
			chapterRegex
				.find(script.html())
				?.groupValues?.get(1)
				?: doc.parseFailed("Failed to find chapter feed")

		} else if (doc.selectFirst("#clwd > script") != null) {
			val chapterRegex = """clwd\.run\('([^']+)'""".toRegex()
			val scriptSelector = "#clwd > script"
			val script = doc.selectFirstOrThrow(scriptSelector)
			chapterRegex
				.find(script.html())
				?.groupValues?.get(1)
				?: doc.parseFailed("Failed to find chapter feed")

		} else if (doc.selectFirst("#chapterlist") != null) {
			doc.selectFirstOrThrow("#chapterlist").attr("data-post-title")
		} else {
			doc.selectFirstOrThrow("script:containsData(var label_chapter)").data()
				.substringAfter("label_chapter = \"").substringBefore("\"")
		}

		val url = buildString {
			append("https://")
			append(domain)
			append("/feeds/posts/default/-/")
			append(feed)
			append("?alt=json&orderby=published&max-results=9999")
		}
		val json =
			webClient.httpGet(url).parseJson().getJSONObject("feed").getJSONArray("entry").asTypedList<JSONObject>()
				.reversed()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return json.mapIndexedNotNull { i, j ->
			val name = j.getJSONObject("title").getString("\$t")
			val href =
				j.getJSONArray("link").asTypedList<JSONObject>().first { it.getString("rel") == "alternate" }
					.getString("href")
			val dateText = j.getJSONObject("published").getString("\$t").substringBefore("T")
			val slug = mangaUrl.substringAfterLast('/')
			val slugChapter = href.substringAfterLast('/')
			if (slug == slugChapter) {
				return@mapIndexedNotNull null
			}
			MangaChapter(
				id = generateUid(href),
				url = href,
				name = name,
				number = i + 1f,
				volume = 0,
				branch = null,
				uploadDate = dateFormat.tryParse(dateText),
				scanlator = null,
				source = source,
			)
		}
	}

	protected open val selectPage =
		"div.check-box img, article#reader .separator img, article.container .separator img, #readarea img, #reader img, #readerarea img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {

		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()

		return if (doc.selectFirst("script:containsData(chapterImage =)") != null) {
			doc.selectFirstOrThrow("script:containsData(chapterImage =)").data()
				.substringAfter("[").substringBefore("]")
				.replace(" ", "").replace("\"", "")
				.split(",").map { url ->
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					)
				}

		} else if (doc.selectFirst("script:containsData(const content = )") != null) {
			doc.selectFirstOrThrow("script:containsData(const content = )").data()
				.substringAfter("`").substringBefore("`;").split("src=\"").drop(1)

				.map { img ->
					val url = img.substringBefore("\"")
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					)
				}

		} else {
			doc.select(selectPage).map { img ->
				val url = img.requireSrc()
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		}

	}
}
