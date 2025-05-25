package org.koitharu.kotatsu.parsers.site.zh

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Demographic
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.YEAR_UNKNOWN
import org.koitharu.kotatsu.parsers.site.zh.LZ4K.decompressFromBase64
import org.koitharu.kotatsu.parsers.util.attrOrThrow
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.selectOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
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

/*******************************************************
 * Parser class
 ******************************************************/

@MangaSourceParser("MANHUAGUI", "Manhuagui", "zh")
internal class ManhuaguiParser(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.MANHUAGUI, pageSize = 42) {

	/*******************************************************
	 * Important constants and helper functions
	 ******************************************************/

	override val configKeyDomain = ConfigKey.Domain("www.manhuagui.com")

	val configKeyImgServer = ConfigKey.PreferredImageServer(
		presetValues = arrayOf("us", "us2", "us3", "eu", "eu2", "eu3").associateWith { it },
		defaultValue = "us",
	)

	val imgServer = "${config[configKeyImgServer]}.hamreus.com"

	private val uaList = arrayOf(
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

	val configKeyUserAgent: ConfigKey.UserAgent
		get() = ConfigKey.UserAgent(uaList.random())

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(
			SortOrder.NEWEST, // 最新发布
			SortOrder.UPDATED, // 最新更新
			SortOrder.POPULARITY, // 人气最旺
			SortOrder.RATING, // 评分最高
		)

	override val defaultSortOrder: SortOrder
		get() = SortOrder.UPDATED

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isYearSupported = true,
			isOriginalLocaleSupported = true,
		)

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", config[configKeyUserAgent])
		.add("Referer", "https://${domain}")
		.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
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

	private val fetchedTags = suspendLazy(initializer = ::fetchAvailableTags)

	private val listUrl = "/list"
	private val searchUrl = "/s"
	private val ratingUrl = "/tools/vote.ashx"
	private val sectionChaptersSelector = ".chapter-list"

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
			this == YEAR_UNKNOWN -> null
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

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet(listUrl.toAbsoluteUrl(domain)).parseHtml()
		val tags = doc.selectOrThrow("div.filter-nav > .filter.genre > ul > li > a").drop(1)
		return tags.mapToSet { a ->
			val title = a.text()
			val key = a.attr("href").removePrefix(listUrl).removeSurrounding("/")
			MangaTag(
				title = title,
				key = key,
				source = source,
			)
		}
	}

	private suspend fun getDetailsByLink(link: HttpUrl): Manga? = coroutineScope {
		val url = link.encodedPath
		val id = generateUid(url)
		val publicUrl = url.toAbsoluteUrl(domain)
		val source = source

		val detailsAsync = async {
			val doc = webClient.httpGet(link).parseHtml()

			val title = doc.selectFirst("div.book-title h1")?.text() ?: ""
			val coverUrl = doc.selectFirst("div.book-cover > p > img")?.src()?.toAbsoluteUrl(imgServer)
			val altTitles = doc.select(".book-title h2").eachText().toSet()
			val contentRating: ContentRating = doc.selectFirst("input#__VIEWSTATE").let {
				when (it) {
					null -> ContentRating.SAFE
					else -> ContentRating.ADULT
				}
			}
			val tags = doc.select("ul.detail-list > li:nth-child(2) > span:first-child > a").mapToSet { e ->
				MangaTag(
					title = e.text(),
					key = e.attr("href").removePrefix(listUrl).removeSurrounding("/"),
					source = source,
				)
			}
			val state = doc.selectFirst("li.status > span > span")?.className()?.let { className ->
				when (className) {
					"red" -> MangaState.ONGOING
					"dgreen" -> MangaState.FINISHED
					else -> null
				}
			}
			val authors = doc.select("a[href^=\"/author\"]").eachText().toSet()
			val description = doc.selectFirst("div.book-intro > #intro-all > p")?.text()
			val chapters = parseChapters(doc)

			Manga(
				id = id,
				title = title,
				altTitles = altTitles,
				url = url,
				publicUrl = publicUrl,
				rating = 0f, // fetched afterwards
				contentRating = contentRating,
				coverUrl = coverUrl,
				tags = tags,
				state = state,
				authors = authors,
				description = description,
				chapters = chapters,
				source = source,
			)
		}

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

		val details = detailsAsync.await()
		val rating = ratingAsync.await()

		details.copy(rating = rating)
	}

	private fun parseChapters(doc: Document, url: String? = null): List<MangaChapter> {
		// Parse chapters of sections
		val (sectionTitles, sectionChapters) = doc.selectFirst("#__VIEWSTATE").let {
			if (it != null) {
				val viewStateStr = decompressFromBase64(it.attrOrThrow("value"))
					?: throw ParseException("Cannot decompress __VIEWSTATE", url.ifNullOrEmpty { "" })
				val doc1 = Jsoup.parse(viewStateStr)
				Pair(
					doc1.select("h4 span"),
					doc1.select(sectionChaptersSelector),
				)
			} else {
				Pair(
					doc.select(".chapter h4 span"),
					doc.select(sectionChaptersSelector),
				)
			}
		}

		// Parse chapters from each section
		val chapters = sectionTitles
			.zip(sectionChapters)
			.flatMapIndexed { index, (title, section) ->
				val chaps = section.select("ul").flatMap {
					it.select("li a").asReversed()
				}
				chaps.mapChapters { chapIdx, chap ->
					MangaChapter(
						url = chap.attrOrThrow("href"),
						id = generateUid(chap.attrOrThrow("href")),
						title = chap.attrOrThrow("title"),
						number = (chapIdx + 1).toFloat(),
						volume = index + 1,
						scanlator = null,
						uploadDate = 0,
						branch = title.text(),
						source = source,
					)
				}
			}

		return chapters
	}

	/*******************************************************
	 * Class method overrides
	 ******************************************************/

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
		availableTags = fetchedTags.get(),
		availableDemographics = EnumSet.complementOf(EnumSet.of(Demographic.JOSEI)),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		// Flag of whether there is title query param
		var flagHasTitleQuery = false

		val url = buildString {
			var queryFull: String?
			var orderStr: String?
			var pageStr: String?
			if (filter.query == null) {
				append(listUrl.toAbsoluteUrl(domain))
				queryFull = listOfNotNull(
					filter.locale,
					filter.tags.oneOrThrowIfMany(),
					filter.demographics.oneOrThrowIfMany(),
					filter.year.takeUnless { it == YEAR_UNKNOWN },
					filter.states.oneOrThrowIfMany(),
				).joinToString("_") { it.toQueryParam().toString() }
				orderStr = when (order) {
					SortOrder.UPDATED -> "update"
					SortOrder.POPULARITY -> "view"
					SortOrder.RATING -> "rate"
					else -> "index"
				}
				pageStr = "/${orderStr}_p${page}.html"
			} else {
				flagHasTitleQuery = true
				append(searchUrl.toAbsoluteUrl(domain))
				queryFull = filter.query
				orderStr = when (order) {
					SortOrder.POPULARITY -> "_o1"
					SortOrder.NEWEST -> "_o2"
					SortOrder.RATING -> "_o3"
					else -> ""
				}
				pageStr = "${orderStr}_p${page}.html"
			}

			append("/${queryFull}${pageStr}")
		}

		val doc = webClient.httpGet(url.toHttpUrl()).parseHtml()

		return doc.select(if (flagHasTitleQuery) "div.book-result > ul > li" else "div.book-list > ul#contList > li")
			.map { li ->
				val a = li.selectFirstOrThrow("a")
				val em =
					li.selectFirst(if (flagHasTitleQuery) "div.book-score > p:first-child > strong" else "span.updateon > em")
				val href = a.attrOrThrow("href")
				val rating = em?.text()?.toFloat()?.div(10) ?: RATING_UNKNOWN
				Manga(
					id = generateUid(href),
					title = a.attr("title"),
					altTitles = emptySet<String>(),
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = rating,
					contentRating = null, // It can be fetched afterwards. Mark it null temporarily.
					coverUrl = a.selectFirst("img")?.src(),
					tags = setOf(),
					state = null,
					authors = emptySet<String>(),
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		return getDetailsByLink(manga.publicUrl.toHttpUrl()) ?: throw ParseException(
			"Cannot resolve link",
			manga.publicUrl,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(chapUrl).parseHtml()
		val regex = Regex("""^.*\}\('(.*)',(\d*),(\d*),'([\w\+/=]*)'.*${'$'}""", RegexOption.MULTILINE)
		val result = regex.find(doc.html()) ?: throw ParseException("Cannot find chapter metadata", chapUrl)
		val metadataRaw = decompressFromBase64(result.groupValues[4]) ?: throw ParseException(
			"Cannot decompress chapter metadata",
			chapUrl,
		)
		val json = PACKERDecoder.unpack(result.groupValues[1], metadataRaw.split("|"))

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
