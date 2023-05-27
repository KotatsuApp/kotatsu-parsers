package org.koitharu.kotatsu.parsers.site

import okhttp3.Headers
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("JAPSCAN", "JapScan", "fr")
internal class JapScanParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.JAPSCAN, 30) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	override val configKeyDomain = ConfigKey.Domain("www.japscan.lol", "japscan.ws")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/113.0")
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		require(tags.isNullOrEmpty()) { "Tags are not supported by this source" }
		if (!query.isNullOrEmpty()) {
			return if (page == paginator.firstPage) getListPageSearch(query) else emptyList()
		}
		val url = urlBuilder()
			.addPathSegment("mangas")
			.addPathSegment(page.toString())
			.build()
		val root = webClient.httpGet(url).parseHtml()
			.requireElementById("main")
			.selectFirstOrThrow(".flex-wrap")
		return root.select(".mainTitle")
			.map { p ->
				val div = checkNotNull(p.parent())
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				Manga(
					id = generateUid(href),
					title = p.text(),
					altTitle = null,
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
					tags = setOf(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("main")
		val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
		return manga.copy(
			altTitle = root.tableValue("Nom Original:"),
			isNsfw = root.tableValue("Âge conseillé :")?.extractIntOrNull().let { it != null && it >= 18 },
			tags = root.tableValue("Type(s):")?.split(", ")?.mapNotNullToSet {
				it.toTag()
			}.orEmpty() + root.tableValue("Genre(s):")?.split(", ")?.mapNotNullToSet {
				it.toTag()
			}.orEmpty(),
			state = when (root.tableValue("Statut:")) {
				"En Cours" -> MangaState.ONGOING
				"Terminé", "Abondonné" -> MangaState.FINISHED
				else -> null
			},
			author = root.tableValue("Artiste(s):")?.substringBefore(','),
			description = root.selectFirst("p.list-group-item-primary")?.html(),
			chapters = root.requireElementById("chapters_list")
				.select("div.chapters_list")
				.mapChapters(reversed = true) { i, div ->
					val a = div.selectFirst("a") ?: return@mapChapters null
					val href = a.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(div.selectFirst("span.float-right")?.text()),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(chapterUrl).parseHtml()
		val scriptUrl = doc.getElementsByTag("script").firstNotNullOf { script ->
			script.attrAsAbsoluteUrlOrNull("src")?.takeIf { it.contains("/zjs/") }
		}
		val embeddedData = doc.requireElementById("data").attr("data-data")
		val script = webClient.httpGet(scriptUrl).parseRaw()

		val sample = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toList()
		val keyRegex = Regex("""'([\dA-Z]{62})'""", RegexOption.IGNORE_CASE)

		val keyTables = keyRegex.findAll(script)
			.mapNotNullTo(ArrayList(2)) { match ->
				match.groupValues[1].takeIf {
					it.toList().sorted() == sample
				}
			}
		check(keyTables.size == 2)

		var error: Exception? = null
		repeat(2) { i ->
			val key = keyTables[i].zip(keyTables[1 - i]).toMap()
			try {
				val unscrambledData = embeddedData.map { key[it] ?: it }.joinToString("")
				if (unscrambledData.startsWith("ey")) {
					val array = JSONObject(context.decodeBase64(unscrambledData).toString(Charsets.UTF_8))
						.getJSONArray("imagesLink")
					val result = ArrayList<MangaPage>(array.length())
					repeat(array.length()) { index ->
						val url = array.getString(index)
						result += MangaPage(
							id = generateUid(url),
							url = url,
							preview = null,
							source = source,
						)
					}
					return result
				}
			} catch (e: Exception) {
				error = e
			}
		}
		throw (error ?: ParseException("Cannot decode pages list", chapterUrl))
	}

	override suspend fun getTags(): Set<MangaTag> {
		return emptySet() // not supported
	}

	private suspend fun getListPageSearch(
		query: String,
	): List<Manga> {
		val json = webClient.httpPost(
			"https://$domain/live-search/",
			mapOf("search" to query.urlEncoded()),
		).parseJsonArray()
		return json.mapJSON { jo ->
			val url = jo.getString("url")
			Manga(
				id = generateUid(url),
				title = jo.getString("name"),
				altTitle = jo.getStringOrNull("alternate_names")?.substringBefore(','),
				url = url,
				publicUrl = url.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = jo.getString("image").toAbsoluteUrl(domain),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	private fun Element.tableValue(label: String): String? {
		return getElementsMatchingOwnText(label).firstOrNull()?.parent()?.ownTextOrNull()
	}

	private fun String.extractIntOrNull(): Int? = this.filter(Char::isDigit).toIntOrNull()

	private fun String.toTag() = MangaTag(
		title = this.toTitleCase(sourceLocale),
		key = this.replace(' ', '-').lowercase(sourceLocale),
		source = source,
	)
}
