package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@MangaSourceParser("BATOTO", "Bato.To")
internal class BatoToParser(context: MangaLoaderContext) : PagedMangaParser(
	context = context,
	source = MangaParserSource.BATOTO,
	pageSize = 60,
	searchPageSize = 20,
), MangaParserAuthProvider {

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val authUrl: String
		get() = "https://${domain}/signin"

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(domain).any {
				it.name.contains("skey")
			}
		}

	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${domain}/account/profiles").parseHtml().body()
		return body.selectFirst("ul.toggleMenu-content:has(.avatar):has(a) div.text-center div")?.text()
			?: body.parseFailed("Cannot find username")
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_YEAR,
		SortOrder.POPULARITY_MONTH,
		SortOrder.POPULARITY_WEEK,
		SortOrder.POPULARITY_TODAY,
		SortOrder.POPULARITY_HOUR,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isOriginalLocaleSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.allOf(MangaState::class.java),
		availableContentRating = EnumSet.of(ContentRating.SAFE),
		availableLocales = setOf(
			Locale.CHINESE, Locale.ENGLISH, Locale.US, Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN, Locale.JAPANESE,
			Locale("af"), Locale("ar"), Locale("az"), Locale("eu"), Locale("be"),
			Locale("bn"), Locale("bs"), Locale("bg"), Locale("my"), Locale("km"),
			Locale("ceb"), Locale("zh_hk"), Locale("zh_tw"), Locale("hr"), Locale("cs"),
			Locale("da"), Locale("nl"), Locale("eo"), Locale("et"), Locale("fil"),
			Locale("fi"), Locale("ka"), Locale("el"), Locale("ht"), Locale("he"),
			Locale("hi"), Locale("hu"), Locale("id"), Locale("kk"), Locale("ko"),
			Locale("lv"), Locale("ms"), Locale("ml"), Locale("mo"), Locale("mn"),
			Locale("ne"), Locale("no"), Locale("fa"), Locale("pl"), Locale("pt"),
			Locale("pt_br"), Locale("pt_pt"), Locale("ro"), Locale("ru"), Locale("sr"),
			Locale("si"), Locale("sk"), Locale("es"), Locale("es_419"), Locale("ta"),
			Locale("te"), Locale("th"), Locale("ti"), Locale("tr"), Locale("uk"),
			Locale("vi"), Locale("zu"),
		),
	)

	override val configKeyDomain = ConfigKey.Domain(
		"bato.to",
		"batocomic.com",
		"batocomic.net",
		"batocomic.org",
		"batotoo.com",
		"batotwo.com",
		"battwo.com",
		"comiko.net",
		"comiko.org",
		"mangatoto.com",
		"mangatoto.net",
		"mangatoto.org",
		"readtoto.com",
		"readtoto.net",
		"readtoto.org",
		"dto.to",
		"hto.to",
		"mto.to",
		"wto.to",
		"xbato.com",
		"xbato.net",
		"xbato.org",
		"zbato.com",
		"zbato.net",
		"zbato.org",
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		when {
			!filter.query.isNullOrEmpty() -> {
				return search(page, filter.query)
			}

			else -> {

				val url = buildString {
					append("https://")
					append(domain)

					append("/browse?sort=")
					when (order) {
						SortOrder.UPDATED -> append("update.za")
						SortOrder.POPULARITY -> append("views_a.za")
						SortOrder.NEWEST -> append("create.za")
						SortOrder.ALPHABETICAL -> append("title.az")
						SortOrder.POPULARITY_YEAR -> append("views_y.za")
						SortOrder.POPULARITY_MONTH -> append("views_m.za")
						SortOrder.POPULARITY_WEEK -> append("views_w.za")
						SortOrder.POPULARITY_TODAY -> append("views_d.za")
						SortOrder.POPULARITY_HOUR -> append("views_h.za")
						else -> append("update.za")
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&release=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "completed"
								MangaState.ABANDONED -> "cancelled"
								MangaState.PAUSED -> "hiatus"
								MangaState.UPCOMING -> "pending"
							},
						)
					}

					filter.locale?.let {
						append("&langs=")
						if (it.language == "in") {
							append("id")
						} else {
							append(it.language)
						}

					}

					filter.originalLocale?.let {
						append("&origs=")
						if (it.language == "in") {
							append("id")
						} else {
							append(it.language)
						}
					}

					append("&genres=")
					if (filter.tags.isNotEmpty()) {
						filter.tags.joinTo(this, ",") { it.key }
					}

					append("|")
					if (filter.tagsExclude.isNotEmpty()) {
						filter.tagsExclude.joinTo(this, ",") { it.key }
					}

					if (filter.contentRating.isNotEmpty()) {
						filter.contentRating.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									ContentRating.SAFE -> append(",gore,bloody,violence,ecchi,adult,mature,smut,hentai")
									else -> append("")
								},
							)
						}
					}

					append("&page=")
					append(page.toString())
				}

				return parseList(url, page)
			}
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
			.requireElementById("mainer")
		val details = root.selectFirstOrThrow(".detail-set")
		val attrs = details.selectFirst(".attr-main")?.select(".attr-item")?.associate {
			it.child(0).text() to it.child(1)
		}.orEmpty()
		return manga.copy(
			title = root.selectFirst("h3.item-title")?.text() ?: manga.title,
			isNsfw = !root.selectFirst("alert")?.getElementsContainingOwnText("NSFW").isNullOrEmpty(),
			largeCoverUrl = details.selectFirst("img[src]")?.absUrl("src"),
			description = details.getElementById("limit-height-body-summary")
				?.selectFirst(".limit-html")
				?.html(),
			tags = manga.tags + attrs["Genres:"]?.parseTags().orEmpty(),
			state = when (attrs["Original work:"]?.text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				"Cancelled" -> MangaState.ABANDONED
				"Hiatus" -> MangaState.PAUSED
				else -> manga.state
			},
			author = attrs["Authors:"]?.textOrNull() ?: manga.author,
			chapters = root.selectFirst(".episode-list")
				?.selectFirst(".main")
				?.children()
				?.mapChapters(reversed = true) { i, div ->
					div.parseChapter(i)
				}.orEmpty(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val scripts = webClient.httpGet(fullUrl).parseHtml().select("script")
		for (script in scripts) {
			val scriptSrc = script.html()
			val p = scriptSrc.indexOf("const imgHttps =")
			if (p == -1) continue
			val start = scriptSrc.indexOf('[', p)
			val end = scriptSrc.indexOf(';', start)
			if (start == -1 || end == -1) {
				continue
			}
			val images = JSONArray(scriptSrc.substring(start, end))
			val batoPass = scriptSrc.substringBetweenFirst("batoPass =", ";")?.trim(' ', '"', '\n')
				?: script.parseFailed("Cannot find batoPass")
			val batoWord = scriptSrc.substringBetweenFirst("batoWord =", ";")?.trim(' ', '"', '\n')
				?: script.parseFailed("Cannot find batoWord")
			val password = context.evaluateJs(batoPass)?.removeSurrounding('"')
				?: script.parseFailed("Cannot evaluate batoPass")
			val args = JSONArray(decryptAES(batoWord, password))
			val result = ArrayList<MangaPage>(images.length())
			repeat(images.length()) { i ->
				val url = images.getString(i)
				result += MangaPage(
					id = generateUid(url),
					url = if (args.length() == 0) url else url + "?" + args.getString(i),
					preview = null,
					source = source,
				)
			}
			return result
		}
		throw ParseException("Cannot find images list", fullUrl)
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val scripts = webClient.httpGet(
			"https://${domain}/browse",
		).parseHtml().selectOrThrow("script")
		for (script in scripts) {
			val genres = script.html().substringBetweenFirst("const _genres =", ";") ?: continue
			val jo = JSONObject(genres)
			val result = ArraySet<MangaTag>(jo.length())
			jo.keys().forEach { key ->
				val item = jo.getJSONObject(key)
				result += MangaTag(
					title = item.getString("text").toTitleCase(Locale.ENGLISH),
					key = item.getString("file"),
					source = source,
				)
			}
			return result
		}
		throw ParseException("Cannot find gernes list", scripts[0].baseUri())
	}

	private suspend fun search(page: Int, query: String): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/search?word=")
			append(query.replace(' ', '+'))
			append("&page=")
			append(page)
		}
		return parseList(url, page)
	}

	private fun getActivePage(body: Element): Int = body.select("nav ul.pagination > li.page-item.active")
		.lastOrNull()
		?.text()
		?.toIntOrNull() ?: body.parseFailed("Cannot determine current page")

	private suspend fun parseList(url: String, page: Int): List<Manga> {
		val body = webClient.httpGet(url).parseHtml().body()
		if (body.selectFirst(".browse-no-matches") != null) {
			return emptyList()
		}
		val activePage = getActivePage(body)
		if (activePage != page) {
			return emptyList()
		}
		val root = body.requireElementById("series-list")
		return root.children().map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val title = div.selectFirstOrThrow(".item-title").text()
			Manga(
				id = generateUid(href),
				title = title,
				altTitle = div.selectFirst(".item-alias")?.text()?.takeUnless { it == title },
				url = href,
				publicUrl = a.absUrl("href"),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirst("img[src]")?.absUrl("src").orEmpty(),
				largeCoverUrl = null,
				description = null,
				tags = div.selectFirst(".item-genre")?.parseTags().orEmpty(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	private fun Element.parseTags() = children().mapToSet { span ->
		val text = span.ownText()
		MangaTag(
			title = text.toTitleCase(),
			key = text.lowercase(Locale.ENGLISH).replace(' ', '_'),
			source = source,
		)
	}

	private fun Element.parseChapter(index: Int): MangaChapter? {
		val a = selectFirst("a.chapt") ?: return null
		val extra = selectFirst(".extra")
		val href = a.attrAsRelativeUrl("href")
		return MangaChapter(
			id = generateUid(href),
			name = a.text(),
			number = index + 1f,
			volume = 0,
			url = href,
			scanlator = extra?.getElementsByAttributeValueContaining("href", "/group/")?.text(),
			uploadDate = runCatching {
				parseChapterDate(extra?.select("i")?.lastOrNull()?.ownText())
			}.getOrDefault(0),
			branch = null,
			source = source,
		)
	}

	private fun parseChapterDate(date: String?): Long {
		if (date.isNullOrEmpty()) {
			return 0
		}
		val value = date.substringBefore(' ').toInt()
		val field = when {
			"sec" in date -> Calendar.SECOND
			"min" in date -> Calendar.MINUTE
			"hour" in date -> Calendar.HOUR
			"day" in date -> Calendar.DAY_OF_MONTH
			"week" in date -> Calendar.WEEK_OF_YEAR
			"month" in date -> Calendar.MONTH
			"year" in date -> Calendar.YEAR
			else -> return 0
		}
		val calendar = Calendar.getInstance()
		calendar.add(field, -value)
		return calendar.timeInMillis
	}

	private fun decryptAES(encrypted: String, password: String): String {
		val cipherData = context.decodeBase64(encrypted)
		val saltData = cipherData.copyOfRange(8, 16)
		val (key, iv) = generateKeyAndIV(
			keyLength = 32,
			ivLength = 16,
			iterations = 1,
			salt = saltData,
			password = password.toByteArray(StandardCharsets.UTF_8),
			md = MessageDigest.getInstance("MD5"),
		)
		val encryptedData = cipherData.copyOfRange(16, cipherData.size)
		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.DECRYPT_MODE, key, iv)
		return cipher.doFinal(encryptedData).toString(Charsets.UTF_8)
	}

	@Suppress("SameParameterValue")
	private fun generateKeyAndIV(
		keyLength: Int,
		ivLength: Int,
		iterations: Int,
		salt: ByteArray,
		password: ByteArray,
		md: MessageDigest,
	): Pair<SecretKeySpec, IvParameterSpec> {
		val digestLength = md.digestLength
		val requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength
		val generatedData = ByteArray(requiredLength)
		var generatedLength = 0
		md.reset()
		while (generatedLength < keyLength + ivLength) {
			if (generatedLength > 0) {
				md.update(generatedData, generatedLength - digestLength, digestLength)
			}
			md.update(password)
			md.update(salt, 0, 8)
			md.digest(generatedData, generatedLength, digestLength)
			repeat(iterations - 1) {
				md.update(generatedData, generatedLength, digestLength)
				md.digest(generatedData, generatedLength, digestLength)
			}
			generatedLength += digestLength
		}

		return SecretKeySpec(generatedData.copyOfRange(0, keyLength), "AES") to IvParameterSpec(
			if (ivLength > 0) {
				generatedData.copyOfRange(keyLength, keyLength + ivLength)
			} else byteArrayOf(),
		)
	}
}
