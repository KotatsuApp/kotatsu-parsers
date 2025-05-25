package org.koitharu.kotatsu.parsers.site.zh

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Demographic
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria
import org.koitharu.kotatsu.parsers.model.search.SearchCapability
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.site.zh.LZ4K.decompressFromBase64
import org.koitharu.kotatsu.parsers.util.LinkResolver
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.EnumSet
import java.util.Locale

private object LZ4K {
	private const val keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="

	private data class Data(
		var value: Char = '0',
		var position: Int = 0,
		var index: Int = 1,
	)

	private fun Int.power() = 1 shl this

	private val Int.string get() = this.toChar().toString()

	private fun _decompress(length: Int, resetValue: Int, getNextValue: (idx: Int) -> Char): String? {
		val builder = StringBuilder()
		val dictionary = mutableListOf(0.string, 1.string, 2.string)
		var bits = 0
		var maxpower: Int
		var power: Int
		val data = Data(getNextValue(0), resetValue, 1)
		var resb: Int
		var c = ""
		var w: String
		var entry: String
		var numBits = 3
		var enlargeIn = 4
		var dictSize = 4
		var next: Int

		fun doPower(initBits: Int, initPower: Int, initMaxPowerFactor: Int, mode: Int = 0) {
			bits = initBits
			maxpower = initMaxPowerFactor.power()
			power = initPower
			while (power != maxpower) {
				resb = data.value.code and data.position
				data.position = data.position shr 1
				if (data.position == 0) {
					data.position = resetValue
					data.value = getNextValue(data.index++)
				}
				bits = bits or (if (resb > 0) 1 else 0) * power
				power = power shl 1
			}
			when (mode) {
				0 -> Unit
				1 -> c = bits.string
				2 -> {
					dictionary.add(dictSize++, bits.string)
					next = (dictSize - 1)
					enlargeIn--
				}
			}
		}

		fun checkEnlargeIn() {
			if (enlargeIn == 0) {
				enlargeIn = numBits.power()
				numBits++
			}
		}

		doPower(bits, 1, 2)
		next = bits
		when (next) {
			0 -> doPower(0, 1, 8, 1)
			1 -> doPower(0, 1, 16, 1)
			2 -> return ""
		}
		dictionary.add(3, c)
		w = c
		builder.append(w)
		while (true) {
			if (data.index > length) {
				return ""
			}
			doPower(0, 1, numBits)
			next = bits
			when (next) {
				0 -> doPower(0, 1, 8, 2)
				1 -> doPower(0, 1, 16, 2)
				2 -> return builder.toString()
			}
			checkEnlargeIn()
			entry = when {
				dictionary.size > next -> dictionary[next]
				next == dictSize -> w + w[0]
				else -> return null
			}
			builder.append(entry)
			// Add w+entry[0] to the dictionary.
			dictionary.add(dictSize++, w + entry[0])
			enlargeIn--
			w = entry
			checkEnlargeIn()
		}
	}

	fun decompressFromBase64(input: String) = when {
		input.isBlank() -> null
		else -> _decompress(input.length, 32) {
			keyStr.indexOf(input[it]).toChar()
		}
	}
}

private object PACKERDecoder {
	/**
	 * @param src The string to be unpacked.
	 * @param syms A list of replacement symbols.
	 *
	 * @return The unpacked JSON object.
	 */
	fun unpack(src: String, syms: List<String>): JSONObject {
		val BASE = 62

		// Convert integer (0–61) to a single base-62 character
		fun base62(n: Int): String = when {
			n < 10 -> n.toString()
			n < 36 -> ('a' + (n - 10)).toString()
			else -> ('A' + (n - 36)).toString()
		}

		// Recursive radix-62 encoding
		fun encode62(num: Int): String =
			if (num >= BASE) encode62(num / BASE) + base62(num % BASE)
			else base62(num)

		// 1× replacement pass
		var working = src
		val c = syms.size
		for (idx in c - 1 downTo 0) {
			val replacement = syms[idx]
			if (replacement.isNotEmpty()) {
				val token = encode62(idx)
				// \b for word-boundary; escape token in case it contains regex metachars
				val pattern = Regex("\\b${Regex.escape(token)}\\b")
				working = pattern.replace(working, replacement)
			}
		}

		// Grab the JSON object literal inside parentheses
		val objRegex = Regex("""\((\{.+\})\)""", RegexOption.DOT_MATCHES_ALL)
		val match = objRegex.find(working)
			?: throw IllegalArgumentException("JSON payload not found after unpacking.")
		val jsonBlob = match.groupValues[1]

		return JSONObject(jsonBlob)
	}
}

private data class PartialMangaDetails(
	val altTitles: Set<String>,
	val contentRating: ContentRating?,
	val tags: Set<MangaTag>,
	val state: MangaState?,
	val authors: Set<String>,
	val description: String?,
	val chapters: List<MangaChapter>?,
)

/*******************************************************
 * Parser class
 ******************************************************/

@MangaSourceParser("MANHUAGUI", "Manhuagui", "zh")
internal class ManhuaguiParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MANHUAGUI, pageSize = 42) {

	/*******************************************************
	 * Important constants and helper functions
	 ******************************************************/

	override val configKeyDomain = ConfigKey.Domain("www.manhuagui.com")

	// There are many subdomains: us, us1, us2, eu, eu1, eu2. Just choose one you like.
	protected val configKeyImgServer = ConfigKey.PreferredImageServer(
		presetValues = arrayOf("us", "us2", "us3", "eu", "eu2", "eu3").associateWith { it },
		defaultValue = "us",
	)

	val imgServer = config[configKeyImgServer]
		?.takeIf { it.isNotEmpty() } // if not null or empty, proceed
		?.let { "${it}.hamreus.com" } // concat full domain
		?: domain // otherwise, fallback to domain

	protected val uaList = arrayOf(
		"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.2651.70 Safari/537.36 Edg/127.0.2651.70",
		"Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.1774.54 Safari/537.36 Edg/113.0.1774.54",
		"Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.5615.193 Safari/537.36",
		"Mozilla/5.0 (Windows NT 6.1; WOW64; rv:115.0.2) Gecko/20100101 Firefox/115.0.2",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 14_1_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.1245.66 Safari/537.36 Edg/102.0.1245.66",
		"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:135.0.0) Gecko/20100101 Firefox/135.0.0",
		"Mozilla/5.0 (X11; Linux x86_64; rv:128.2.0) Gecko/20100101 Firefox/128.2.0",
		"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.158 Safari/537.36",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.6834.154 Safari/537.36",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 13_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.1343.79 Safari/537.36 Edg/105.0.1343.79",
	)

	protected val configKeyUserAgent: ConfigKey.UserAgent
		get() = ConfigKey.UserAgent(uaList.random())

	protected val headers = Headers.Builder()
		.add("User-Agent", config[configKeyUserAgent])
		.add("Referer", "https://${domain}")
		.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
		// .add("Accept-Encoding", "gzip, deflate, br, zstd")
		.add("Accept-Language", "en-US,en;q=0.7,zh-CN;q=0.3")
		.add("Cache-Control", "no-cache")
		.add("Pragma", "no-cache")
		.add("Sec-Fetch-Dest", "image")
		.add("Sec-Fetch-Mode", "no-cors")
		.add("Sec-Fetch-Site", "cross-site")
		.add("Sec-GPC", "1")
		.add("DNT", "1")
		.add("Connection", "keep-alive")
		.build()

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(
			SortOrder.NEWEST, // 最新发布
			SortOrder.UPDATED, // 最新更新
			SortOrder.POPULARITY, // 人气最旺
			SortOrder.RATING, // 评分最高
		)

	override val defaultSortOrder = SortOrder.UPDATED

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities(
			SearchCapability(
				field = SearchableField.TITLE_NAME,
				criteriaTypes = setOf(QueryCriteria.Match::class),
				isMultiple = false,
				/** @note: I think there is something wrong with the docstring of the field. */
				isExclusive = true,
			),
			SearchCapability(
				field = SearchableField.ORIGINAL_LANGUAGE,
				criteriaTypes = setOf(QueryCriteria.Include::class),
				isMultiple = false,
			),
			SearchCapability(
				field = SearchableField.TAG,
				criteriaTypes = setOf(QueryCriteria.Include::class),
				isMultiple = false,
			),
			SearchCapability(
				field = SearchableField.DEMOGRAPHIC,
				criteriaTypes = setOf(QueryCriteria.Include::class),
				isMultiple = false,
			),
			SearchCapability(
				field = SearchableField.PUBLICATION_YEAR,
				criteriaTypes = setOf(QueryCriteria.Match::class),
				isMultiple = false,
			),
			SearchCapability(
				field = SearchableField.STATE,
				criteriaTypes = setOf(QueryCriteria.Include::class),
				isMultiple = false,
			),
		)

	protected val listUrl = "/list"
	protected val searchUrl = "/s"
	protected val ratingUrl = "/tools/vote.ashx"
	protected val tagSelector = "div.filter-nav > .filter.genre > ul > li > a"
	protected val mangaSelector = "div.book-list > ul#contList > li"
	protected val mangaSearchPageSelector = "div.book-result > ul > li"
	protected val ratingSelector = "span.updateon > em"
	protected val ratingSearchPageSelector = "div.book-score > p:first-child > strong"
	protected val altTitleSelector = ".book-title h2" // Assume that there is one at most
	protected val authorsSelector = "a[href^=\"/author\"]"
	protected val tagsSelector = "ul.detail-list > li:nth-child(2) > span:first-child > a"
	protected val descSelector = "div.book-intro > #intro-all > p"
	protected val nsfwCheckSelector = "input#__VIEWSTATE"
	protected val titleResolveLinkSelector = "div.book-title h1"
	protected val coverSelector = "div.book-cover > p > img"

	/**
	 * @note There is no "section" (such as 单行本, 单话) concept in Kotatsu, so we just collect all chapters.
	 *
	 * @note For SFW works, chapters are easy to parse. But for NSFW ones, it should be a little
	 * more tricky. So it needs a simple check.
	 *
	 * @note Also note that, a section may contains SEVERAL <ul>'s due to pagination, and the chapter list in every
	 * <ul> is reversed. It should be specially handled.
	 *
	 * @test https://www.manhuagui.com/comic/54020/
	 */
	protected val sectionTitlesSelector = ".chapter h4 span"
	protected val sectionTitlesAltSelector = "h4 span"
	protected val chapterViewStateSelector = "#__VIEWSTATE"
	protected val sectionChaptersSelector = ".chapter-list"
	protected val chaptersSelector = "li a"
	protected val stateSelector = "li.status > span > span"

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request().newBuilder().headers(headers).build()
		return chain.proceed(request)
	}

	private fun Any?.toQueryParam(): String? = when (this) {
		// Title
		is String -> urlEncoded()

		is Locale -> when (this) {
			Locale.JAPAN -> "japan" // 日本
			Locale.TRADITIONAL_CHINESE -> "hongkong" // 港台
			Locale.ROOT -> "other" // 其他 (I do not know if it is sure to use it)
			Locale.US -> "europe" // 欧美
			Locale.SIMPLIFIED_CHINESE -> "china" // 内地
			Locale.KOREA -> "korea" // 韩国
			else -> null
		}

		is MangaTag -> key

		is Demographic -> when (this) {
			Demographic.SHOUJO -> "shaonv" // 少女
			Demographic.SHOUNEN -> "shaonian" // 少年
			Demographic.SEINEN -> "qingnian" // 青年
			Demographic.KODOMO -> "ertong" // 儿童
			Demographic.NONE -> "tongyong" // 通用
			else -> null
		}

		// Year
		is Int -> when {
			this >= 2010 && this <= 2025 -> toString()
			this >= 2000 && this < 2010 -> "200x"
			this >= 1990 && this < 2000 -> "199x"
			this >= 1980 && this < 1990 -> "198x"
			this < 1980 -> "197x"
			else -> null
		}

		is MangaState -> when (this) {
			MangaState.ONGOING -> "lianzai" // 连载
			MangaState.FINISHED -> "wanjie" // 完结
			else -> null
		}

		else -> null
	}

	/** @warn It won't do any sanity check. */
	private fun String.addQueryParameters(params: JSONObject): String {
		val builder = this.toHttpUrl().newBuilder()
		val keys = params.keys()
		while (keys.hasNext()) {
			val key = keys.next()
			val value = params.get(key).toString()
			builder.addQueryParameter(key, value)
		}
		return builder.build().toString()
	}

	protected suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}${listUrl}").parseHtml()
		val tags = doc.select(tagSelector).drop(1) // The first one means "all". Need to be dropped.
		return tags.mapToSet { a ->
			val title = a.text()
			val key = a.attr("href").removePrefix("/list/").removeSuffix("/")
			MangaTag(
				title = title,
				key = key,
				source = source,
			)
		}
	}

	protected fun parseChapters(doc: Document): List<MangaChapter> {
		// Parse chapters of sections
		var sectionTitles = doc.select(sectionTitlesSelector)
		var sectionChapters: Elements?
		if (sectionTitles.isEmpty()) {
			// Some complicated happens ...
			val viewState = doc.selectFirst(chapterViewStateSelector)
			if (viewState == null) {
				throw RuntimeException("Cannot find sections")
			}
			val viewStateStr = decompressFromBase64(viewState.attr("value"))
			if (viewStateStr == null) {
				throw RuntimeException("Cannot decompress __VIEWSTATE")
			}
			val doc1 = Jsoup.parse(viewStateStr)
			sectionTitles = doc1.select(sectionTitlesAltSelector)
			sectionChapters = doc1.select(sectionChaptersSelector)
		} else {
			sectionChapters = doc.select(sectionChaptersSelector)
		}

		// Parse chapters from each section
		val chapters = sectionTitles
			.zip(sectionChapters)
			.flatMapIndexed { index, (title, section) ->
				val chaps = section.select("ul").flatMap {
					it.select(chaptersSelector).asReversed()
				}
				chaps.mapChapters { chapIdx, chap ->
					MangaChapter(
						id = generateUid(chap.attr("href")),
						title = chap.attr("title"),
						number = (chapIdx + 1).toFloat(),
						volume = index + 1,
						url = chap.attr("href"),
						scanlator = null,
						uploadDate = 0, // It can be fetched by parsing the source of the chapter.
						branch = title.text(),
						source = source,
					)
				}
			}

		return chapters
	}

	private suspend fun getPartialJSONDetailsByLink(link: HttpUrl): PartialMangaDetails {
		// Parse HTML
		val doc = webClient.httpGet(link.toString()).parseHtml()

		// altTitles
		val altTitles = doc.select(altTitleSelector).eachText().toSet()

		// contentRating
		val contentRating: ContentRating = doc.selectFirst(nsfwCheckSelector).let {
			when (it) {
				null -> ContentRating.SAFE
				else -> ContentRating.ADULT
			}
		}

		// tags
		val tags = doc.select(tagsSelector).mapToSet { e ->
			MangaTag(
				title = e.text(),
				key = e.attr("href").removePrefix("/list/").removeSuffix("/"),
				source = source,
			)
		}

		// state
		val state = doc.selectFirst(stateSelector)?.className()?.let { className ->
			when (className) {
				"red" -> MangaState.ONGOING
				"dgreen" -> MangaState.FINISHED
				else -> null
			}
		}

		// authors
		val authors = doc.select(authorsSelector).eachText()

		// description
		val description = doc.selectFirst(descSelector)?.text()

		// chapters
		val chapters = parseChapters(doc)

		return PartialMangaDetails(
			altTitles = altTitles,
			contentRating = contentRating,
			tags = tags,
			state = state,
			authors = authors.toSet(),
			description = description,
			chapters = chapters,
		)
	}

	private suspend fun getPartialJSONDetailsByUrl(url: String): PartialMangaDetails =
		getPartialJSONDetailsByLink(url.toHttpUrl())

	/*******************************************************
	 * Class method overrides
	 ******************************************************/

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(configKeyUserAgent)
		keys.add(configKeyImgServer)
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableLocales = setOf(
			Locale.JAPAN, Locale.TRADITIONAL_CHINESE, Locale.ROOT,
			Locale.US, Locale.SIMPLIFIED_CHINESE, Locale.KOREA,
		),
		availableTags = fetchAvailableTags(),
		availableDemographics = EnumSet.complementOf(EnumSet.of(Demographic.JOSEI)),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	/**
	 * This method is made for testing. It does little impact to basic usage without itself.
	 */
	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? = coroutineScope {
		// Something easy
		val url = link.encodedPath
		val id = generateUid(url)
		val publicUrl = url.toAbsoluteUrl(domain)
		val source = source

		// Async #1: We have handled some fields
		val detailsAsync = async { getPartialJSONDetailsByLink(link) }

		// Async #2: Now we need to handle the others
		val pairAsync = async {
			val doc = webClient.httpGet(link).parseHtml()
			val title = doc.selectFirst(titleResolveLinkSelector)?.text() ?: ""
			val coverUrl = doc.selectFirst(coverSelector)?.attrAsAbsoluteUrl("src")
			Pair(title, coverUrl)
		}

		// Async #3: In detail page, rating is dynamically calculated from another response
		val ratingAsync = async {
			val bid = link.pathSegments[1]
			val url = ratingUrl.toAbsoluteUrl(domain).addQueryParameters(
				JSONObject().apply {
					put("bid", bid)
					put("act", "get")
				},
			)
			val result = webClient.httpGet(url).parseJson()
			require(result.optBoolean("success")) { "Rating XHR request is not successful" }
			// Count of score 1, 2, 3, 4, 5, respectively
			val a = result.optJSONObject("data")?.optInt("s1") ?: 0
			val b = result.optJSONObject("data")?.optInt("s2") ?: 0
			val c = result.optJSONObject("data")?.optInt("s3") ?: 0
			val d = result.optJSONObject("data")?.optInt("s4") ?: 0
			val e = result.optJSONObject("data")?.optInt("s5") ?: 0
			(a + b * 2 + c * 3 + d * 4 + e * 5) / (a + b + c + d + e).toFloat() * 2
		}

		// Bundle all
		val details = detailsAsync.await()
		val (title, coverUrl) = pairAsync.await()
		val rating = ratingAsync.await()

		Manga(
			id = id,
			title = title,
			url = url,
			publicUrl = publicUrl,
			rating = rating,
			coverUrl = coverUrl,
			source = source,
			altTitles = details.altTitles,
			contentRating = details.contentRating,
			tags = details.tags,
			state = details.state,
			authors = details.authors,
			description = details.description,
			chapters = details.chapters,
		)
	}

	override suspend fun getListPage(
		query: MangaSearchQuery,
		page: Int,
	): List<Manga> {
		// Flag of whether there is title query param
		var flagHasTitleQuery = false

		// Build final URL
		val url = buildString {
			// Query
			/** @note Every field of query can only contain one value. */
			var queryTitle: String? = null // 标题
			var queryLang: String? = null // 地区
			var queryTag: String? = null // 剧情
			var queryDemo: String? = null // 受众
			var queryYear: String? = null // 年份
			var queryState: String? = null // 进度

			query.criteria.forEach { criterion ->
				val field = criterion.field
				when (criterion) {
					// Lang, Tag, Demo, State
					is QueryCriteria.Include<*> -> {
						val values = criterion.values
						val param = values.first().toQueryParam()
						when (field) {
							SearchableField.ORIGINAL_LANGUAGE -> {
								queryLang = param
							}

							SearchableField.TAG -> {
								queryTag = param
							}

							SearchableField.DEMOGRAPHIC -> {
								queryDemo = param
							}

							SearchableField.STATE -> {
								queryState = param
							}

							else -> {}
						}
					}

					// Title, Year
					is QueryCriteria.Match<*> -> {
						val value = criterion.value
						val param = value.toQueryParam()
						when (field) {
							SearchableField.TITLE_NAME -> {
								queryTitle = param
							}

							SearchableField.PUBLICATION_YEAR -> {
								queryYear = param
							}

							else -> {}
						}
					}

					else -> {}
				}
			}

			var queryFull: String?
			var orderStr: String?
			var pageStr: String?
			if (queryTitle == null) {
				// List base URL
				append("https://${domain}${listUrl}")
				// List params
				queryFull = listOfNotNull(
					queryLang, queryTag, queryDemo, queryYear, queryState,
				).joinToString("_")
				// Sort order
				orderStr = when (query.order) {
					SortOrder.NEWEST -> "index"
					SortOrder.UPDATED -> "update"
					SortOrder.POPULARITY -> "view"
					SortOrder.RATING -> "rate"
					else -> "index"
				}
				// Order and page
				pageStr = "/${orderStr}_p${page}.html"
			} else {
				// Update flag
				flagHasTitleQuery = true
				// Search title base URL
				append("https://${domain}${searchUrl}")
				// Search params
				queryFull = queryTitle
				// Sort order
				orderStr = when (query.order) {
					SortOrder.NEWEST -> "_o2"
					SortOrder.UPDATED -> ""
					SortOrder.POPULARITY -> "_o1"
					SortOrder.RATING -> "_o3"
					else -> ""
				}
				// Order and page
				pageStr = "${orderStr}_p${page}.html"
			}

			/**
			 * @note When queryFull is empty, it will at last append an additional slash to the
			 * URL, but it does little badness.
			 */
			append("/${queryFull}${pageStr}")
		}

		// Fetch and parse HTML
		val doc = webClient.httpGet(url.toHttpUrl()).parseHtml()

		return doc.select(if (flagHasTitleQuery) mangaSearchPageSelector else mangaSelector).map { li ->
			val a = li.selectFirstOrThrow("a")  // Contains most details
			val em =
				li.selectFirst(if (flagHasTitleQuery) ratingSearchPageSelector else ratingSelector)  // Contains rating, but not so vital
			val href = a.attrAsRelativeUrl("href")
			val rating = em?.text()?.toFloat()?.div(10) ?: RATING_UNKNOWN
			Manga(
				id = generateUid(href),
				title = a.attr("title"),
				altTitles = emptySet<String>(),
				url = href, // Should be relative
				publicUrl = href.toAbsoluteUrl(domain), // Should be absolute
				rating = rating,
				contentRating = null, // It can be fetched afterwards. Mark it null temporarily.
				coverUrl = a.selectFirst("img")?.src(),
				tags = setOf(),
				state = null,
				// Very unfortunately the list page does not contain authors in list page
				authors = emptySet<String>(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val details = getPartialJSONDetailsByUrl(manga.publicUrl)

		return manga.copy(
			altTitles = details.altTitles,
			contentRating = details.contentRating,
			tags = details.tags,
			state = details.state,
			authors = details.authors,
			description = details.description,
			chapters = details.chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		// Parse raw chapter metadata
		val chapUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(chapUrl).parseHtml()
		val regex = Regex("""^.*\}\('(.*)',(\d*),(\d*),'([\w\+/=]*)'.*${'$'}""", RegexOption.MULTILINE)
		val result = regex.find(doc.html())
		if (result == null) {
			throw RuntimeException("Cannot find chapter metadata in the page")
		}
		val metadataRaw = decompressFromBase64(result.groupValues[4])
		if (metadataRaw == null) {
			throw RuntimeException("Cannot decompress chapter metadata")
		}
		val json = PACKERDecoder.unpack(result.groupValues[1], metadataRaw.split("|"))

		// Extract what we want
		val files = json.getJSONArray("files")
		val semiFullUrl = json.getString("path").toAbsoluteUrl(imgServer)
		val signature = json.getJSONObject("sl")

		return files.asTypedList<String>().map { it ->
			val fullUrl = (semiFullUrl + it).addQueryParameters(signature)
			MangaPage(
				id = generateUid(fullUrl),
				url = fullUrl,
				preview = fullUrl,
				source = source,
			)
		}
	}
}
