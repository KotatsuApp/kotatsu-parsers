package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
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
		.add("User-Agent", UserAgents.FIREFOX_DESKTOP)
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
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.requireElementById("main")
			.selectFirstOrThrow("div.d-flex.flex-wrap")
		return root.select("div.p-2")
			.map { div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				Manga(
					id = generateUid(href),
					title = div.selectFirstOrThrow(".mainTitle").text(),
					altTitle = null,
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = div.selectFirstOrThrow("img.img-fluid").attrAsAbsoluteUrl("src"),
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

	private fun extractQuotedContent(input: String): List<String> {
		val regex = Regex("'(.*?)'")
		return regex.findAll(input).map { it.groupValues[1] }.toList()
	}

	private fun listJSToKey(jsList: MutableList<String>, offsettab: Int, listKey: List<String>): MutableList<String> {
		for (i in 0 until jsList.size) {
			if (jsList[i].contains("0x")) {
				var decoupeHexa = jsList[i].split("('")[1]
				decoupeHexa = decoupeHexa.split("')")[0]
				var indexkey = Integer.decode(decoupeHexa) - offsettab - 1
				if (indexkey < 0) {
					indexkey = listKey.size - 1
				}
				jsList[i] = listKey[indexkey]
			}
		}

		return jsList
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(chapterUrl).parseHtml()
		val scriptUrl = doc.getElementsByTag("script").firstNotNullOf { script ->
			script.attrAsAbsoluteUrlOrNull("src")?.takeIf { it.contains("/zjs/") }
		}
		val embeddedData = doc.requireElementById("data").attr("data-data")
		val script = webClient.httpGet(scriptUrl).parseRaw()

		var tabKey = "'" + script.split("=['")[1]
		tabKey = tabKey.split("];")[0]
		val listKey = tabKey.split("','").toMutableList()

		var decoupeOffset = script.split("-0x")[1]
		decoupeOffset = "0x" + decoupeOffset.split(";")[0]

		val offsettab = Integer.decode(decoupeOffset)

		var decoupeFuncOrder = script.split("while(!![])")[1]
		decoupeFuncOrder = decoupeFuncOrder.split("if")[0]

		val listKeyOrder = extractQuotedContent(decoupeFuncOrder).toMutableList()

		if (listKeyOrder.size < 3) {
			throw Exception("L'ordre des clés n'a pas pu être déterminé")
		}
		var goodorder = false
		for (i in 0 until listKey.size) {
			for (z in 0 until listKeyOrder.size) {
				if (listKey[Integer.decode(listKeyOrder[z]) - offsettab - 1].contains("[0-9]".toRegex())) {
					goodorder = true
				} else {
					goodorder = false
					break
				}
			}

			if (goodorder) {
				break
			}

			val firstElement = listKey.removeAt(0)
			listKey.add(firstElement)
		}

		if (!goodorder) {
			throw Exception("L'ordre des clés n'a pas pu être déterminé")
		}

		val zjscalc = script.split("/[A-Z0-9]/gi,")[1]

		val calc1 = zjscalc.split(",")[0]
		var calc1tab = calc1.split("+").toMutableList()
		calc1tab = listJSToKey(calc1tab, offsettab, listKey)

		val calc2 = zjscalc.split(",")[1]
		var calc2tab = calc2.split("+").toMutableList()
		calc2tab = listJSToKey(calc2tab, offsettab, listKey)

		var key1 = calc1tab.joinToString("")
		var key2 = calc2tab.joinToString("")

		key1 = key1.filterNot { c -> c == '\'' || c == ' ' }
		key2 = key2.filterNot { c -> c == '\'' || c == ' ' }

		val keyTables = listOf(
			key1.reversed(),
			key2.reversed(),
		)

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
