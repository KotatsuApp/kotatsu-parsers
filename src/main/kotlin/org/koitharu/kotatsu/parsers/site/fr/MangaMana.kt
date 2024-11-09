package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.*
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAMANA", "MangaMana", "fr")
internal class MangaMana(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.MANGAMANA, 25) {

	override val configKeyDomain = ConfigKey.Domain("www.manga-mana.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.RATING,
			SortOrder.RATING_ASC,
			SortOrder.ALPHABETICAL,
			SortOrder.ALPHABETICAL_DESC,
			SortOrder.NEWEST,
			SortOrder.NEWEST_ASC,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val postData = buildString {
			append("page=")
			append(page)
			when {
				!filter.query.isNullOrEmpty() -> {
					if (page > 1) {
						return emptyList()
					}
					val domainCdn = "cdn" + domain.removePrefix("www")
					val url = "https://$domain/search-live?q=${filter.query.urlEncoded()}"
					val json = webClient.httpGet(url).parseJsonArray()
					return json.mapJSON { jo ->
						val slug = jo.getString("slug") ?: throw ParseException("Missing Slug", url)
						val url = "https://$domain/m/$slug"
						val img = "https://$domainCdn/uploads/manga/$slug/cover/cover_thumb.jpg"
						Manga(
							id = generateUid(url),
							title = jo.getString("name").orEmpty(),
							coverUrl = img,
							altTitle = jo.getString("otherNames").orEmpty(),
							author = null,
							isNsfw = when (jo.getIntOrDefault("caution", 0)) {
								0 -> false
								2 -> true
								else -> false
							},
							rating = RATING_UNKNOWN,
							url = url,
							description = jo.getString("summary_old").orEmpty(),
							publicUrl = url,
							tags = emptySet(),
							state = when (jo.getIntOrDefault("status_id_fr", 4)) {
								1 -> MangaState.ONGOING
								2 -> MangaState.FINISHED
								3 -> MangaState.ABANDONED
								else -> null
							},
							source = source,
						)
					}

				}

				else -> {

					if (order == SortOrder.UPDATED) {

						if (filter.tags.isNotEmpty() or filter.states.isNotEmpty()) {
							throw IllegalArgumentException("Le filtrage par « tri par : mis à jour » avec d'autres n'est pas pris en charge par cette source.")
						}

						val doc = webClient.httpGet("https://$domain/?page=$page").parseHtml()
						return doc.select("div.row div.col_home").map { div ->
							val href = div.selectFirstOrThrow("h4 a").attrAsRelativeUrl("href")
							val isNsfw = div.selectFirst("img[data-adult]")?.attr("data-adult")?.isNotEmpty() == true
							val img = if (isNsfw) {
								div.selectFirst("img")?.attr("data-adult")
							} else {
								div.selectFirst("img")?.attr("data-src")?.replace(" ", "")
							}
							Manga(
								id = generateUid(href),
								title = div.select("h4").text(),
								altTitle = null,
								url = href,
								publicUrl = href.toAbsoluteUrl(domain),
								rating = RATING_UNKNOWN,
								isNsfw = isNsfw,
								coverUrl = img.orEmpty(),
								description = null,
								tags = emptySet(),
								state = null,
								author = null,
								source = source,
							)
						}
					} else {
						filter.tags.oneOrThrowIfMany()?.let {
							append("&category=")
							append(it.key)
						}

						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							append(
								when (it) {
									MangaState.ONGOING -> "1"
									MangaState.FINISHED -> "2"
									MangaState.ABANDONED -> "3"
									else -> ""
								},
							)
						}

						append("&sort_by=")
						when (order) {
							SortOrder.RATING -> append("score&sort_dir=desc")
							SortOrder.RATING_ASC -> append("score&sort_dir=asc")
							SortOrder.NEWEST -> append("updated_at&sort_dir=desc")
							SortOrder.NEWEST_ASC -> append("updated_at&sort_dir=asc")
							SortOrder.ALPHABETICAL -> append("name&sort_dir=asc")
							SortOrder.ALPHABETICAL_DESC -> append("name&sort_dir=desc")
							else -> append("updated_at&sort_dir=desc")
						}
					}
				}
			}
		}

		val url = "https://$domain/liste-mangas"
		val token = webClient.httpGet(url).parseHtml().selectFirstOrThrow("meta[name=csrf-token]").attr("content")
		val headers = Headers.Builder().add("X-CSRF-TOKEN", token).add("X-Requested-With", "XMLHttpRequest")
			.add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").build()
		val doc = makeRequest(url, postData.toRequestBody(), headers)

		return doc.select("div.p-2 div.col").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			val isNsfw = div.selectFirst("img[data-adult]")?.attr("data-adult")?.isNotEmpty() == true
			val img = if (isNsfw) {
				div.selectFirst("img")?.attr("data-adult")
			} else {
				div.selectFirst("img")?.attr("data-src")?.replace(" ", "")
			}
			Manga(
				id = generateUid(href),
				title = div.select("h2.fs-6").text(),
				altTitle = doc.selectFirst(".mangalist_item_othernames")?.text().orEmpty(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = div.getElementById("avgrating")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				isNsfw = isNsfw,
				coverUrl = img.orEmpty(),
				description = div.selectFirst(".mangalist_item_description")?.text().orEmpty(),
				tags = div.select("div.mb-1 a").mapToSet {
					val key = it.attr("href").substringAfterLast('=')
					MangaTag(
						key = key,
						title = it.text(),
						source = source,
					)
				},
				state = null,
				author = null,
				source = source,
			)
		}
	}


	private suspend fun makeRequest(url: String, payload: RequestBody, headers: Headers): Document {
		var retryCount = 0
		val backoffDelay = 2000L // Initial delay (milliseconds)
		val request = Request.Builder().url(url).post(payload).headers(headers).build()
		while (true) {
			try {
				return Jsoup.parse(context.httpClient.newCall(request).execute().parseJson().getString("html"))

			} catch (e: Exception) {
				// Log or handle the exception as needed
				if (++retryCount <= 5) {
					withContext(Dispatchers.Default) {
						delay(backoffDelay)
					}
				} else {
					throw e
				}
			}
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val mangaUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(mangaUrl).parseHtml()
		val maxPageChapterSelect = doc.select("ul.pagination a.page-link")
		var maxPageChapter = 1
		if (!maxPageChapterSelect.isNullOrEmpty()) {
			maxPageChapterSelect.map {
				val i = it.attr("href").substringAfterLast("=").toInt()
				if (i > maxPageChapter) {
					maxPageChapter = i
				}
			}
		}
		manga.copy(
			state = when (doc.select("div.show_details div.d-flex:contains(Statut) span").text()) {
				"En Cours" -> MangaState.ONGOING
				"Terminé" -> MangaState.FINISHED
				"Abandonné" -> MangaState.ABANDONED
				else -> null
			},
			author = doc.selectFirst("div.show_details span[itemprop=author]")?.text().orEmpty(),
			description = doc.selectFirst("dd[itemprop=description]")?.text(),
			rating = doc.getElementById("avgrating")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			tags = doc.select("ul.list-unstyled li a.category").mapToSet {
				val key = it.attr("href").substringAfterLast('=')
				MangaTag(
					key = key,
					title = it.text(),
					source = source,
				)
			},
			chapters = run {
				if (maxPageChapter == 1) {
					parseChapters(doc)
				} else {
					coroutineScope {
						val result = ArrayList(parseChapters(doc))
						result.ensureCapacity(result.size * maxPageChapter)
						(2..maxPageChapter).map { i ->
							async {
								loadChapters(mangaUrl, i)
							}
						}.awaitAll()
							.flattenTo(result)
						result
					}
				}
			}.reversed(),
		)
	}


	private suspend fun loadChapters(baseUrl: String, page: Int): List<MangaChapter> {
		return parseChapters(webClient.httpGet("$baseUrl?page=$page").parseHtml().body())
	}

	private val dateFormat = SimpleDateFormat("d MMM yyyy", sourceLocale)

	private fun parseChapters(doc: Element): List<MangaChapter> {
		return doc.select("ul.list-unstyled li a.chapter_link")
			.mapChapters { i, a ->
				val href = a.attrAsRelativeUrl("href")
				val name = a.selectFirst(".chapter div")?.html()?.substringBefore("<") ?: "Chapitre $i"
				val dateText = a.selectFirst(".small")?.text()
				val chapterN = href.substringAfterLast('/').replace("-", ".").replace("[^0-9.]".toRegex(), "").toFloat()
				MangaChapter(
					id = generateUid(href),
					name = name,
					number = chapterN,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(dateText),
					branch = null,
					source = source,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()

		val cdn = docs.selectFirstOrThrow("script:containsData(var cdn = )").data().substringAfterLast("var cdn = \"")
			.substringBefore('"')
		val domainCdn = cdn + domain.removePrefix("www")
		val slugManga = chapterUrl.substringAfter("/m/").substringBeforeLast('/')
		val slugChapter = chapterUrl.substringAfterLast('/')

		val script = docs.selectFirstOrThrow("script:containsData(var pages =)")
		val json = JSONArray(script.data().substringAfter("pages = ").substringBefore("; var next_chapter"))
		val pages = ArrayList<MangaPage>(json.length())
		for (i in 0 until json.length()) {
			val img = json.getJSONObject(i).getString("image")
			val v = json.getJSONObject(i).getInt("version")
			val url = "https://$domainCdn/uploads/manga/$slugManga/chapters_fr/$slugChapter/$img?$v"
			pages.add(
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/liste-mangas").parseHtml()
		return doc.select("select.selectpicker option").drop(1).mapToSet {
			MangaTag(
				key = it.attr("value"),
				title = it.text(),
				source = source,
			)
		}
	}
}
