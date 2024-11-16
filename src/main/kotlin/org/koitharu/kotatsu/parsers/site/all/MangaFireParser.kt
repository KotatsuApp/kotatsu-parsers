package org.koitharu.kotatsu.parsers.site.all

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

private const val PIECE_SIZE = 200
private const val MIN_SPLIT_COUNT = 5

internal abstract class MangaFireParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	private val siteLang: String,
) : PagedMangaParser(context, source, 30), Interceptor, MangaParserAuthProvider {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("mangafire.to")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.RELEVANCE,
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val authUrl: String
		get() = "https://${domain}"

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(domain).any {
				it.value.contains("user")
			}
		}

	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${domain}/user/profile").parseHtml().body()
		return body.selectFirst("form.ajax input[name*=username]")?.attr("value")
			?: body.parseFailed("Cannot find username")
	}

	private val tags = suspendLazy(soft = true) {
		webClient.httpGet("https://$domain/filter").parseHtml()
			.select(".genres > li").map {
				MangaTag(
					title = it.selectFirstOrThrow("label").ownText().toTitleCase(sourceLocale),
					key = it.selectFirstOrThrow("input").attr("value"),
					source = source,
				)
			}.associateBy { it.title }
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = tags.get().values.toSet(),
		availableStates = EnumSet.allOf(MangaState::class.java),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = "https://$domain/filter".toHttpUrl().newBuilder().apply {
			addQueryParameter("page", page.toString())
			addQueryParameter("language[]", siteLang)

			when {
				!filter.query.isNullOrEmpty() -> {
					val encodedQuery = filter.query.splitByWhitespace().joinToString(separator = "+") { part ->
						part.urlEncoded()
					}
					addEncodedQueryParameter("keyword", encodedQuery)
					addQueryParameter(
						name = "sort",
						value = when (order) {
							SortOrder.UPDATED -> "recently_updated"
							SortOrder.POPULARITY -> "most_viewed"
							SortOrder.RATING -> "scores"
							SortOrder.NEWEST -> "release_date"
							SortOrder.ALPHABETICAL -> "title_az"
							SortOrder.RELEVANCE -> "most_relevance"
							else -> ""
						},
					)
				}

				else -> {
					filter.tagsExclude.forEach { tag ->
						addQueryParameter("genre[]", "-${tag.key}")
					}
					filter.tags.forEach { tag ->
						addQueryParameter("genre[]", tag.key)
					}
					filter.locale?.let {
						addQueryParameter("language[]", it.language)
					}
					filter.states.forEach { state ->
						addQueryParameter(
							name = "status[]",
							value = when (state) {
								MangaState.ONGOING -> "releasing"
								MangaState.FINISHED -> "completed"
								MangaState.ABANDONED -> "discontinued"
								MangaState.PAUSED -> "on_hiatus"
								MangaState.UPCOMING -> "info"
							},
						)
					}
					addQueryParameter(
						name = "sort",
						value = when (order) {
							SortOrder.UPDATED -> "recently_updated"
							SortOrder.POPULARITY -> "most_viewed"
							SortOrder.RATING -> "scores"
							SortOrder.NEWEST -> "release_date"
							SortOrder.ALPHABETICAL -> "title_az"
							SortOrder.RELEVANCE -> "most_relevance"
							else -> ""
						},
					)
				}
			}
		}.build()

		return webClient.httpGet(url)
			.parseHtml().parseMangaList()
	}

	private fun Document.parseMangaList(): List<Manga> {
		return select(".original.card-lg .unit .inner").map {
			val a = it.selectFirstOrThrow(".info > a")
			val mangaUrl = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(mangaUrl),
				url = mangaUrl,
				publicUrl = mangaUrl.toAbsoluteUrl(domain),
				title = a.ownText(),
				coverUrl = it.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
				source = source,
				altTitle = null,
				largeCoverUrl = null,
				author = null,
				isNsfw = false,
				rating = RATING_UNKNOWN,
				state = null,
				tags = emptySet(),
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val document = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val availableTags = tags.get()
		var isNsfw = false

		return manga.copy(
			title = document.selectFirstOrThrow(".info > h1").ownText(),
			altTitle = document.selectFirst(".info > h6")?.ownText(),
			rating = document.selectFirst("div.rating-box")?.attr("data-score")
				?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN,
			coverUrl = document.selectFirstOrThrow("div.manga-detail div.poster img")
				.attrAsAbsoluteUrl("src"),
			tags = document.select("div.meta a[href*=/genre/]").mapNotNullToSet {
				val tag = it.ownText()
				if (tag == "Hentai" || tag == "Ecchi") {
					isNsfw = true
				}
				availableTags[tag.toTitleCase(sourceLocale)]
			},
			isNsfw = isNsfw,
			state = document.selectFirst(".info > p")?.ownText()?.let {
				when (it.lowercase()) {
					"releasing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					"discontinued" -> MangaState.ABANDONED
					"on_hiatus" -> MangaState.PAUSED
					"info" -> MangaState.UPCOMING
					else -> null
				}
			},
			author = document.select("div.meta a[href*=/author/]")
				.joinToString { it.ownText() },
			description = document.selectFirstOrThrow("#synopsis div.modal-content").html(),
			chapters = getChapters(manga.url, document),
		)
	}

	private data class ChapterBranch(
		val type: String,
		val langCode: String,
		val langTitle: String,
	)

	private suspend fun getChapters(mangaUrl: String, document: Document): List<MangaChapter> {
		val availableTypes = document.select(".chapvol-tab > a").map {
			it.attr("data-name")
		}
		val langTypePairs = document.select(".m-list div.tab-content").flatMap {
			val type = it.attr("data-name")

			it.select(".list-menu .dropdown-item").map { item ->
				ChapterBranch(
					type = type,
					langCode = item.attr("data-code").lowercase(),
					langTitle = item.attr("data-title"),
				)
			}
		}.filter {
			it.langCode == siteLang && availableTypes.contains(it.type)
		}

		val id = mangaUrl.substringAfterLast('.')

		return coroutineScope {
			langTypePairs.map {
				async {
					getChaptersBranch(id, it)
				}
			}.awaitAll().flatten()
		}
	}

	private suspend fun getChaptersBranch(mangaId: String, branch: ChapterBranch): List<MangaChapter> {
		val chapterElements = webClient
			.httpGet("https://$domain/ajax/read/$mangaId/${branch.type}/${branch.langCode}")
			.parseJson()
			.getJSONObject("result")
			.getString("html")
			.let(Jsoup::parseBodyFragment)
			.select("ul li a")

		if (branch.type == "chapter") {
			val doc = webClient
				.httpGet("https://$domain/ajax/manga/$mangaId/${branch.type}/${branch.langCode}")
				.parseJson()
				.getString("result")
				.let(Jsoup::parseBodyFragment)

			doc.select("ul li a").withIndex().forEach { (i, it) ->
				val date = it.select("span")[1].ownText()
				chapterElements[i].attr("upload-date", date)
				chapterElements[i].attr("other-title", it.attr("title"))
			}
		}

		return chapterElements.mapChapters(reversed = true) { _, it ->
			MangaChapter(
				id = generateUid(it.attr("href")),
				name = it.attr("title").ifBlank {
					"${branch.type.toTitleCase()} ${it.attr("data-number")}"
				},
				number = it.attr("data-number").toFloat(),
				volume = it.attr("other-title").let {
					volumeNumRegex.find(it)?.groupValues?.getOrNull(2)?.toInt() ?: 0
				},
				url = "${branch.type}/${it.attr("data-id")}",
				scanlator = null,
				uploadDate = dateFormat.tryParse(it.attr("upload-date")),
				branch = "${branch.langTitle} ${branch.type.toTitleCase()}",
				source = source,
			)
		}
	}

	private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
	private val volumeNumRegex = Regex("""vol(ume)?\s*(\d+)""", RegexOption.IGNORE_CASE)

	override suspend fun getRelatedManga(seed: Manga): List<Manga> = coroutineScope {
		val document = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		val total = document.select(
			"section.m-related a[href*=/manga/], .side-manga:not(:has(.head:contains(trending))) .unit",
		).size
		val mangas = ArrayList<Manga>(total)

		// "Related Manga"
		document.select("section.m-related a[href*=/manga/]").map {
			async {
				val url = it.attrAsRelativeUrl("href")

				val mangaDocument = webClient
					.httpGet(url.toAbsoluteUrl(domain))
					.parseHtml()

				val chaptersInManga = mangaDocument.select(".m-list div.tab-content .list-menu .dropdown-item")
					.map { it.attr("data-code").lowercase() }


				if (!chaptersInManga.contains(siteLang)) {
					return@async null
				}

				Manga(
					id = generateUid(url),
					url = url,
					publicUrl = url.toAbsoluteUrl(domain),
					title = it.ownText(),
					coverUrl = mangaDocument.selectFirstOrThrow("div.manga-detail div.poster img")
						.attrAsAbsoluteUrl("src"),
					source = source,
					altTitle = null,
					largeCoverUrl = null,
					author = null,
					isNsfw = false,
					rating = RATING_UNKNOWN,
					state = null,
					tags = emptySet(),
				)
			}
		}.awaitAll()
			.filterNotNullTo(mangas)

		// "You may also like"
		document.select(".side-manga:not(:has(.head:contains(trending))) .unit").forEach {
			val url = it.attrAsRelativeUrl("href")
			mangas.add(
				Manga(
					id = generateUid(url),
					url = url,
					publicUrl = url.toAbsoluteUrl(domain),
					title = it.selectFirstOrThrow(".info h6").ownText(),
					coverUrl = it.selectFirstOrThrow(".poster img").attrAsAbsoluteUrl("src"),
					source = source,
					altTitle = null,
					largeCoverUrl = null,
					author = null,
					isNsfw = false,
					rating = RATING_UNKNOWN,
					state = null,
					tags = emptySet(),
				),
			)
		}

		mangas.ifEmpty {
			// fallback: author's other works
			document.select("div.meta a[href*=/author/]").map {
				async {
					val url = it.attrAsAbsoluteUrl("href").toHttpUrl()
						.newBuilder()
						.addQueryParameter("language[]", siteLang)
						.build()

					webClient.httpGet(url)
						.parseHtml().parseMangaList()
				}
			}.awaitAll().flatten()
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val images = webClient
			.httpGet("https://$domain/ajax/read/${chapter.url}")
			.parseJson()
			.getJSONObject("result")
			.getJSONArray("images")

		val pages = ArrayList<MangaPage>(images.length())

		for (i in 0 until images.length()) {
			val img = images.getJSONArray(i)

			val url = img.getString(0)
			val offset = img.getInt(2)

			pages.add(
				MangaPage(
					id = generateUid(url),
					url = if (offset < 1) {
						url
					} else {
						"$url#scrambled_$offset"
					},
					preview = null,
					source = source,
				),
			)
		}

		return pages
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)

		if (request.url.fragment?.startsWith("scrambled") != true) {
			return response
		}

		return context.redrawImageResponse(response) { bitmap ->
			val offset = request.url.fragment!!.substringAfter("_").toInt()
			val width = bitmap.width
			val height = bitmap.height

			val result = context.createBitmap(width, height)

			val pieceWidth = min(PIECE_SIZE, width.ceilDiv(MIN_SPLIT_COUNT))
			val pieceHeight = min(PIECE_SIZE, height.ceilDiv(MIN_SPLIT_COUNT))
			val xMax = width.ceilDiv(pieceWidth) - 1
			val yMax = height.ceilDiv(pieceHeight) - 1

			for (y in 0..yMax) {
				for (x in 0..xMax) {
					val xDst = pieceWidth * x
					val yDst = pieceHeight * y
					val w = min(pieceWidth, width - xDst)
					val h = min(pieceHeight, height - yDst)

					val xSrc = pieceWidth * when (x) {
						xMax -> x // margin
						else -> (xMax - x + offset) % xMax
					}
					val ySrc = pieceHeight * when (y) {
						yMax -> y // margin
						else -> (yMax - y + offset) % yMax
					}

					val srcRect = Rect(xSrc, ySrc, xSrc + w, ySrc + h)
					val dstRect = Rect(xDst, yDst, xDst + w, yDst + h)

					result.drawBitmap(bitmap, srcRect, dstRect)
				}
			}

			return@redrawImageResponse result
		}
	}

	private fun Int.ceilDiv(other: Int) = (this + (other - 1)) / other

	@MangaSourceParser("MANGAFIRE_EN", "MangaFire English", "en")
	class English(context: MangaLoaderContext) : MangaFireParser(context, MangaParserSource.MANGAFIRE_EN, "en")

	@MangaSourceParser("MANGAFIRE_ES", "MangaFire Spanish", "es")
	class Spanish(context: MangaLoaderContext) : MangaFireParser(context, MangaParserSource.MANGAFIRE_ES, "es")

	@MangaSourceParser("MANGAFIRE_ESLA", "MangaFire Spanish (Latim)", "es")
	class SpanishLatim(context: MangaLoaderContext) :
		MangaFireParser(context, MangaParserSource.MANGAFIRE_ESLA, "es-la")

	@MangaSourceParser("MANGAFIRE_FR", "MangaFire French", "fr")
	class French(context: MangaLoaderContext) : MangaFireParser(context, MangaParserSource.MANGAFIRE_FR, "fr")

	@MangaSourceParser("MANGAFIRE_JA", "MangaFire Japanese", "ja")
	class Japanese(context: MangaLoaderContext) : MangaFireParser(context, MangaParserSource.MANGAFIRE_JA, "ja")

	@MangaSourceParser("MANGAFIRE_PT", "MangaFire Portuguese", "pt")
	class Portuguese(context: MangaLoaderContext) : MangaFireParser(context, MangaParserSource.MANGAFIRE_PT, "pt")

	@MangaSourceParser("MANGAFIRE_PTBR", "MangaFire Portuguese (Brazil)", "pt")
	class PortugueseBR(context: MangaLoaderContext) :
		MangaFireParser(context, MangaParserSource.MANGAFIRE_PTBR, "pt-br")
}
