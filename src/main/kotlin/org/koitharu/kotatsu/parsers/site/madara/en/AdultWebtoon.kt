package org.koitharu.kotatsu.parsers.site.madara.en

import kotlinx.coroutines.*
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ADULT_WEBTOON", "AdultWebtoon", "en", ContentType.HENTAI)
internal class AdultWebtoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ADULT_WEBTOON, "adultwebtoon.com") {
	override val tagPrefix = "adult-webtoon-genre/"
	override val listUrl = "adult-webtoon/"
	override val postReq = true
	override val withoutAjax = true

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = emptySet(),
		availableContentRating = emptySet(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val pages = page + 1

		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					if (pages > 1) {
						append("/page/")
						append(pages.toString())
					}
					append("/?s=")
					append(filter.query.urlEncoded())
					append("&post_type=wp-manga")
				}

				else -> {

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append('/')
							append(tagPrefix)
							append(it.key)
							append('/')
						}
					} else {
						append('/')
						append(listUrl)
					}

					if (pages > 1) {
						append("page/")
						append(pages)
						append('/')
					}

					append("?m_orderby=")
					when (order) {
						SortOrder.POPULARITY -> append("views")
						SortOrder.UPDATED -> append("latest")
						SortOrder.NEWEST -> append("new-manga")
						SortOrder.ALPHABETICAL -> append("alphabet")
						SortOrder.RATING -> append("rating")
						else -> append("latest")
					}
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body()
		val chaptersDeferred = async { loadChapters(manga.url, doc) }
		val desc = body.select(selectDesc).html()
		val stateDiv = if (selectState.isEmpty()) {
			body.selectFirst("div.post-content_item:contains(Status)")?.selectLast("div.summary-content")
		} else {
			body.selectFirst(selectState)
		}


		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in abandoned -> MangaState.ABANDONED
				in paused -> MangaState.PAUSED
				else -> null
			}
		}

		val alt =
			doc.body().select(".post-content_item:contains(Alt) .summary-content").firstOrNull()?.tableValue()
				?.textOrNull()

		manga.copy(
			tags = doc.body().select(selectGenre).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {
		val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
		val url = "https://$domain/wp-admin/admin-ajax.php"
		val postData = "post_id=$mangaId&action=ajax_chap"
		val headers = Headers.Builder().add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").build()
		val doc = makeRequest(url, postData.toRequestBody(), headers)
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = name,
				number = i + 1f,
				volume = 0,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				scanlator = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chapterProtector = doc.getElementById("chapter-protector-data")
		if (chapterProtector == null) {
			throw if (doc.selectFirst(selectRequiredLogin) != null) {
				AuthRequiredException(source)
			} else {
				val root = doc.body().selectFirst(selectBodyPage) ?: throw ParseException(
					"No image found, try to log in",
					fullUrl,
				)
				return root.select(selectPage).map { div ->
					val img = div.selectFirstOrThrow("img")
					val url = img.requireSrc().toRelativeUrl(domain)
					MangaPage(
						id = generateUid(url),
						url = url.replace("http:", "https:"),
						preview = null,
						source = source,
					)
				}
			}
		} else {

			val chapterProtectorHtml = chapterProtector.attr("src")
				.takeIf { it.startsWith("data:text/javascript;base64,") }
				?.substringAfter("data:text/javascript;base64,")
				?.let {
					Base64.getDecoder().decode(it).decodeToString()
				}
				?: chapterProtector.html()

			val password = chapterProtectorHtml.substringAfter("wpmangaprotectornonce='").substringBefore("';")
			val chapterData = JSONObject(
				chapterProtectorHtml.substringAfter("chapter_data='").substringBefore("';").replace("\\/", "/"),
			)
			val unsaltedCiphertext = context.decodeBase64(chapterData.getString("ct"))
			val salt = chapterData.getString("s").toString().decodeHex()
			val ciphertext = "Salted__".toByteArray(Charsets.UTF_8) + salt + unsaltedCiphertext

			val rawImgArray = CryptoAES(context).decrypt(context.encodeBase64(ciphertext), password)
			val imgArrayString = rawImgArray.filterNot { c -> c == '[' || c == ']' || c == '\\' || c == '"' }

			return imgArrayString.split(",").map { url ->
				MangaPage(
					id = generateUid(url),
					url = url.replace("http:", "https:"),
					preview = null,
					source = source,
				)
			}

		}
	}

	private fun String.decodeHex(): ByteArray {
		check(length % 2 == 0) { "Must have an even length" }

		return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
	}

	private suspend fun makeRequest(url: String, payload: RequestBody, headers: Headers): Document {
		var retryCount = 0
		val backoffDelay = 2000L // Initial delay (milliseconds)
		val request = Request.Builder().url(url).post(payload).headers(headers).build()
		while (true) {
			try {
				return context.httpClient.newCall(request).execute().parseHtml()

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
}
