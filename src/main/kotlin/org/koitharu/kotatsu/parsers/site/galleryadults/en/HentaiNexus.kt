package org.koitharu.kotatsu.parsers.site.galleryadults.en

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
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.urlDecode
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.Base64

@MangaSourceParser("HENTAINEXUS", "HentaiNexus", "en", type = ContentType.HENTAI)
internal class HentaiNexus(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.HENTAINEXUS, "hentainexus.com", 30) {
	override val selectGallery = "div.container div.columns div.column"
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".card-header"
	override val selectTitle = ".title"
	override val selectTag = "tr:contains(Tags) td:nth-child(2) span.tag a"
	override val selectAuthor = "tr:contains(Artist) td:nth-child(2) a"
	override val selectLanguageChapter = ""
	override val selectUrlChapter = ""
	override val selectTotalPage = ".section div.container:nth-child(2) > div.box > div.columns div.column"

	val selectReadUrl = "a:contains(Read Online)"
	val selectPublisher = "tr:contains(Publisher) td:nth-child(2)"
	val selectPublishedDate = "tr:contains(Published) td:nth-child(2)"
	val selectDescription = "tr:contains(Description) td:nth-child(2)"

	var mangaInternalId: String = ""                    /* use as a flag for reloading data */
	var mangaPages: List<MangaPage> = listOf()

	var mangaPagesInternalId: String = ""               /* use as a flag for reloading data */
	var decryptedPagesData: List<String> = listOf()

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		val document = webClient.httpGet("https://$domain/explore/categories/tag").parseHtml()
		val tags = document.select("div.container div.columns div.column").mapToSet {div ->
			val tag = div.selectFirstOrThrow("a").attr("href")
				.substring(8) // href="/?q=tag:value"
				.urlDecode()
				.trim(' ', '\"')
			MangaTag(
				title = tag.replace(Regex("\\b[a-z]")) { it.value.uppercase(sourceLocale) },
				key = tag,
				source = source,
			)
		}

		return MangaListFilterOptions(
			availableTags = tags
		)
	}

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val url = buildString {
			append("https://$domain/page/$page")
			when {
				!filter.query.isNullOrEmpty() -> {
					append("?q=")
					append(filter.query.urlEncoded())
				}

				else -> {
					val queries = mutableListOf<String>()

					if (!filter.author.isNullOrEmpty()) {
						queries.add("artist:${filter.author}")
					}

					filter.tags.map {
						val key = it.key
						when {
							key.split(" ").count() > 1 -> {
								"\"${key.replace(Regex("\\s+")) { "+" }}\""
							}
							else -> {
								key
							}
						}
					}.also {
						if (it.count() > 0) {
							queries.add("tag:${ it.joinToString("+") }")
						}
					}

					if (queries.count() > 0) {
						append("?q=${queries.joinToString("+")}")
					}
				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
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
		val tags = doc.select(selectTag).mapToSet {
			val value = it.attr("href").substring(8).urlDecode().trim('\"')       /* /?q=tag:"blow job" */
			MangaTag(
				title = value.replace(Regex("\\b[a-z]")) { x -> x.value.uppercase(sourceLocale) },
				key = value,
				source = source
			)
		}
		val authors = doc.select(selectAuthor).mapToSet {
			it.attr("href").substring(11).urlDecode()                 /* /?q=artist:Danchino */
		}

		mangaPages = getPagesInternal(manga.url, doc)
		mangaInternalId = manga.url.split("/").last()

		val format = SimpleDateFormat("dd MMMM yyyy")

		return manga.copy(
			title = doc.select(selectTitle).text().cleanupTitle(),
			tags = tags,
			authors = authors,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					title = manga.title,
					number = 0f,
					volume = 0,
					url = manga.url,
					scanlator = doc.select(selectPublisher).text().replace(Regex(" \\([\\d,]+\\)")) { "" },
					uploadDate = format.parseSafe(doc.select(selectPublishedDate).text()),
					branch = "English",
					source = source,
				),
			),
			description = doc.selectFirst(selectDescription)?.text() ?: ""
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		if (mangaPages.isEmpty() or !chapter.url.contains(mangaInternalId)) {
			mangaPages = getPagesInternal(chapter.url)
			mangaInternalId = chapter.url.split("/").last()
		}

		return mangaPages
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		if (decryptedPagesData.isEmpty() or !page.url.contains(mangaPagesInternalId)) {
			decryptedPagesData = getPageUrlInternal(page.url)
			mangaPagesInternalId = page.url.split("/").last().split("#")[0]
		}

		val pageNumber = page.url.split("#").last().toInt()
		return decryptedPagesData[pageNumber - 1]
	}

	private suspend fun getPagesInternal(chapterUrl: String, document: Document? = null): List<MangaPage> {
		val document = document ?: webClient.httpGet(chapterUrl.toAbsoluteUrl(domain)).parseHtml()
		val readUrl = document.selectFirstOrThrow(selectReadUrl).attr("href")

		return document.select(selectTotalPage).mapIndexed { index, element ->
			val url = "$readUrl#${index + 1}"
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = element.select("img").attr("src"),
				source = source,
			)
		}
	}

	private suspend fun getPageUrlInternal(pageUrl: String) : List<String> {
		val doc = webClient.httpGet(pageUrl.toAbsoluteUrl(domain)).parseHtml()
		val encryptedPagesData = doc.selectFirstOrThrow("script:not([src])").toString()
			.substringAfter("initReader(\"")
			.substringBefore("\",")
		val decryptedString = decrypt(encryptedPagesData).replace("\\/", "/")

		val jsonArrayData = JSONArray(decryptedString)
		val pagesData : MutableList<String> = mutableListOf()

		for (i in 0 until jsonArrayData.length()) {
			val item = jsonArrayData.get(i) as JSONObject
			pagesData.add(item.get("image") as String)
		}

		return pagesData
	}

	private fun decrypt(encodedData: String): String {
		val xorKey = listOf('h','e','n','t','a','i','n','e','x','u','s','.','c','o','m')
		val keyLength = minOf(xorKey.size, 64)

		// Decode base64 string into characters (1 byte per char, unsigned)
		val decodedBytes = Base64.getDecoder().decode(encodedData)
		val decodedChars = decodedBytes.map { (it.toInt() and 0xFF).toChar() }.toMutableList()

		// XOR first 64 characters with the key
		for (i in 0 until keyLength) {
			decodedChars[i] = (decodedChars[i].code xor xorKey[i].code).toChar()
		}

		val decodedString = decodedChars.joinToString("")

		// Prime sieve: first 16 primes
		val sieve = BooleanArray(257)
		val primeIndexes = mutableListOf<Int>()
		var j = 2
		while (primeIndexes.size < 16) {
			if (!sieve[j]) {
				primeIndexes.add(j)
				for (k in j * 2..256 step j) sieve[k] = true
			}
			j++
		}

		// Hash from first 64 chars
		var hash = 0
		for (k in 0 until 64) {
			hash = hash xor decodedString[k].code
			repeat(8) {
				hash = if ((hash and 1) == 1) {
					(hash ushr 1) xor 12
				} else {
					hash ushr 1
				}
			}
		}
		hash = hash and 7

		// RC4 key scheduling
		val S = IntArray(256) { it }
		j = 0
		for (i in 0 until 256) {
			j = (j + S[i] + decodedString[i % 64].code) % 256
			S[i] = S[j].also { S[j] = S[i] }
		}

		// Decrypt rest of the chars
		val result = StringBuilder()
		var i = 0
		j = 0
		var keyStream = 0
		var rnd = 0
		val step = primeIndexes[hash]

		for (n in 0 until decodedString.length - 64) {
			i = (i + step) % 256
			j = (keyStream + S[(j + S[i]) % 256]) % 256
			keyStream = (keyStream + i + S[i]) % 256
			S[i] = S[j].also { S[j] = S[i] }

			rnd = S[(j + S[(i + S[(rnd + keyStream) % 256]) % 256]) % 256]
			val decryptedChar = decodedString[n + 64].code xor rnd
			result.append(decryptedChar.toChar())
		}

		return result.toString()
	}
}
