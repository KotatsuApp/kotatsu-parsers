package org.koitharu.kotatsu.parsers.site.galleryadults.all

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
import org.koitharu.kotatsu.parsers.util.textOrNull
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.urlDecode
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.Base64

@MangaSourceParser("HENTAINEXUS", "HentaiNexus", type = ContentType.HENTAI)
internal class HentaiNexus(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.HENTAINEXUS, "hentainexus.com", 30) {
	override val selectGallery = "div.container div.columns div.column"
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".card-header"
	override val selectTitle = ".title"
	override val selectTag = "tr:contains(Tags) td:nth-child(2)"
	override val selectAuthor = "tr:contains(Artist) td:nth-child(2)"
	override val selectLanguageChapter = ""
	override val selectUrlChapter = ""
	override val selectTotalPage = ".section div.container:nth-child(2) > div.box > div.columns div.column"

	val selectReadUrl = "a:contains(Read Online)"
	val selectPublisher = "tr:contains(Publisher) td:nth-child(2)"
	val selectPublishedDate = "tr:contains(Published) td:nth-child(2)"
	val selectDescription = "tr:contains(Description) td:nth-child(2)"

	var mangaPages: List<MangaPage> = listOf()
	var decryptedPagesData: List<String> = listOf()

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isMultipleTagsSupported = true,
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
					val tags = filter.tags.map {
						val key = it.key
						when {
							key.split(" ").count() > 1 -> {
								"\"${key.replace(Regex("\\s+")) { "+" }}\""
							}
							else -> {
								key
							}
						}
					}
					if (tags.count() > 0) {
						append("?q=tag:${ tags.joinToString("+") }")
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
		val tags = doc.selectFirst(selectTag)?.parseTags()
		val authors = doc.selectFirst(selectAuthor)?.parseTags()?.mapToSet { it.title }

		mangaPages = getPagesInternal(manga.url, doc)

		return manga.copy(
			tags = tags.orEmpty(),
			title = doc.selectFirst(selectTitle)?.textOrNull()?.cleanupTitle() ?: manga.title,
			authors = authors.orEmpty(),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					title = manga.title,
					number = 0f,
					volume = 0,
					url = manga.url,
					scanlator = doc.selectFirst(selectPublisher)?.text()?.replace(Regex(" \\([\\d,]+\\)")) { "" },
					uploadDate = parseDateString(doc.selectFirst(selectPublishedDate)?.text()),
					branch = "English",
					source = source,
				),
			),
			description = doc.selectFirst(selectDescription)?.text() ?: ""
		)
	}

	private fun parseDateString(dateString: String?) : Long {
		if (dateString == null) return 0
		val monthToNumber = mapOf("January" to 1, "February" to 2, "March" to 3, "April" to 4, "May" to 5, "June" to 6, "July" to 7, "August" to 8, "September" to 9, "October" to 10, "November" to 11, "December" to 12, "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "Jun" to 6, "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12, "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12, "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
		val format = SimpleDateFormat("yyyy-MM-dd")
		val dateValues = dateString.split(" ")
		return format.parse("${dateValues[2]}-${(monthToNumber[dateValues[1]].toString()).padStart(2, '0')}-${dateValues[0].padStart(2, '0')}")?.time ?: 0
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		if (mangaPages.count() == 0) {
			mangaPages = getPagesInternal(chapter.url)
		}

		return mangaPages
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		if (decryptedPagesData.count() == 0) {
			decryptedPagesData = getPageUrlInternal(page.url)
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
		var decryptedString = decrypt(encryptedPagesData).replace("\\/", "/")
		val pageUrls : MutableList<String> = mutableListOf()

		var foundIndex = decryptedString.indexOf("\",\"label\":", 0)
		do {
			val pageUrl = decryptedString
				.substringAfter("{\"image\":\"")
				.substringBefore("\",\"label\":")

			pageUrls.add(pageUrl)

			decryptedString = decryptedString.substring(foundIndex + 10)
			foundIndex = decryptedString.indexOf("\",\"label\":", 0)
		} while (foundIndex > -1)

		return pageUrls
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
