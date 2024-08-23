package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import androidx.collection.SparseArrayCompat
import androidx.collection.set
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.headersContentLength
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*
import java.util.Collections.emptyList
import java.util.concurrent.TimeUnit
import kotlin.math.pow

private const val DOMAIN_UNAUTHORIZED = "e-hentai.org"
private const val DOMAIN_AUTHORIZED = "exhentai.org"

@MangaSourceParser("EXHENTAI", "ExHentai", type = ContentType.HENTAI)
internal class ExHentaiParser(
	context: MangaLoaderContext,
) : PagedMangaParser(context, MangaParserSource.EXHENTAI, pageSize = 25), MangaParserAuthProvider, Interceptor {

	override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.NEWEST)
	override val isTagsExclusionSupported: Boolean = true

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain(
			if (isAuthorized) DOMAIN_AUTHORIZED else DOMAIN_UNAUTHORIZED,
			if (isAuthorized) DOMAIN_UNAUTHORIZED else DOMAIN_AUTHORIZED,
		)

	override val authUrl: String
		get() = "https://${domain}/bounce_login.php"

	private val ratingPattern = Regex("-?[0-9]+px")
	private val authCookies = arrayOf("ipb_member_id", "ipb_pass_hash")
	private var updateDm = false
	private val nextPages = SparseArrayCompat<Long>()
	private val suspiciousContentKey = ConfigKey.ShowSuspiciousContent(false)
	private val tagsMap = SuspendLazy(::fetchTags)

	override val isAuthorized: Boolean
		get() {
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

	init {
		context.cookieJar.insertCookies(DOMAIN_AUTHORIZED, "nw=1", "sl=dm_2")
		context.cookieJar.insertCookies(DOMAIN_UNAUTHORIZED, "nw=1", "sl=dm_2")
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val next = nextPages.get(page, 0L)

		if (page > 0 && next == 0L) {
			assert(false) { "Page timestamp not found" }
			return emptyList()
		}

		var search = ""

		val url = buildString {
			append("https://")
			append(domain)
			append("/?next=")
			append(next)
			when (filter) {

				is MangaListFilter.Search -> {
					search += filter.query.urlEncoded()
					append("&f_search=")
					append(search.trim().replace(' ', '+'))
				}

				is MangaListFilter.Advanced -> {

					filter.toSearchQuery()?.let { sq ->
						append("&f_search=")
						append(sq.urlEncoded())
					}

					val catsOn = filter.tags.mapNotNullToSet { it.key.toIntOrNull() }
					val catsOff = filter.tagsExclude.mapNotNullToSet { it.key.toIntOrNull() }
					if (catsOff.size >= 10) {
						return emptyList()
					}
					var fCats = catsOn.fold(0, Int::or)
					if (fCats != 0) {
						fCats = 1023 - fCats
					}
					fCats = catsOff.fold(fCats, Int::or)

					if (fCats != 0) {
						append("&f_cats=")
						append(fCats)
					}
				}

				null -> {}
			}
			// by unknown reason cookie "sl=dm_2" is ignored, so, we should request it again
			if (updateDm) {
				append("&inline_set=dm_e")
			}
			append("&advsearch=1")
			if (config[suspiciousContentKey]) {
				append("&f_sh=on")
			}
		}

		val body = webClient.httpGet(url).parseHtml().body()
		val root = body.selectFirst("table.itg")
			?.selectFirst("tbody")
			?: if (updateDm) {
				body.parseFailed("Cannot find root")
			} else {
				updateDm = true
				return getListPage(page, filter)
			}
		updateDm = false
		nextPages[page + 1] = getNextTimestamp(body)
		return root.children().mapNotNull { tr ->
			if (tr.childrenSize() != 2) return@mapNotNull null
			val (td1, td2) = tr.children()
			val gLink = td2.selectFirstOrThrow("div.glink")
			val a = gLink.parents().select("a").first() ?: gLink.parseFailed("link not found")
			val href = a.attrAsRelativeUrl("href")
			val tagsDiv = gLink.nextElementSibling() ?: gLink.parseFailed("tags div not found")
			val mainTag = td2.selectFirst("div.cn")?.let { div ->
				MangaTag(
					title = div.text().toTitleCase(Locale.ENGLISH),
					key = tagIdByClass(div.classNames()) ?: return@let null,
					source = source,
				)
			}
			Manga(
				id = generateUid(href),
				title = gLink.text().cleanupTitle(),
				altTitle = null,
				url = href,
				publicUrl = a.absUrl("href"),
				rating = td2.selectFirst("div.ir")?.parseRating() ?: RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = td1.selectFirst("img")?.absUrl("src").orEmpty(),
				tags = setOfNotNull(mainTag),
				state = null,
				author = tagsDiv.getElementsContainingOwnText("artist:").first()
					?.nextElementSibling()?.text(),
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
		val lang = root.getElementById("gd3")
			?.selectFirst("tr:contains(Language)")
			?.selectFirst(".gdt2")?.ownTextOrNull()

		val tagMap = tagsMap.get()
		val tags = ArraySet<MangaTag>()
		tagList?.selectFirst("tr:contains(female:)")?.select("a")?.mapNotNullTo(tags) { tagMap[it.text()] }
		tagList?.selectFirst("tr:contains(male:)")?.select("a")?.mapNotNullTo(tags) { tagMap[it.text()] }

		return manga.copy(
			title = title?.getElementById("gn")?.text()?.cleanupTitle() ?: manga.title,
			altTitle = title?.getElementById("gj")?.text()?.cleanupTitle() ?: manga.altTitle,
			publicUrl = doc.baseUri().ifEmpty { manga.publicUrl },
			rating = root.getElementById("rating_label")?.text()
				?.substringAfterLast(' ')
				?.toFloatOrNull()
				?.div(5f) ?: manga.rating,
			largeCoverUrl = cover?.styleValueOrNull("background")?.cssUrl(),
			tags = tags,
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
						name = "${manga.title} #$i",
						number = i.toFloat(),
						volume = 0,
						url = url,
						uploadDate = 0L,
						source = source,
						scanlator = null,
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
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.body().requireElementById("img").attrAsAbsoluteUrl("src")
	}

	private val tags =
		"ahegao,anal,angel,apron,bandages,bbw,bdsm,beauty mark,big areolae,big ass,big breasts,big clit,big lips," +
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return tagsMap.get().values.toSet()
	}

	private suspend fun fetchTags(): Map<String, MangaTag> {
		val tagMap = ArrayMap<String, MangaTag>()
		val tagElements = tags.split(",")
		for (el in tagElements) {
			if (el.isEmpty()) continue
			tagMap[el] = MangaTag(
				title = el.toTitleCase(Locale.ENGLISH),
				key = el,
				source = source,
			)
		}

		val doc = webClient.httpGet("https://${domain}").parseHtml()
		val root = doc.body().requireElementById("searchbox").selectFirstOrThrow("table")
		root.select("div.cs").forEach { div ->
			val id = div.id().substringAfterLast('_').toIntOrNull() ?: return@forEach
			val name = div.text().toTitleCase(Locale.ENGLISH)
			tagMap[name] = MangaTag(
				title = "Kind: $name",
				key = id.toString(),
				source = source,
			)
		}
		return tagMap
	}

	override suspend fun getAvailableLocales(): Set<Locale> = setOf(
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
	)

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		if (response.headersContentLength() <= 256) {
			val text = response.peekBody(256).string()
			if (text.startsWith("Your IP address has been temporarily banned")) {
				val hours = Regex("([0-9]+) hours?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0
				val minutes = Regex("([0-9]+) minutes?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0
				val seconds = Regex("([0-9]+) seconds?").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0
				throw TooManyRequestExceptions(
					url = response.request.url.toString(),
					retryAfter = TimeUnit.HOURS.toMillis(hours)
						+ TimeUnit.MINUTES.toMillis(minutes)
						+ TimeUnit.SECONDS.toMillis(seconds),
				)
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

	private fun isAuthorized(domain: String): Boolean {
		val cookies = context.cookieJar.getCookies(domain).mapToSet { x -> x.name }
		return authCookies.all { it in cookies }
	}

	private fun Element.parseRating(): Float {
		return runCatching {
			val style = requireNotNull(attr("style"))
			val (v1, v2) = ratingPattern.find(style)!!.destructured
			var p1 = v1.dropLast(2).toInt()
			val p2 = v2.dropLast(2).toInt()
			if (p2 != -1) {
				p1 += 8
			}
			(80 - p1) / 80f
		}.getOrDefault(RATING_UNKNOWN)
	}

	private fun String.cleanupTitle(): String {
		val result = StringBuilder(length)
		var skip = false
		for (c in this) {
			when {
				c == '[' -> skip = true
				c == ']' -> skip = false
				c.isWhitespace() && result.isEmpty() -> continue
				!skip -> result.append(c)
			}
		}
		while (result.lastOrNull()?.isWhitespace() == true) {
			result.deleteCharAt(result.lastIndex)
		}
		return result.toString()
	}

	private fun String.cssUrl(): String? {
		val fromIndex = indexOf("url(")
		if (fromIndex == -1) {
			return null
		}
		val toIndex = indexOf(')', startIndex = fromIndex)
		return if (toIndex == -1) {
			null
		} else {
			substring(fromIndex + 4, toIndex).trim()
		}
	}

	private fun tagIdByClass(classNames: Collection<String>): String? {
		val className = classNames.find { x -> x.startsWith("ct") } ?: return null
		val num = className.drop(2).toIntOrNull(16) ?: return null
		return 2.0.pow(num).toInt().toString()
	}

	private fun getNextTimestamp(root: Element): Long {
		return root.getElementById("unext")
			?.attrAsAbsoluteUrlOrNull("href")
			?.toHttpUrlOrNull()
			?.queryParameter("next")
			?.toLongOrNull() ?: 1
	}

	private fun MangaListFilter.Advanced.toSearchQuery(): String? {
		val joiner = StringUtil.StringJoiner(" ")
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
		return joiner.complete().takeUnless { it.isEmpty() }
	}
}
