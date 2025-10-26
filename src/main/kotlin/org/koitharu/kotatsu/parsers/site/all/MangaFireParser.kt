package org.koitharu.kotatsu.parsers.site.all

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
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
import org.koitharu.kotatsu.parsers.network.OkHttpWebClient
import org.koitharu.kotatsu.parsers.network.WebClient
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.getCookies
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import org.koitharu.kotatsu.parsers.util.ownTextOrNull
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.splitByWhitespace
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.EnumSet
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.min

private const val PIECE_SIZE = 200
private const val MIN_SPLIT_COUNT = 5

@Suppress("CustomX509TrustManager")
internal abstract class MangaFireParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	private val siteLang: String,
) : PagedMangaParser(context, source, 30), Interceptor, MangaParserAuthProvider {

    private val client: WebClient by lazy {
        val newHttpClient = context.httpClient.newBuilder()
            .sslSocketFactory(SSLUtils.sslSocketFactory!!, SSLUtils.trustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request.newBuilder()
                    .addHeader("Referer", "https://$domain/")
                    .build())

                if (request.url.fragment?.startsWith("scrambled") == true) {
                    return@addInterceptor context.redrawImageResponse(response) { bitmap ->
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

                        result
                    }
                }

                response
            }
            .build()
        OkHttpWebClient(newHttpClient, source)
    }

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

	override suspend fun isAuthorized(): Boolean {
		return context.cookieJar.getCookies(domain).any {
			it.value.contains("user")
		}
	}

	override suspend fun getUsername(): String {
		val body = client.httpGet("https://${domain}/user/profile").parseHtml().body()
		return body.selectFirst("form.ajax input[name*=username]")?.attr("value")
			?: body.parseFailed("Cannot find username")
	}

	private val tags = suspendLazy(soft = true) {
		client.httpGet("https://$domain/filter").parseHtml()
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
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.ABANDONED,
			MangaState.PAUSED,
			MangaState.UPCOMING,
		),
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

                    // Generate VRF for search query
                    val searchVrf = VrfGenerator.generate(filter.query.trim())
                    addQueryParameter("vrf", searchVrf)

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
								else -> throw IllegalArgumentException("$state not supported")
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

		return client.httpGet(url)
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
				altTitles = emptySet(),
				largeCoverUrl = null,
				authors = emptySet(),
				contentRating = null,
				rating = RATING_UNKNOWN,
				state = null,
				tags = emptySet(),
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val document = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val availableTags = tags.get()
		var isAdult = false
		var isSuggestive = false
		val author = document.select("div.meta a[href*=/author/]")
			.joinToString { it.ownText() }.nullIfEmpty()

		return manga.copy(
			title = document.selectFirstOrThrow(".info > h1").ownText(),
			altTitles = setOfNotNull(document.selectFirst(".info > h6")?.ownTextOrNull()),
			rating = document.selectFirst("div.rating-box")?.attr("data-score")
				?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN,
			coverUrl = document.selectFirstOrThrow("div.manga-detail div.poster img")
				.attrAsAbsoluteUrl("src"),
			tags = document.select("div.meta a[href*=/genre/]").mapNotNullToSet {
				val tag = it.ownText()
				if (tag == "Hentai") {
					isAdult = true
				} else if (tag == "Ecchi") {
					isSuggestive = true
				}
				availableTags[tag.toTitleCase(sourceLocale)]
			},
			contentRating = when {
				isAdult -> ContentRating.ADULT
				isSuggestive -> ContentRating.SUGGESTIVE
				else -> ContentRating.SAFE
			},
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
			authors = setOfNotNull(author),
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
        val readVrfInput = "$mangaId@${branch.type}@${branch.langCode}"
        val readVrf = VrfGenerator.generate(readVrfInput)

        val response = client
            .httpGet("https://$domain/ajax/read/$mangaId/${branch.type}/${branch.langCode}?vrf=$readVrf")

        val chapterElements = response.parseJson()
            .getJSONObject("result")
            .getString("html")
            .let(Jsoup::parseBodyFragment)
            .select("ul li a")

		if (branch.type == "chapter") {
			val doc = client
				.httpGet("https://$domain/ajax/manga/$mangaId/${branch.type}/${branch.langCode}")
				.parseJson()
				.getString("result")
				.let(Jsoup::parseBodyFragment)

            doc.select("ul li a").withIndex().forEach { (i, it) ->
                val date = it.select("span").getOrNull(1)?.ownText() ?: ""
                chapterElements[i].attr("upload-date", date)
                chapterElements[i].attr("other-title", it.attr("title"))
            }
        }

        return chapterElements.mapChapters(reversed = true) { _, it ->
            val chapterId = it.attr("data-id")
            MangaChapter(
                id = generateUid(it.attr("href")),
                title = it.attr("title").ifBlank {
                    "${branch.type.toTitleCase()} ${it.attr("data-number")}"
                },
                number = it.attr("data-number").toFloatOrNull() ?: -1f,
                volume = it.attr("other-title").let { title ->
                    volumeNumRegex.find(title)?.groupValues?.getOrNull(2)?.toInt() ?: 0
                },
                url = "$mangaId/${branch.type}/${branch.langCode}/$chapterId",
                scanlator = null,
                uploadDate = dateFormat.parseSafe(it.attr("upload-date")),
                branch = "${branch.langTitle} ${branch.type.toTitleCase()}",
                source = source,
            )
        }
    }

	private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
	private val volumeNumRegex = Regex("""vol(ume)?\s*(\d+)""", RegexOption.IGNORE_CASE)

	override suspend fun getRelatedManga(seed: Manga): List<Manga> = coroutineScope {
		val document = client.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		val total = document.select(
			"section.m-related a[href*=/manga/], .side-manga:not(:has(.head:contains(trending))) .unit",
		).size
		val mangas = ArrayList<Manga>(total)

		// "Related Manga"
		document.select("section.m-related a[href*=/manga/]").map {
			async {
				val url = it.attrAsRelativeUrl("href")

				val mangaDocument = client
					.httpGet(url.toAbsoluteUrl(domain))
					.parseHtml()

				val chaptersInManga = mangaDocument.select(".m-list div.tab-content .list-menu .dropdown-item")
					.map { i -> i.attr("data-code").lowercase() }


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
					altTitles = emptySet(),
					largeCoverUrl = null,
					authors = emptySet(),
					contentRating = null,
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
					altTitles = emptySet(),
					largeCoverUrl = null,
					authors = emptySet(),
					contentRating = null,
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

					client.httpGet(url)
						.parseHtml().parseMangaList()
				}
			}.awaitAll().flatten()
		}
	}

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val chapterId = chapter.url.substringAfterLast('/')
        val vrf = VrfGenerator.generate("chapter@$chapterId")

        val images = client
            .httpGet("https://$domain/ajax/read/chapter/$chapterId?vrf=$vrf")
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

private object VrfGenerator {
    private fun atob(data: String): ByteArray = Base64.getDecoder().decode(data)

    private fun btoa(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    private fun rc4(key: ByteArray, input: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0

        // KSA
        for (i in 0..255) {
            j = (j + s[i] + key[i % key.size].toInt().and(0xFF)) and 0xFF
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }

        // PRGA
        val output = ByteArray(input.size)
        var i = 0
        j = 0
        for (y in input.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            val k = s[(s[i] + s[j]) and 0xFF]
            output[y] = (input[y].toInt() xor k).toByte()
        }
        return output
    }

    private fun transform(
        input: ByteArray,
        initSeedBytes: ByteArray,
        prefixKeyBytes: ByteArray,
        prefixLen: Int,
        schedule: List<(Int) -> Int>,
    ): ByteArray {
        val out = mutableListOf<Byte>()
        for (i in input.indices) {
            if (i < prefixLen) {
                out.add(prefixKeyBytes[i])
            }
            val transformed = schedule[i % 10](
                (input[i].toInt() xor initSeedBytes[i % 32].toInt()) and 0xFF,
            ) and 0xFF
            out.add(transformed.toByte())
        }
        return out.toByteArray()
    }

    private val scheduleC = listOf<(Int) -> Int>(
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c xor 241) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c xor 8) and 0xFF },
    )

    private val scheduleY = listOf<(Int) -> Int>(
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c xor 163) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c xor 83) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
    )

    private val scheduleB = listOf<(Int) -> Int>(
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c xor 8) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c xor 163) and 0xFF },
    )

    private val scheduleJ = listOf<(Int) -> Int>(
        { c -> (c + 223) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c xor 83) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c xor 83) and 0xFF },
    )

    private val scheduleE = listOf<(Int) -> Int>(
        { c -> (c + 82) and 0xFF },
        { c -> (c xor 83) and 0xFF },
        { c -> (c xor 163) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c xor 8) and 0xFF },
        { c -> (c xor 241) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c + 176) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
    )

    private val rc4Keys = mapOf(
        "l" to "u8cBwTi1CM4XE3BkwG5Ble3AxWgnhKiXD9Cr279yNW0=",
        "g" to "t00NOJ/Fl3wZtez1xU6/YvcWDoXzjrDHJLL2r/IWgcY=",
        "B" to "S7I+968ZY4Fo3sLVNH/ExCNq7gjuOHjSRgSqh6SsPJc=",
        "m" to "7D4Q8i8dApRj6UWxXbIBEa1UqvjI+8W0UvPH9talJK8=",
        "F" to "0JsmfWZA1kwZeWLk5gfV5g41lwLL72wHbam5ZPfnOVE=",
    )

    private val seeds32 = mapOf(
        "A" to "pGjzSCtS4izckNAOhrY5unJnO2E1VbrU+tXRYG24vTo=",
        "V" to "dFcKX9Qpu7mt/AD6mb1QF4w+KqHTKmdiqp7penubAKI=",
        "N" to "owp1QIY/kBiRWrRn9TLN2CdZsLeejzHhfJwdiQMjg3w=",
        "P" to "H1XbRvXOvZAhyyPaO68vgIUgdAHn68Y6mrwkpIpEue8=",
        "k" to "2Nmobf/mpQ7+Dxq1/olPSDj3xV8PZkPbKaucJvVckL0=",
    )

    private val prefixKeys = mapOf(
        "O" to "Rowe+rg/0g==",
        "v" to "8cULcnOMJVY8AA==",
        "L" to "n2+Og2Gth8Hh",
        "p" to "aRpvzH+yoA==",
        "W" to "ZB4oBi0=",
    )

    fun generate(input: String): String {
        var bytes = input.toByteArray()
        // RC4 1
        bytes = rc4(atob(rc4Keys["l"]!!), bytes)

        // Step C1
        bytes = transform(bytes, atob(seeds32["A"]!!), atob(prefixKeys["O"]!!), 7, scheduleC)

        // RC4 2
        bytes = rc4(atob(rc4Keys["g"]!!), bytes)

        // Step Y
        bytes = transform(bytes, atob(seeds32["V"]!!), atob(prefixKeys["v"]!!), 10, scheduleY)

        // RC4 3
        bytes = rc4(atob(rc4Keys["B"]!!), bytes)

        // Step B
        bytes = transform(bytes, atob(seeds32["N"]!!), atob(prefixKeys["L"]!!), 9, scheduleB)

        // RC4 4
        bytes = rc4(atob(rc4Keys["m"]!!), bytes)

        // Step J
        bytes = transform(bytes, atob(seeds32["P"]!!), atob(prefixKeys["p"]!!), 7, scheduleJ)

        // RC4 5
        bytes = rc4(atob(rc4Keys["F"]!!), bytes)

        // Step E
        bytes = transform(bytes, atob(seeds32["k"]!!), atob(prefixKeys["W"]!!), 5, scheduleE)

        // Base64URL encode
        return btoa(bytes)
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }
}

public object SSLUtils {
    public val trustAllCerts: Array<TrustManager> = arrayOf(@Suppress("CustomX509TrustManager")
    object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
    })

    public val sslSocketFactory: SSLSocketFactory? = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, SecureRandom())
    }.socketFactory

    public val trustManager: X509TrustManager = trustAllCerts[0] as X509TrustManager
}
