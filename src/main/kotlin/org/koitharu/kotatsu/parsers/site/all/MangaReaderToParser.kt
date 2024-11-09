package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.MutableIntObjectMap
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

@MangaSourceParser("MANGAREADERTO", "MangaReader.To")
internal class MangaReaderToParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MANGAREADERTO, 16),
	Interceptor, MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain("mangareader.to")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val authUrl: String
		get() = "https://${domain}/home"

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(domain).any {
				it.name.contains("connect.sid")
			}
		}

	// It will be easier to connect to a manga page, as the source redirects to a lot of advertising.
	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${domain}/user/profile").parseHtml().body()
		return body.getElementById("pro5-name")?.attr("value") ?: body.parseFailed("Cannot find username")
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
	)

	val tags = suspendLazy(soft = true) {
		val document = webClient.httpGet("https://$domain/filter").parseHtml()

		document.select("div.f-genre-item").map {
			MangaTag(
				title = it.ownText().toTitleCase(sourceLocale),
				key = it.attr("data-id"),
				source = source,
			)
		}.associateBy { it.title }
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = tags.get().values.toSet(),
		availableStates = EnumSet.allOf(MangaState::class.java),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = "https://$domain".toHttpUrl().newBuilder().apply {
			when {
				!filter.query.isNullOrEmpty() -> {
					addPathSegment("search")
					addQueryParameter("keyword", filter.query)
					addQueryParameter("page", page.toString())
				}

				else -> {
					addPathSegment("filter")
					addQueryParameter("page", page.toString())
					addQueryParameter(
						name = "sort",
						value = when (order) {
							SortOrder.POPULARITY -> "most-viewed"
							SortOrder.RATING -> "score"
							SortOrder.UPDATED -> "latest-updated"
							SortOrder.NEWEST -> "release-date"
							SortOrder.ALPHABETICAL -> "name-az"
							else -> "default"
						},
					)
					addQueryParameter("genres", filter.tags.joinToString(",") { it.key })
					addQueryParameter(
						name = "status",
						value = when (filter.states.oneOrThrowIfMany()) {
							MangaState.ONGOING -> "2"
							MangaState.FINISHED -> "1"
							MangaState.ABANDONED -> "4"
							MangaState.PAUSED -> "3"
							MangaState.UPCOMING -> "5"
							null -> ""
						},
					)
				}
			}
		}.build()

		val document = webClient.httpGet(url).parseHtml()

		return document.select(".manga_list-sbs .manga-poster").map {
			val mangaUrl = it.attrAsRelativeUrl("href")
			val thumb = it.select("img")
			Manga(
				id = generateUid(mangaUrl),
				url = mangaUrl,
				publicUrl = mangaUrl.toAbsoluteUrl(domain),
				title = thumb.attr("alt"),
				coverUrl = thumb.attr("src"),
				source = source,
				altTitle = null,
				author = null,
				isNsfw = false,
				rating = RATING_UNKNOWN,
				state = null,
				tags = emptySet(),
			)
		}
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val document = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		return document.select(".block_area_authors-other .manga_list-sbs .manga-poster, .featured-block-ul .manga-poster")
			.map {
				val mangaUrl = it.attrAsRelativeUrl("href")
				val thumb = it.select("img")
				Manga(
					id = generateUid(mangaUrl),
					url = mangaUrl,
					publicUrl = mangaUrl.toAbsoluteUrl(domain),
					title = thumb.attr("alt"),
					coverUrl = thumb.attr("src"),
					source = source,
					altTitle = null,
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
			title = document.selectFirst("h2.manga-name")!!.ownText(),
			altTitle = document.selectFirst("div.manga-name-or")?.ownText(),
			rating = document.selectFirst("div.anisc-info .item:contains(score:) > .name")
				?.text()?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN,
			coverUrl = document.selectFirst(".manga-poster > img")!!.attr("src"),
			tags = document.select("div.genres > a[href*=/genre/]").mapNotNullToSet {
				val tag = it.ownText()
				if (tag == "Hentai" || tag == "Ecchi") {
					isNsfw = true
				}
				availableTags[tag]
			},
			isNsfw = isNsfw,
			state = document.selectFirst("div.anisc-info .item:contains(status:) > .name")
				?.text()?.let {
					when (it) {
						"Publishing" -> MangaState.ONGOING
						"Finished" -> MangaState.FINISHED
						"On Hiatus" -> MangaState.PAUSED
						"Discontinued" -> MangaState.ABANDONED
						"Not yet published" -> MangaState.UPCOMING
						else -> null
					}
				},
			author = document.select("div.anisc-info a[href*=/author/]")
				.joinToString { it.ownText().replace(", ", " ") },
			description = document.select("div.description").text(),
			chapters = parseChapters(document),
			source = source,
		)
	}

	private fun parseChapters(document: Document): List<MangaChapter> {
		val total =
			document.select(".chapters-list-ul > ul > li.chapter-item, .volume-list-ul div.lang-volumes > div.item").size
		val chapters = ChaptersListBuilder(total)

		document.select(".chapters-list-ul > ul").forEach { ul ->
			ul.select("li.chapter-item").reversed().forEach { li ->
				val a = li.selectFirst("a")!!

				chapters.add(
					MangaChapter(
						id = generateUid(a.attrAsRelativeUrl("href")),
						name = a.attr("title"),
						number = li.attr("data-number").toFloat(),
						volume = 0,
						url = a.attrAsRelativeUrl("href"),
						scanlator = null,
						uploadDate = 0L,
						branch = createBranchName(ul.id().substringBefore("-chapters"), "Chapters"),
						source = source,
					),
				)
			}
		}
		val numRegex = Regex("""(\d+)""")
		document.select(".volume-list-ul div.lang-volumes").forEach { div ->
			div.select("div.item > div.manga-poster").reversed().forEach { vol ->
				val url = vol.selectFirst("a")!!.attrAsRelativeUrl("href")
				val name = vol.selectFirst("span")!!.ownText()
				chapters.add(
					MangaChapter(
						id = generateUid(url),
						name = name,
						number = numRegex.find(name)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f,
						volume = 0,
						url = url,
						scanlator = null,
						uploadDate = 0L,
						branch = createBranchName(div.id().substringBefore("-volumes"), "Volumes"),
						source = source,
					),
				)
			}
		}

		return chapters.toList()
	}

	private fun createBranchName(lang: String, type: String): String {
		val langCode = lang.substringBefore("-")

		return Locale(langCode).displayLanguage + " " + type
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val typeAndId = webClient.httpGet(chapter.url.toAbsoluteUrl(domain))
			.parseHtml()
			.selectFirst("#wrapper")!!.run {
				"${attr("data-reading-by")}/${attr("data-reading-id")}"
			}
		val document = webClient.httpGet("https://$domain/ajax/image/list/$typeAndId?quality=high")
			.parseJson()
			.getString("html")
			.let(Jsoup::parse)

		return document.select(".iv-card").map {
			val url = it.attr("data-url")
			MangaPage(
				id = generateUid(url),
				url = if (it.hasClass("shuffled")) {
					"$url#scrambled"
				} else {
					url
				},
				preview = null,
				source = source,
			)
		}
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)

		if (request.url.fragment != "scrambled") return response

		return context.redrawImageResponse(response, ::descramble)
	}

	private val memo = MutableIntObjectMap<IntArray>()

	private fun descramble(bitmap: Bitmap): Bitmap = synchronized(memo) {
		val width = bitmap.width
		val height = bitmap.height

		val result = context.createBitmap(width, height)

		val pieces = ArrayList<Piece>()
		for (y in 0 until height step PIECE_SIZE) {
			for (x in 0 until width step PIECE_SIZE) {
				val w = min(PIECE_SIZE, width - x)
				val h = min(PIECE_SIZE, height - y)
				pieces.add(Piece(x, y, w, h))
			}
		}

		val groups = pieces.groupBy { it.w shl 16 or it.h }

		for (group in groups.values) {
			val size = group.size

			val permutation = memo.getOrPut(size) {
				val random = SeedRandom("staystay")

				// https://github.com/webcaetano/shuffle-seed
				val indices = (0 until size).toMutableList()
				IntArray(size) { indices.removeAt((random.nextDouble() * indices.size).toInt()) }
			}

			for ((i, original) in permutation.withIndex()) {
				val src = group[i]
				val dst = group[original]

				val srcRect = Rect(src.x, src.y, src.x + src.w, src.y + src.h)
				val dstRect = Rect(dst.x, dst.y, dst.x + dst.w, dst.y + dst.h)

				result.drawBitmap(bitmap, srcRect, dstRect)
			}
		}

		return result
	}

	private class Piece(val x: Int, val y: Int, val w: Int, val h: Int)

	// https://github.com/davidbau/seedrandom
	private class SeedRandom(key: String) {
		private val input = ByteArray(RC4_WIDTH)
		private val buffer = ByteArray(RC4_WIDTH)
		private var pos = RC4_WIDTH

		private val rc4 = Cipher.getInstance("RC4").apply {
			init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(), "RC4"))
			update(input, 0, RC4_WIDTH, buffer) // RC4-drop[256]
		}

		fun nextDouble(): Double {
			var num = nextByte()
			var exp = 8
			while (num < 1L shl 52) {
				num = num shl 8 or nextByte()
				exp += 8
			}
			while (num >= 1L shl 53) {
				num = num ushr 1
				exp--
			}
			return Math.scalb(num.toDouble(), -exp)
		}

		private fun nextByte(): Long {
			if (pos == RC4_WIDTH) {
				rc4.update(input, 0, RC4_WIDTH, buffer)
				pos = 0
			}
			return buffer[pos++].toLong() and 0xFF
		}
	}
}

private const val RC4_WIDTH = 256
private const val PIECE_SIZE = 200
