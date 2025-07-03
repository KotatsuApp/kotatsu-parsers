package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import androidx.collection.MutableIntLongMap
import androidx.collection.MutableIntObjectMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okhttp3.internal.headersContentLength
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections.emptyList
import java.util.concurrent.TimeUnit

private const val DOMAIN_UNAUTHORIZED = "e-hentai.org"
private const val DOMAIN_AUTHORIZED = "exhentai.org"
private val TAG_PREFIXES = arrayOf("male:", "female:", "other:")

@MangaSourceParser("EXHENTAI", "ExHentai", type = ContentType.HENTAI)
internal class ExHentaiParser(
	context: MangaLoaderContext,
) : LegacyPagedMangaParser(context, MangaParserSource.EXHENTAI, pageSize = 25), MangaParserAuthProvider, Interceptor {

	override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.NEWEST)

	override val configKeyDomain: ConfigKey.Domain
		get() {
			val isAuthorized = checkAuth()
			return ConfigKey.Domain(
				if (isAuthorized) DOMAIN_AUTHORIZED else DOMAIN_UNAUTHORIZED,
				if (isAuthorized) DOMAIN_UNAUTHORIZED else DOMAIN_AUTHORIZED,
			)
		}

	override val authUrl: String
		get() = "https://${domain}/bounce_login.php"

	private val ratingPattern = Regex("-?[0-9]+px")
	private val titleCleanupPattern = Regex("(\\[.*?]|\\([C0-9]*\\))")
	private val spacesCleanupPattern = Regex("(^\\s+|\\s+\$|\\s+(?=\\s))")
	private val authCookies = arrayOf("ipb_member_id", "ipb_pass_hash")
	private val suspiciousContentKey = ConfigKey.ShowSuspiciousContent(false)
	private val nextPages = MutableIntObjectMap<MutableIntLongMap>()

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun isAuthorized(): Boolean = checkAuth()

	init {
		context.cookieJar.insertCookies(DOMAIN_AUTHORIZED, "nw=1", "sl=dm_2")
		context.cookieJar.insertCookies(DOMAIN_UNAUTHORIZED, "nw=1", "sl=dm_2")
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = mapTags(),
		availableContentTypes = EnumSet.of(
			ContentType.DOUJINSHI,
			ContentType.MANGA,
			ContentType.ARTIST_CG,
			ContentType.GAME_CG,
			ContentType.COMICS,
			ContentType.IMAGE_SET,
			ContentType.OTHER,
		),
		availableLocales = setOf(
			Locale.JAPANESE,
			Locale.ENGLISH,
			Locale.CHINESE,
			Locale("nl"),
			Locale.FRENCH,
			Locale.GERMAN,
			Locale("hu"),
			Locale.ITALIAN,
			Locale("kr"),
			Locale("pl"),
			Locale("pt"),
			Locale("ru"),
			Locale("es"),
			Locale("th"),
			Locale("vi"),
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return getListPage(page, order, filter, updateDm = false)
	}

	private suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
		updateDm: Boolean,
	): List<Manga> {
		val next = synchronized(nextPages) {
			nextPages[filter.hashCode()]?.getOrDefault(page, 0L) ?: 0L
		}

		if (page > 0 && next == 0L) {
			assert(false) { "Page timestamp not found" }
			return emptyList()
		}

		val url = urlBuilder()
		url.addEncodedQueryParameter("next", next.toString())
		url.addQueryParameter("f_search", filter.toSearchQuery())

		val fCats = filter.types.toFCats()
		if (fCats != 0) {
			url.addEncodedQueryParameter("f_cats", (1023 - fCats).toString())
		}
		if (updateDm) {
			// by unknown reason cookie "sl=dm_2" is ignored, so, we should request it again
			url.addQueryParameter("inline_set", "dm_e")
		}
		url.addQueryParameter("advsearch", "1")
		if (config[suspiciousContentKey]) {
			url.addQueryParameter("f_sh", "on")
		}
		val body = webClient.httpGet(url.build()).parseHtml().body()
		val root = body.selectFirst("table.itg")?.selectFirst("tbody")
		if (root == null) {
			if (updateDm) {
				if (body.getElementsContainingText("No hits found").isNotEmpty()) {
					return emptyList()
				} else {
					body.parseFailed("Cannot find root")
				}
			} else {
				return getListPage(page, order, filter, updateDm = true)
			}
		}
		val nextTimestamp = getNextTimestamp(body)
		synchronized(nextPages) {
			nextPages.getOrPut(filter.hashCode()) {
				MutableIntLongMap()
			}.put(page + 1, nextTimestamp)
		}

		return root.children().mapNotNull { tr ->
			if (tr.childrenSize() != 2) return@mapNotNull null
			val (td1, td2) = tr.children()
			val gLink = td2.selectFirstOrThrow("div.glink")
			val a = gLink.parents().select("a").first() ?: gLink.parseFailed("link not found")
			val href = a.attrAsRelativeUrl("href")
			val tagsDiv = gLink.nextElementSibling() ?: gLink.parseFailed("tags div not found")
			val rawTitle = gLink.text()
			val author = tagsDiv.getElementsContainingOwnText("artist:").first()
				?.nextElementSibling()?.textOrNull()
			Manga(
				id = generateUid(href),
				title = rawTitle.cleanupTitle(),
				altTitles = emptySet(),
				url = href,
				publicUrl = a.absUrl("href"),
				rating = td2.selectFirst("div.ir")?.parseRating() ?: RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = td1.selectFirst("img")?.attrAsAbsoluteUrlOrNull("src"),
				tags = tagsDiv.parseTags(),
				state = when {
					rawTitle.contains("(ongoing)", ignoreCase = true) -> MangaState.ONGOING
					else -> null
				},
				authors = setOfNotNull(author),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirstOrThrow("div.gm")
		val cover = root.getElementById("gd1")?.children()?.first()
		val title = root.getElementById("gd2")
		val tagList = root.getElementById("taglist")
		val tabs = doc.body().selectFirst("table.ptt")?.selectFirst("tr")
		val gd3 = root.getElementById("gd3")
		val lang = gd3
			?.selectFirst("tr:contains(Language)")
			?.selectFirst(".gdt2")?.ownTextOrNull()
		val uploadDate = gd3
			?.selectFirst("tr:contains(Posted)")
			?.selectFirst(".gdt2")?.ownTextOrNull()
			.let { SimpleDateFormat("yyyy-MM-dd HH:mm", sourceLocale).tryParse(it) }
		val uploader = gd3
			?.getElementsByAttributeValueContaining("href", "/uploader/")
			?.firstOrNull()
			?.ownTextOrNull()
		val tags = tagList?.parseTags().orEmpty()

		return manga.copy(
			title = title?.getElementById("gn")?.text()?.cleanupTitle() ?: manga.title,
			altTitles = setOfNotNull(title?.getElementById("gj")?.text()?.cleanupTitle()?.nullIfEmpty()),
			publicUrl = doc.baseUri().ifEmpty { manga.publicUrl },
			rating = root.getElementById("rating_label")?.text()
				?.substringAfterLast(' ')
				?.toFloatOrNull()
				?.div(5f) ?: manga.rating,
			largeCoverUrl = cover?.styleValueOrNull("background")?.cssUrl(),
			tags = manga.tags + tags,
			description = tagList?.select("tr")?.joinToString("<br>") { tr ->
				val (tc, td) = tr.children()
				val subTags = td.select("a").joinToString { it.html() }
				"<b>${tc.html()}</b> $subTags"
			},
			chapters = tabs?.select("a")?.findLast { a ->
				a.text().toIntOrNull() != null
			}?.let { a ->
				val count = a.text().toInt()
				val chapters = ChaptersListBuilder(count)
				for (i in 1..count) {
					val url = "${manga.url}?p=${i - 1}"
					chapters += MangaChapter(
						id = generateUid(url),
						title = null,
						number = i.toFloat(),
						volume = 0,
						url = url,
						uploadDate = uploadDate,
						source = source,
						scanlator = uploader,
						branch = lang,
					)
				}
				chapters.toList()
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("gdt")
		return root.select("a").map { a ->
			val url = a.attrAsRelativeUrl("href")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = a.children().firstOrNull()?.extractPreview(),
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.body().requireElementById("img").attrAsAbsoluteUrl("src")
	}

	@Suppress("SpellCheckingInspection")
	private val tags: String
		get() = "ahegao,anal,angel,apron,bandages,bbw,bdsm,beauty mark,big areolae,big ass,big breasts,big clit,big lips," +
			"big nipples,bikini,blackmail,bloomers,blowjob,bodysuit,bondage,breast expansion,bukkake,bunny girl,business suit," +
			"catgirl,centaur,cheating,chinese dress,christmas,collar,corset,cosplaying,cowgirl,crossdressing,cunnilingus," +
			"dark skin,daughter,deepthroat,defloration,demon girl,double penetration,dougi,dragon,drunk,elf,exhibitionism,farting," +
			"females only,femdom,filming,fingering,fishnets,footjob,fox girl,furry,futanari,garter belt,ghost,giantess," +
			"glasses,gloves,goblin,gothic lolita,growth,guro,gyaru,hair buns,hairy,hairy armpits,handjob,harem,hidden sex," +
			"horns,huge breasts,humiliation,impregnation,incest,inverted nipples,kemonomimi,kimono,kissing,lactation," +
			"latex,leg lock,leotard,lingerie,lizard girl,maid,masked face,masturbation,midget,miko,milf,mind break," +
			"mind control,monster girl,mother,muscle,nakadashi,netorare,nose hook,nun,nurse,oil,paizuri,panda girl," +
			"pantyhose,piercing,pixie cut,policewoman,ponytail,pregnant,rape,rimjob,robot,scat,lolicon,schoolgirl uniform," +
			"sex toys,shemale,sister,small breasts,smell,sole dickgirl,sole female,squirting,stockings,sundress,sweating," +
			"swimsuit,swinging,tail,tall girl,teacher,tentacles,thigh high boots,tomboy,transformation,twins,twintails," +
			"unusual pupils,urination,vore,vtuber,widow,wings,witch,wolf girl,x-ray,yuri,zombie,sole male,males only,yaoi," +
			"tomgirl,tall man,oni,shotacon,prostate massage,policeman,males only,huge penis,fox boy,feminization,dog boy,dickgirl on male,big penis"

	private fun mapTags(): Set<MangaTag> {
		val tagElements = tags.split(",")
		val result = ArraySet<MangaTag>(tagElements.size)
		for (tag in tagElements) {
			val el = tag.trim()
			if (el.isEmpty()) continue
			result += MangaTag(
				title = el.toTitleCase(Locale.ENGLISH),
				key = el,
				source = source,
			)
		}
		return result
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		if (response.headersContentLength() <= 256) {
			val text = response.peekBody(256).use { it.string() }
			if (text.contains("IP address has been temporarily banned", ignoreCase = true)) {
				val hours = Regex("([0-9]+) hours?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0
				val minutes = Regex("([0-9]+) minutes?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0
				val seconds = Regex("([0-9]+) seconds?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0
				response.closeQuietly()
				throw TooManyRequestExceptions(
					url = response.request.url.toString(),
					retryAfter = TimeUnit.HOURS.toMillis(hours)
						+ TimeUnit.MINUTES.toMillis(minutes)
						+ TimeUnit.SECONDS.toMillis(seconds),
				)
			}
		}
		val imageRect = response.request.url.fragment?.split(',')
		if (imageRect != null && imageRect.size == 4) {
			// rect: top,left,right,bottom
			return context.redrawImageResponse(response) { bitmap ->
				val srcRect = Rect(
					left = imageRect[0].toInt(),
					top = imageRect[1].toInt(),
					right = imageRect[2].toInt(),
					bottom = imageRect[3].toInt(),
				)
				val dstRect = Rect(0, 0, srcRect.width, srcRect.height)
				val result = context.createBitmap(dstRect.width, dstRect.height)
				result.drawBitmap(bitmap, srcRect, dstRect)
				result
			}
		}
		return response
	}

	private fun Locale.toLanguagePath() = when (language) {
		else -> getDisplayLanguage(Locale.ENGLISH).lowercase()
	}

	override suspend fun getUsername(): String {
		val doc = webClient.httpGet("https://forums.$DOMAIN_UNAUTHORIZED/").parseHtml().body()
		val username = doc.getElementById("userlinks")
			?.getElementsByAttributeValueContaining("href", "showuser=")
			?.firstOrNull()
			?.ownText()
			?: if (doc.getElementById("userlinksguest") != null) {
				throw AuthRequiredException(source)
			} else {
				doc.parseFailed()
			}
		return username
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
		keys.add(suspiciousContentKey)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val query = seed.title
		return getListPage(
			page = 0,
			order = defaultSortOrder,
			filter = MangaListFilter(query = query),
		)
	}

	private fun isAuthorized(domain: String): Boolean {
		val cookies = context.cookieJar.getCookies(domain).mapToSet { x -> x.name }
		return authCookies.all { it in cookies }
	}

	private fun Element.parseRating(): Float {
		return runCatching {
			val style = requireNotNull(attr("style"))
			val (v1, v2) = ratingPattern.findAll(style).toList()
			var p1 = v1.groupValues.first().dropLast(2).toInt()
			val p2 = v2.groupValues.first().dropLast(2).toInt()
			if (p2 != -1) {
				p1 += 8
			}
			(80 - p1) / 80f
		}.getOrDefault(RATING_UNKNOWN)
	}

	private fun String.cleanupTitle(): String {
		return replace(titleCleanupPattern, "")
			.replace(spacesCleanupPattern, "")
	}

	private fun Element.parseTags(): Set<MangaTag> {

		fun Element.parseTag() = textOrNull()?.let {
			MangaTag(title = it.toTitleCase(Locale.ENGLISH), key = it, source = source)
		}

		val result = ArraySet<MangaTag>()
		for (prefix in TAG_PREFIXES) {
			getElementsByAttributeValueStarting("id", "ta_$prefix").mapNotNullTo(result, Element::parseTag)
			getElementsByAttributeValueStarting("title", prefix).mapNotNullTo(result, Element::parseTag)
		}
		return result
	}

	private fun Element.extractPreview(): String? {
		val bg = backgroundOrNull() ?: return null
		return buildString {
			append(bg.url)
			append('#')
			// rect: left,top,right,bottom
			append(bg.left)
			append(',')
			append(bg.top)
			append(',')
			append(bg.right)
			append(',')
			append(bg.bottom)
		}
	}

	private fun getNextTimestamp(root: Element): Long {
		return root.getElementById("unext")
			?.attrAsAbsoluteUrlOrNull("href")
			?.toHttpUrlOrNull()
			?.queryParameter("next")
			?.toLongOrNull() ?: 1
	}

	private fun MangaListFilter.toSearchQuery(): String? {
		if (isEmpty()) {
			return null
		}
		val joiner = StringUtil.StringJoiner(" ")
		if (!query.isNullOrEmpty()) {
			joiner.add(query)
		}
		for (tag in tags) {
			if (tag.key.isNumeric()) {
				continue
			}
			joiner.add("tag:\"")
			joiner.append(tag.key)
			joiner.append("\"$")
		}
		for (tag in tagsExclude) {
			if (tag.key.isNumeric()) {
				continue
			}
			joiner.add("-tag:\"")
			joiner.append(tag.key)
			joiner.append("\"$")
		}
		locale?.let { lc ->
			joiner.add("language:\"")
			joiner.append(lc.toLanguagePath())
			joiner.append("\"$")
		}
		if (!author.isNullOrEmpty()) {
			joiner.add("artist:\"")
			joiner.append(author)
			joiner.append("\"$")
		}
		return joiner.complete().nullIfEmpty()
	}

	private fun Collection<ContentType>.toFCats(): Int = fold(0) { acc, ct ->
		val cat: Int = when (ct) {
			ContentType.DOUJINSHI -> 2
			ContentType.MANGA -> 4
			ContentType.ARTIST_CG -> 8
			ContentType.GAME_CG -> 16
			ContentType.COMICS -> 512
			ContentType.IMAGE_SET -> 32
			else -> 449 // 1 or 64 or 128 or 256
		}
		acc or cat
	}

	private fun checkAuth(): Boolean {
		val authorized = isAuthorized(DOMAIN_UNAUTHORIZED)
		if (authorized) {
			if (!isAuthorized(DOMAIN_AUTHORIZED)) {
				context.cookieJar.copyCookies(
					DOMAIN_UNAUTHORIZED,
					DOMAIN_AUTHORIZED,
					authCookies,
				)
				context.cookieJar.insertCookies(DOMAIN_AUTHORIZED, "yay=louder")
			}
			return true
		}
		return false
	}
}
