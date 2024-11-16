package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

@OptIn(ExperimentalUnsignedTypes::class)
@MangaSourceParser("HITOMILA", "Hitomi.La", type = ContentType.HENTAI)
internal class HitomiLaParser(context: MangaLoaderContext) : MangaParser(context, MangaParserSource.HITOMILA) {
	override val configKeyDomain = ConfigKey.Domain("hitomi.la")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	private val ltnBaseUrl get() = "https://${getDomain("ltn")}"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	private val localeMap: Map<Locale, String> = mapOf(
		Locale("id") to "indonesian",
		Locale("jv") to "javanese",
		Locale("ca") to "catalan",
		Locale("ceb") to "cebuano",
		Locale("cs") to "czech",
		Locale("da") to "danish",
		Locale("de") to "german",
		Locale("et") to "estonian",
		Locale.ENGLISH to "english",
		Locale("es") to "spanish",
		Locale("eo") to "esperanto",
		Locale("fr") to "french",
		Locale("it") to "italian",
		Locale("hi") to "hindi",
		Locale("hu") to "hungarian",
		Locale("pl") to "polish",
		Locale("pt") to "portuguese",
		Locale("vi") to "vietnamese",
		Locale("tr") to "turkish",
		Locale("ru") to "russian",
		Locale("uk") to "ukrainian",
		Locale("ar") to "arabic",
		Locale.KOREAN to "korean",
		Locale.CHINESE to "chinese",
		Locale.JAPANESE to "japanese",
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableLocales = localeMap.keys,
	)

	private fun Locale?.getSiteLang(): String = when (this) {
		null -> "all"
		else -> localeMap[this] ?: "all"
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> = coroutineScope {
		('a'..'z').map { alphabet ->
			async {
				val doc = webClient.httpGet("https://$domain/alltags-$alphabet.html").parseHtml()

				doc.select(".posts > li").mapNotNull { element ->
					val num =
						element.ownText().let {
							Regex("""\((\d+)\)""").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
						}

					if (num > 100) {
						val url = element.selectFirst("a")
						val href =
							url?.attrAsRelativeUrl("href")
								?: return@mapNotNull null

						MangaTag(
							title = url.ownText().toTagTitle(),
							key = href.tagUrlToTag(),
							source = source,
						)
					} else {
						null
					}
				}
			}
		}.awaitAll().flatten().toSet()
	}

	private var cachedSearchIds: List<Int> = emptyList()

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> = when {
		filter.query.isNullOrEmpty() -> {
			if (filter.tags.isEmpty()) {
				when (order) {
					SortOrder.POPULARITY -> {
						getGalleryIDsFromNozomi(
							"popular",
							"today",
							filter.locale.getSiteLang(),
							offset.nextOffsetRange(),
						)
					}

					else -> {
						getGalleryIDsFromNozomi(null, "index", filter.locale.getSiteLang(), offset.nextOffsetRange())
					}
				}
			} else {
				if (offset == 0) {
					cachedSearchIds =
						hitomiSearch(
							filter.tags.joinToString(" ") { it.key },
							order == SortOrder.POPULARITY,
							filter.locale.getSiteLang(),
						).toList()
				}
				cachedSearchIds.subList(offset, min(offset + 25, cachedSearchIds.size))
			}
		}

		else -> {
			if (offset == 0) {
				cachedSearchIds = hitomiSearch(filter.query, order == SortOrder.POPULARITY).toList()
			}
			cachedSearchIds.subList(offset, min(offset + 25, cachedSearchIds.size))
		}
	}.toMangaList()

	private fun Int.nextOffsetRange(): LongRange {
		val bytes = this * 4L
		return bytes.until(bytes + 100L)
	}

	private suspend fun hitomiSearch(
		query: String,
		sortByPopularity: Boolean = false,
		language: String = "all",
	): Set<Int> =
		coroutineScope {
			val terms = query
				.trim()
				.replace(Regex("""^\?"""), "")
				.lowercase()
				.splitByWhitespace()
				.map {
					it.replace('_', ' ')
				}

			val positiveTerms = LinkedList<String>()
			val negativeTerms = LinkedList<String>()

			for (term in terms) {
				if (term.startsWith("-")) {
					negativeTerms.push(term.removePrefix("-"))
				} else if (term.isNotBlank()) {
					positiveTerms.push(term)
				}
			}

			val positiveResults = positiveTerms.map {
				async {
					runCatchingCancellable {
						getGalleryIDsForQuery(it, language)
					}.getOrDefault(emptySet())
				}
			}

			val negativeResults = negativeTerms.map {
				async {
					runCatchingCancellable {
						getGalleryIDsForQuery(it, language)
					}.getOrDefault(emptySet())
				}
			}

			val results = when {
				sortByPopularity -> getGalleryIDsFromNozomi(null, "popular", language)
				positiveTerms.isEmpty() -> getGalleryIDsFromNozomi(null, "index", language)
				else -> emptySet()
			}.toMutableSet()

			fun filterPositive(newResults: Set<Int>) {
				when {
					results.isEmpty() -> results.addAll(newResults)
					else -> results.retainAll(newResults)
				}
			}

			fun filterNegative(newResults: Set<Int>) {
				results.removeAll(newResults)
			}

			// positive results
			positiveResults.forEach {
				filterPositive(it.await())
			}

			// negative results
			negativeResults.forEach {
				filterNegative(it.await())
			}

			results
		}

	// search.js
	private suspend fun getGalleryIDsForQuery(
		query: String,
		language: String = "all",
	): Set<Int> {
		query.replace("_", " ").let {
			if (it.indexOf(':') > -1) {
				val sides = it.split(":")
				val ns = sides[0]
				var tag = sides[1]

				var area: String? = ns
				var lang = language
				when (ns) {
					"female", "male" -> {
						area = "tag"
						tag = it
					}

					"language" -> {
						area = null
						lang = tag
						tag = "index"
					}
				}

				return getGalleryIDsFromNozomi(area, tag, lang)
			}

			val key = hashTerm(it)
			val node = getGalleryNodeAtAddress(0)
			val data = bSearch(key, node) ?: return emptySet()

			return getGalleryIDsFromData(data)
		}
	}

	private suspend fun getGalleryIDsFromData(data: Pair<Long, Int>): Set<Int> {
		val url = "$ltnBaseUrl/galleriesindex/galleries.${galleriesIndexVersion.get()}.data"
		val (offset, length) = data
		require(length in 1..100000000) {
			"Length $length is too long"
		}

		val inbuf = getRangedResponse(url, offset.until(offset + length))

		val galleryIDs = mutableSetOf<Int>()

		val buffer =
			ByteBuffer
				.wrap(inbuf)
				.order(ByteOrder.BIG_ENDIAN)

		val numberOfGalleryIDs = buffer.int

		val expectedLength = numberOfGalleryIDs * 4 + 4

		require(numberOfGalleryIDs in 1..10000000) {
			"number_of_galleryids $numberOfGalleryIDs is too long"
		}
		require(inbuf.size == expectedLength) {
			"inbuf.byteLength ${inbuf.size} != expected_length $expectedLength"
		}

		for (i in 0.until(numberOfGalleryIDs))
			galleryIDs.add(buffer.int)

		return galleryIDs
	}

	private suspend fun bSearch(
		key: UByteArray,
		node: Node,
	): Pair<Long, Int>? {
		fun compareArrayBuffers(
			dv1: UByteArray,
			dv2: UByteArray,
		): Int {
			val top = min(dv1.size, dv2.size)

			for (i in 0.until(top)) {
				if (dv1[i] < dv2[i]) {
					return -1
				} else if (dv1[i] > dv2[i]) {
					return 1
				}
			}

			return 0
		}

		fun locateKey(
			key: UByteArray,
			node: Node,
		): Pair<Boolean, Int> {
			for (i in node.keys.indices) {
				val cmpResult = compareArrayBuffers(key, node.keys[i])

				if (cmpResult <= 0) {
					return Pair(cmpResult == 0, i)
				}
			}

			return Pair(false, node.keys.size)
		}

		fun isLeaf(node: Node): Boolean {
			for (subnode in node.subNodeAddresses)
				if (subnode != 0L) {
					return false
				}

			return true
		}

		if (node.keys.isEmpty()) {
			return null
		}

		val (there, where) = locateKey(key, node)
		if (there) {
			return node.datas[where]
		} else if (isLeaf(node)) {
			return null
		}

		val nextNode = getGalleryNodeAtAddress(node.subNodeAddresses[where])
		return bSearch(key, nextNode)
	}

	private suspend fun getGalleryIDsFromNozomi(
		area: String?,
		tag: String,
		language: String,
		range: LongRange? = null,
	): Set<Int> {
		val nozomiAddress = when (area) {
			null -> "$ltnBaseUrl/$tag-$language.nozomi"
			else -> "$ltnBaseUrl/$area/$tag-$language.nozomi"
		}

		val bytes = getRangedResponse(nozomiAddress, range)
		val nozomi = mutableSetOf<Int>()

		val arrayBuffer = ByteBuffer
			.wrap(bytes)
			.order(ByteOrder.BIG_ENDIAN)

		while (arrayBuffer.hasRemaining())
			nozomi.add(arrayBuffer.int)

		return nozomi
	}

	private val galleriesIndexVersion = suspendLazy {
		webClient.httpGet("$ltnBaseUrl/galleriesindex/version?_=${System.currentTimeMillis()}").parseRaw()
	}

	private data class Node(
		val keys: List<UByteArray>,
		val datas: List<Pair<Long, Int>>,
		val subNodeAddresses: List<Long>,
	)

	private fun decodeNode(data: ByteArray): Node {
		val buffer = ByteBuffer
			.wrap(data)
			.order(ByteOrder.BIG_ENDIAN)

		val uData = data.toUByteArray()

		val numberOfKeys = buffer.int
		val keys = ArrayList<UByteArray>()

		for (i in 0.until(numberOfKeys)) {
			val keySize = buffer.int

			check(keySize in 1..32) { "Invalid key size $keySize" }

			keys.add(uData.sliceArray(buffer.position().until(buffer.position() + keySize)))
			buffer.position(buffer.position() + keySize)
		}

		val numberOfDatas = buffer.int
		val datas = ArrayList<Pair<Long, Int>>()

		for (i in 0.until(numberOfDatas)) {
			val offset = buffer.long
			val length = buffer.int

			datas.add(Pair(offset, length))
		}

		val numberOfSubNodeAddresses = 16 + 1
		val subNodeAddresses = ArrayList<Long>()

		for (i in 0.until(numberOfSubNodeAddresses)) {
			val subNodeAddress = buffer.long
			subNodeAddresses.add(subNodeAddress)
		}

		return Node(keys, datas, subNodeAddresses)
	}

	private suspend fun getGalleryNodeAtAddress(address: Long): Node {
		val url = "$ltnBaseUrl/galleriesindex/galleries.${galleriesIndexVersion.get()}.index"

		val nodedata = getRangedResponse(url, address.until(address + 464))

		return decodeNode(nodedata)
	}

	private suspend fun getRangedResponse(
		url: String,
		range: LongRange? = null,
	): ByteArray {
		val rangeHeaders = when (range) {
			null -> Headers.headersOf()
			else -> Headers.headersOf("Range", "bytes=${range.first}-${range.last}")
		}

		return webClient.httpGet(url, rangeHeaders).parseBytes()
	}

	private fun hashTerm(term: String): UByteArray {
		return sha256(term.toByteArray()).copyOfRange(0, 4).toUByteArray()
	}

	private fun sha256(data: ByteArray): ByteArray {
		return MessageDigest.getInstance("SHA-256").digest(data)
	}

	private suspend fun Collection<Int>.toMangaList(): List<Manga> = coroutineScope {
		map { id ->
			async {
				runCatchingCancellable {
					val doc = webClient.httpGet("$ltnBaseUrl/galleryblock/$id.html").let {
						val baseUri = it.request.url.toString()
						val html = it.parseRaw()
						Jsoup.parse(rewriteTnPaths(html), baseUri)
					}

					Manga(
						id = generateUid(id.toString()),
						title = doc.selectFirstOrThrow("h1").text(),
						url = id.toString(),
						coverUrl =
							"https:" +
								doc.selectFirstOrThrow("picture > source")
									.attr("data-srcset")
									.substringBefore(" "),
						publicUrl =
							doc.selectFirstOrThrow("h1 > a")
								.attrAsRelativeUrl("href")
								.toAbsoluteUrl(domain),
						author = null,
						tags = emptySet(),
						isNsfw = true,
						rating = RATING_UNKNOWN,
						altTitle = null,
						state = null,
						source = source,
					)
				}.getOrNull()
			}
		}.awaitAll().filterNotNull()
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = webClient.httpGet("$ltnBaseUrl/galleries/${manga.url}.js")
			.parseRaw()
			.substringAfter("var galleryinfo = ")
			.let(::JSONObject)

		return manga.copy(
			title = json.getString("title"),
			largeCoverUrl =
				json.getJSONArray("files").getJSONObject(0).let {
					val hash = it.getString("hash")
					val commonId = commonImageId()
					val imageId = imageIdFromHash(hash)
					val subDomain = 'a' + subdomainOffset(imageId)

					"https://${getDomain("${subDomain}a")}/webp/$commonId$imageId/$hash.webp"
				},
			author =
				json.optJSONArray("artists")
					?.mapJSON { it.getString("artist").toCamelCase() }
					?.joinToString(),
			publicUrl = json.getString("galleryurl").toAbsoluteUrl(domain),
			tags =
				buildSet {
					json.optJSONArray("characters")
						?.mapToTags("character")
						?.let(::addAll)
					json.optJSONArray("tags")
						?.mapToTags("tag")
						?.let(::addAll)
					json.optJSONArray("artists")
						?.mapToTags("artist")
						?.let(::addAll)
					json.optJSONArray("parodys")
						?.mapToTags("parody")
						?.let(::addAll)
					json.optJSONArray("groups")
						?.mapToTags("group")
						?.let(::addAll)
				},
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.url),
					url = manga.url,
					name = json.getString("title"),
					scanlator = json.getString("type").toTitleCase(),
					number = 1f,
					volume = 0,
					branch = json.getString("language_localname"),
					source = source,
					uploadDate = dateFormat.tryParse(json.getString("date").substringBeforeLast("-")),
				),
			),
		)
	}

	private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

	private fun JSONArray.mapToTags(key: String): Set<MangaTag> {
		val tags = ArraySet<MangaTag>(length())
		mapJSON {
			MangaTag(
				title =
					it.getString(key).toCamelCase().let { title ->
						if (it.getStringOrNull("female")?.toIntOrNull() == 1) {
							"$title ♀"
						} else if (it.getStringOrNull("male")?.toIntOrNull() == 1) {
							"$title ♂"
						} else {
							title
						}
					},
				key = it.getString("url").tagUrlToTag(),
				source = source,
			).let(tags::add)
		}
		return tags
	}

	private fun String.tagUrlToTag(): String {
		val urlContent = this.split("/")
		val ns = urlContent[1]
		val tag =
			urlContent[2]
				.substringBeforeLast("-")
				.urlDecode()
				.replace(" ", "_")

		return if (tag.split(":")[0] in listOf("female", "male")) {
			tag
		} else {
			"$ns:$tag"
		}
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val json = webClient.httpGet("$ltnBaseUrl/galleries/${seed.url}.js")
			.parseRaw()
			.substringAfter("var galleryinfo = ")
			.let(::JSONObject)

		// any better way to get List<Int> from this json?
		return json.getJSONArray("related").let {
			0.until(it.length()).map { i -> it.getInt(i) }
		}.toMangaList()
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet("$ltnBaseUrl/galleries/${chapter.url}.js")
			.parseRaw()
			.substringAfter("var galleryinfo = ")
			.let(::JSONObject)

		return json.getJSONArray("files").mapJSON { image ->
			val hash = image.getString("hash")
			val commonId = commonImageId()
			val imageId = imageIdFromHash(hash)
			val subDomain = 'a' + subdomainOffset(imageId)

			MangaPage(
				id = generateUid(hash),
				url = "https://${getDomain("${subDomain}a")}/webp/$commonId$imageId/$hash.webp",
				preview = "https://${getDomain("${subDomain}tn")}/webpsmalltn/${thumbPathFromHash(hash)}/$hash.webp",
				source = source,
			)
		}
	}

	// / --->

	private var scriptLastRetrieval: Long = -1L
	private val mutex = Mutex()
	private var subdomainOffsetDefault = 0
	private val subdomainOffsetMap = mutableMapOf<Int, Int>()
	private var commonImageId = ""

	private suspend fun refreshScript() = mutex.withLock {
		if (scriptLastRetrieval == -1L || (scriptLastRetrieval + 60000) < System.currentTimeMillis()) {
			val ggScript = webClient.httpGet("$ltnBaseUrl/gg.js?_=${System.currentTimeMillis()}").parseRaw()

			subdomainOffsetDefault = Regex("var o = (\\d)").find(ggScript)!!.groupValues[1].toInt()
			val o = Regex("o = (\\d); break;").find(ggScript)!!.groupValues[1].toInt()

			subdomainOffsetMap.clear()
			Regex("case (\\d+):").findAll(ggScript).forEach {
				val case = it.groupValues[1].toInt()
				subdomainOffsetMap[case] = o
			}

			commonImageId = Regex("b: '(.+)'").find(ggScript)!!.groupValues[1]

			scriptLastRetrieval = System.currentTimeMillis()
		}
	}

	// m <-- gg.js
	private suspend fun subdomainOffset(imageId: Int): Int {
		refreshScript()
		return subdomainOffsetMap[imageId] ?: subdomainOffsetDefault
	}

	// b <-- gg.js
	private suspend fun commonImageId(): String {
		refreshScript()
		return commonImageId
	}

	// s <-- gg.js
	private fun imageIdFromHash(hash: String): Int {
		val match = Regex("(..)(.)$").find(hash)
		return match!!.groupValues.let { it[2] + it[1] }.toInt(16)
	}

	// real_full_path_from_hash <-- common.js
	private fun thumbPathFromHash(hash: String): String {
		return hash.replace(Regex("""^.*(..)(.)$"""), "$2/$1")
	}

	private suspend fun subdomainFromURL(url: String, base: String?): String {
		var retval = "b"

		if (!base.isNullOrBlank()) {
			retval = base
		}

		val regex = Regex("""/[0-9a-f]{61}([0-9a-f]{2})([0-9a-f])""")
		val hashMatch = regex.find(url) ?: return "a"
		val imageId = hashMatch.groupValues.let { it[2] + it[1] }.toIntOrNull(16)

		if (imageId != null) {
			retval = ('a' + subdomainOffset(imageId)).toString() + retval
		}

		return retval
	}

	// rewrite_tn_paths <-- common.js
	private suspend fun rewriteTnPaths(html: String): String {
		val tnRegex = Regex("""//tn\.hitomi\.la/[^/]+/[0-9a-f]/[0-9a-f]{2}/[0-9a-f]{64}""")
		val url = tnRegex.find(html)?.value ?: return html
		val newSubdomain = subdomainFromURL(url, "tn")
		val newUrl = url.replace(Regex("""//..?\.hitomi\.la/"""), "//${getDomain(newSubdomain)}/")

		return html.replace(tnRegex, newUrl)
	}

	private fun String.toTagTitle(): String {
		return toCamelCase()
			.replace("♂", "(male)")
			.replace("♀", "(female)")
	}
}
