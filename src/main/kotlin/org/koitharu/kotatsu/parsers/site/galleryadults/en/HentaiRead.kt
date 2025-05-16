package org.koitharu.kotatsu.parsers.site.galleryadults.en

import androidx.collection.arraySetOf
import okhttp3.Headers
import okhttp3.HttpUrl
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
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.YEAR_UNKNOWN
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.LinkResolver
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("HENTAIREAD", "HentaiRead", "en", type = ContentType.HENTAI)
internal class HentaiRead(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.HENTAIREAD, "hentairead.com", 30) {

	override fun getRequestHeaders(): Headers {
		return super.getRequestHeaders().newBuilder()
			.add("referer", "https://$domain/")
			.build()
	}

	private val availableTags = suspendLazy(initializer = ::mangaTags)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isAuthorSearchSupported = true,
			isYearSupported = true,
			isTagsExclusionSupported = true
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = availableTags.get(), // updated-at 2025-05-07, 699 tags. https://hentairead.com/?s=
			availableContentTypes = setOf(
				ContentType.DOUJINSHI,
				ContentType.HENTAI,
				ContentType.COMICS,
				ContentType.ARTIST_CG
			),
		)
	}

	override val selectGallery = ".manga-grid .manga-item"
	override val selectGalleryLink = "a.btn-read"
	override val selectGalleryTitle = ".manga-item__wrapper div:nth-child(3) a"
	override val selectTitle = ".manga-titles h1"
	override val selectTag = "div.text-primary:contains(Tags:)"
	override val selectAuthor = "div.text-primary:contains(Artist:)"
	override val selectLanguageChapter = ""
	override val selectUrlChapter = ""
	override val selectTotalPage = "[data-page]"

	val selectGalleryDetails = ".manga-item__detail div"
	val selectGalleryTags = ".manga-item__tags span"
	val selectGalleryRating = ".manga-item__rating span:nth-child(2)"

	val selectAltTitle = ".manga-titles h2"
	val selectParody = "div.text-primary:contains(Parody:)"
	val selectUploadedDate = "div.text-primary:contains(Uploaded:)"

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		// Query structure:
		// ?s=&
		// title-type=contains&     /* ?s= query types: contain, start-with, end-with */
		// search-mode=AND&         /* AND | OR */
		// release-type=in&         /* release year query-types: in, before, after */
		// release=&                /* release year */
		// categories[]=4&          /* category include: Doujinshi */
		// categories[]=52&         /* category include: Manga */
		// categories[]=4798&       /* category include: Artist CG */
		// categories[]=36278&      /* category include: Western */
		// artists[]=1223&          /* author search */
		// including[]=2928&        /* tags include */
		// including[]=600&
		// excluding[]=2928&        /* tags exclude */
		// pages=                   /* search range (leaves empty or 1-1000 for max-range) */
		val url = buildString {
			append("https://$domain")

			if (page > 1) {
				append("/page/$page")
			}

			val queries = mutableListOf<String>()
			queries.add("s=${filter.query ?: ""}")
			queries.add("title-type=contains&search-mode=AND") // Not supported

			if (filter.year != YEAR_UNKNOWN) {
				queries.add("release-type=in")
				queries.add("release=${filter.year}")
			} else {
				queries.add("release-type=in&release=")
			}

			filter.types.forEach {
				when (it) {
					ContentType.DOUJINSHI -> queries.add("categories[]=4")
					ContentType.HENTAI    -> queries.add("categories[]=52")
					ContentType.ARTIST_CG -> queries.add("categories[]=4798")
					ContentType.COMICS    -> queries.add("categories[]=36278")
					else -> {
						// Do nothing
					}
				}
			}

			if (!filter.author.isNullOrEmpty()) {
				val jsonResponse = webClient.httpGet(
					"/wp-admin/admin-ajax.php?action=search_manga_terms&search=${filter.author}&taxonomy=manga_artist"
						.toAbsoluteUrl(domain)
				).parseJson()

				val results = jsonResponse.get("results") as JSONArray
				if (results.length() > 0) {
					for (i in 0 until results.length()) {
						val item = results.get(i) as JSONObject
						if (filter.author.contentEquals(item.get("text") as String)) {
							val authorId = item.get("id")
							queries.add("artists[]=$authorId")
						}
					}
				} else {
					queries.add("artists[]=")
				}
			}

			filter.tags.forEach {
				queries.add("including[]=${it.key}")
			}

			filter.tagsExclude.forEach {
				queries.add("excluding[]=${it.key}")
			}

			queries.add("pages=")

			if (queries.count() > 4) {
				// That's means our query is not empty!
				append("/?")
				append(
					queries.joinToString("&")
				)
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(selectGallery).map {div ->
			val href = div.selectFirstOrThrow(selectGalleryLink).attrAsRelativeUrl("href")

			val allTags = mangaTags()
			val tags = div.select(selectGalleryTags).mapNotNullToSet { span ->
				val tagTitle = span.text()
				allTags.find { x -> x.title == tagTitle }
			}

			Manga(
				id = generateUid(href),
				title = div.select(selectGalleryTitle).text().cleanupTitle(),
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = div.selectFirstOrThrow(selectGalleryRating).text().toFloat(),
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = div.selectFirst(selectGalleryImg)?.src(),
				tags = tags,
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		var tags = manga.tags
		if (tags.count() == 0) {
			val allTags = availableTags.get()
			tags = doc.selectFirstOrThrow(selectTag).parent()!!.select("a").mapNotNullToSet {
				val title = it.select("span:first-child").text()
				allTags.find { x -> x.title == title }
			}
		}

		val authors = mutableSetOf<String>()
		doc.selectFirst(selectAuthor)?.nextElementSibling()?.parent()?.select("a")?.forEach {
			authors.add(
				it.select("span:first-child").text()
			)
		}

		var description = ""
		val parody = doc.selectFirst(selectParody)?.nextElementSibling()?.select("span:first-child")?.text()
		if (!parody.isNullOrEmpty() and !parody.contentEquals("Original")) {
			description = "Parody: $parody"
		}

		val dateFormat = SimpleDateFormat("MMMM d, yyyy h:mm a", Locale.ENGLISH)
		val uploadDateString = doc.selectFirst(selectUploadedDate)?.nextElementSibling()?.text()

		return manga.copy(
			title = doc.select(selectTitle).text().cleanupTitle(),
			altTitles = doc.selectFirst(selectAltTitle)?.text()?.let { setOf(it) } ?: emptySet(),
			contentRating = if (isNsfwSource) ContentRating.ADULT else null,
			rating = doc.select(".rating__number .rating__current").text().toFloat(),
			largeCoverUrl = doc.selectFirst("#mangaSummary a.image--hover img")!!.src(),
			tags = tags,
			authors = authors,
			description = description,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					title = manga.title,
					number = 0f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(uploadDateString),
					branch = "English",
					source = source,
				)
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select(selectTotalPage).map {
			val previewImgUrl = it.selectFirstOrThrow("img").attr("src")
			val index = it.attr("data-page")
			val url = "${chapter.url}/english/p/$index"

			MangaPage(
				id = generateUid(url),
				url = url,
				preview = previewImgUrl,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		if (page.preview.isNullOrEmpty()) {
			throw Exception("It should be not null. Something wrong!")
		}

		// preview page url: https://hencover.xyz/preview/${mangaId}/${chapterId}/hr_${index.padLeft(4)}.jpg
		// page url: https://henread.xyz/${mangaId}/${chapterId}/hr_${index.padLeft(4)}.jpg
		val index = page.url.split("/").last()
		val t = page.preview.split("/")
		val mangaId = t[4]
		val chapterId = t[5]

		return "https://henread.xyz/$mangaId/$chapterId/hr_${index.padStart(4, '0')}.jpg"
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val mangaId = "/${link.pathSegments[0]}/${link.pathSegments[1]}/"
		return resolver.resolveManga(this, mangaId)
	}

	private fun mangaTags() = arraySetOf(
//		MangaTag("Abortion", "2091", source)
		MangaTag("Abortion", "2091", source), MangaTag("Absorption", "2928", source), MangaTag("Additional Eyes", "47284", source), MangaTag("Adventitious Mouth", "32896", source), MangaTag("Adventitious Penis", "23899", source), MangaTag("Adventitious Vagina", "27052", source), MangaTag("Afro", "35756", source), MangaTag("Age Progression", "1922", source), MangaTag("Age Regression", "542", source), MangaTag("Ahegao", "566", source), MangaTag("Ai Generated", "36466", source), MangaTag("Albino", "20092", source), MangaTag("Alien", "6860", source), MangaTag("Alien Girl", "2160", source), MangaTag("All The Way Through", "898", source), MangaTag("Already Uploaded", "49088", source), MangaTag("Amputee", "646", source), MangaTag("Anal", "506", source), MangaTag("Anal Birth", "958", source), MangaTag("Anal Intercourse", "19411", source), MangaTag("Anal Prolapse", "30212", source), MangaTag("Analphagia", "30668", source), MangaTag("Angel", "3185", source), MangaTag("Animal on Animal", "4861", source), MangaTag("Animal On Furry", "36637", source), MangaTag("Animated", "19625", source), MangaTag("Animegao", "19630", source), MangaTag("Anorexic", "3725", source), MangaTag("Anthology", "3397", source), MangaTag("Apparel Bukkake", "21374", source), MangaTag("Apron", "847", source), MangaTag("Armpit Licking", "801", source), MangaTag("Armpit Sex", "1749", source), MangaTag("Artbook", "855", source), MangaTag("Asphyxiation", "1637", source), MangaTag("Ass Expansion", "13918", source), MangaTag("Assjob", "653", source), MangaTag("Aunt", "2531", source), MangaTag("Autofellatio", "1148", source), MangaTag("Autopaizuri", "5058", source), MangaTag("Bald", "677", source), MangaTag("Ball Caressing", "36205", source), MangaTag("Ball Sucking", "1116", source), MangaTag("Ball-less Shemale", "45409", source), MangaTag("Balljob", "845", source), MangaTag("Balls Expansion", "2114", source), MangaTag("Bandages", "2004", source), MangaTag("Bandaid", "1393", source), MangaTag("Bat", "44404", source), MangaTag("Bat Girl", "17792", source), MangaTag("BBM", "592", source), MangaTag("BBW", "1331", source), MangaTag("BDSM", "787", source), MangaTag("Bear", "5910", source), MangaTag("Bear Girl", "17932", source), MangaTag("Beauty Mark", "590", source), MangaTag("Bee Girl", "2073", source), MangaTag("Bestiality", "923", source), MangaTag("Big Areolae", "9869", source), MangaTag("Big Ass", "700", source), MangaTag("Big Balls", "900", source), MangaTag("Big Breasts", "515", source), MangaTag("Big Clit", "876", source), MangaTag("Big Lips", "4323", source), MangaTag("Big Muscles", "20909", source), MangaTag("Big Nipples", "817", source), MangaTag("Big Penis", "569", source), MangaTag("Big Vagina", "2172", source), MangaTag("Bike Shorts", "775", source), MangaTag("Bikini", "520", source), MangaTag("Bird Boy", "45931", source), MangaTag("Bird Girl", "44917", source), MangaTag("Bisexual", "967", source), MangaTag("Bite Mark", "48917", source), MangaTag("Blackmail", "749", source), MangaTag("Blind", "6652", source), MangaTag("Blindfold", "519", source), MangaTag("Blood", "647", source), MangaTag("Bloomers", "1074", source), MangaTag("Blowjob", "507", source), MangaTag("Blowjob Face", "730", source), MangaTag("Body Modification", "879", source), MangaTag("Body Painting", "7388", source), MangaTag("Body Swap", "1842", source), MangaTag("Body Writing", "1375", source), MangaTag("Bodystocking", "740", source), MangaTag("Bodysuit", "792", source), MangaTag("Bondage", "625", source), MangaTag("Braces", "38079", source), MangaTag("Brain Fuck", "1086", source), MangaTag("Breast Expansion", "877", source), MangaTag("Breast Feeding", "2480", source), MangaTag("Breast Reduction", "20459", source), MangaTag("Bride", "1020", source), MangaTag("Brother", "831", source), MangaTag("Bukkake", "616", source), MangaTag("Bull", "12834", source), MangaTag("Bunny Boy", "6327", source), MangaTag("Bunny Girl", "671", source), MangaTag("Burping", "21803", source), MangaTag("Business Suit", "1809", source), MangaTag("Butler", "5516", source), MangaTag("Cannibalism", "12799", source), MangaTag("Caption", "45633", source), MangaTag("Cashier", "5584", source), MangaTag("Cat", "10418", source), MangaTag("Catboy", "1469", source), MangaTag("Catfight", "1513", source), MangaTag("Catgirl", "609", source), MangaTag("CBT", "1934", source), MangaTag("Centaur", "3754", source), MangaTag("Cervix Penetration", "580", source), MangaTag("Cervix Prolapse", "20087", source), MangaTag("Chastity Belt", "755", source), MangaTag("Cheating", "571", source), MangaTag("Cheerleader", "756", source), MangaTag("Chikan", "1539", source), MangaTag("Chinese Dress", "2627", source), MangaTag("Chloroform", "895", source), MangaTag("Christmas", "1121", source), MangaTag("Clamp", "2971", source), MangaTag("Clit Growth", "2006", source), MangaTag("Clit Insertion", "20536", source), MangaTag("Clit Stimulation", "20942", source), MangaTag("Clone", "12596", source), MangaTag("Closed Eyes", "5411", source), MangaTag("Clothed Female Nude Male", "2136", source), MangaTag("Clothed Male Nude Female", "19754", source), MangaTag("Clothed Paizuri", "3858", source), MangaTag("Clown", "23376", source), MangaTag("Coach", "3506", source), MangaTag("Cock Ring", "27964", source), MangaTag("Cockphagia", "32897", source), MangaTag("Cockslapping", "21667", source), MangaTag("Collar", "540", source), MangaTag("Comic", "31046", source), MangaTag("Compilation", "29776", source), MangaTag("Condom", "1154", source), MangaTag("Confinement", "46487", source), MangaTag("Conjoined", "24499", source), MangaTag("Coprophagia", "13897", source), MangaTag("Corpse", "47871", source), MangaTag("Corruption", "816", source), MangaTag("Corset", "1259", source), MangaTag("Cosplaying", "19508", source), MangaTag("Cousin", "2249", source), MangaTag("Cow", "1882", source), MangaTag("Cowgirl", "1280", source), MangaTag("Cowman", "18047", source), MangaTag("Crossdressing", "554", source), MangaTag("Crotch Tattoo", "806", source), MangaTag("Crown", "951", source), MangaTag("Crying", "22005", source), MangaTag("Cum Bath", "1384", source), MangaTag("Cum in Eye", "5479", source), MangaTag("Cum Swap", "1450", source), MangaTag("Cumflation", "20039", source), MangaTag("Cunnilingus", "579", source), MangaTag("Cuntboy", "2216", source), MangaTag("Cuntbusting", "22725", source), MangaTag("Dark Nipples", "2173", source), MangaTag("Dark Sclera", "1822", source), MangaTag("Dark Skin", "565", source), MangaTag("Daughter", "791", source), MangaTag("Deepthroat", "843", source), MangaTag("Deer", "47735", source), MangaTag("Deer Boy", "12174", source), MangaTag("Deer Girl", "18703", source), MangaTag("Defloration", "517", source), MangaTag("Demon", "4093", source), MangaTag("Demon Girl", "819", source), MangaTag("Denki Anma", "33586", source), MangaTag("Detached Sleeves", "23000", source), MangaTag("Diaper", "3633", source), MangaTag("Dick Growth", "818", source), MangaTag("Dickgirl on Dickgirl", "3137", source), MangaTag("Dickgirl On Female", "38076", source), MangaTag("Dickgirl on Male", "935", source), MangaTag("Dickgirls Only", "3138", source), MangaTag("Dicknipples", "920", source), MangaTag("DILF", "805", source), MangaTag("Dinosaur", "25687", source), MangaTag("Dismantling", "22357", source), MangaTag("Dog", "2021", source), MangaTag("Dog Boy", "5146", source), MangaTag("Dog Girl", "1283", source), MangaTag("Doll Joints", "20167", source), MangaTag("Dolphin", "9227", source), MangaTag("Domination Loss", "20093", source), MangaTag("Double Anal", "788", source), MangaTag("Double Blowjob", "1834", source), MangaTag("Double Penetration", "662", source), MangaTag("Double Vaginal", "1400", source), MangaTag("Dougi", "2499", source), MangaTag("Dragon", "4271", source), MangaTag("Drill Hair", "19763", source), MangaTag("Drugs", "618", source), MangaTag("Drunk", "694", source), MangaTag("Ear Fuck", "5067", source), MangaTag("Eel", "10861", source), MangaTag("Eggs", "19699", source), MangaTag("Electric Shocks", "1282", source), MangaTag("Elf", "518", source), MangaTag("Emotionless Sex", "1552", source), MangaTag("Enema", "752", source), MangaTag("Exhibitionism", "689", source), MangaTag("Exposed Clothing", "18718", source), MangaTag("Eye Penetration", "20645", source), MangaTag("Eye-covering Bang", "19412", source), MangaTag("Eyemask", "1216", source), MangaTag("Eyepatch", "1093", source), MangaTag("Facesitting", "1307", source), MangaTag("Facial Hair", "13734", source), MangaTag("Fairy", "6977", source), MangaTag("Fanny Packing", "33447", source), MangaTag("Farting", "1939", source), MangaTag("Father", "7560", source), MangaTag("Females Only", "1104", source), MangaTag("Femdom", "669", source), MangaTag("Feminization", "1258", source), MangaTag("FFF Threesome", "15868", source), MangaTag("FFM Threesome", "635", source), MangaTag("FFT Threesome", "2576", source), MangaTag("Filming", "1073", source), MangaTag("Fingering", "508", source), MangaTag("First Person Perspective", "2616", source), MangaTag("Fish", "9679", source), MangaTag("Fishnets", "5735", source), MangaTag("Fisting", "776", source), MangaTag("Focus Anal", "19509", source), MangaTag("Focus Blowjob", "19644", source), MangaTag("Focus Paizuri", "20164", source), MangaTag("Food On Body", "25508", source), MangaTag("Foot Insertion", "6378", source), MangaTag("Foot Licking", "1298", source), MangaTag("Footjob", "762", source), MangaTag("Forbidden Content", "27175", source), MangaTag("Forced Exposure", "19638", source), MangaTag("Forniphilia", "1783", source), MangaTag("Fox", "38847", source), MangaTag("Fox Boy", "2666", source), MangaTag("Fox Girl", "976", source), MangaTag("Freckles", "1767", source), MangaTag("Frog", "13055", source), MangaTag("Frog Girl", "17940", source), MangaTag("Frottage", "19664", source), MangaTag("Full Censorship", "2198", source), MangaTag("Full Color", "526", source), MangaTag("Full-packaged Futanari", "37309", source), MangaTag("Fundoshi", "2471", source), MangaTag("Furry", "4133", source), MangaTag("Futanari", "772", source), MangaTag("Futanarization", "10419", source), MangaTag("Gag", "902", source), MangaTag("Gang Rape", "35950", source), MangaTag("Gaping", "821", source), MangaTag("Garter Belt", "704", source), MangaTag("Gasmask", "3512", source), MangaTag("Gender Change", "19477", source), MangaTag("Gender Morph", "19478", source), MangaTag("Genital Piercing", "29426", source), MangaTag("Ghost", "1424", source), MangaTag("Giant", "5692", source), MangaTag("Giant Sperm", "31910", source), MangaTag("Giantess", "2999", source), MangaTag("Gigantic Breasts", "20243", source), MangaTag("Gijinka", "28460", source), MangaTag("Giraffe Girl", "35803", source), MangaTag("Glasses", "683", source), MangaTag("Glory Hole", "2692", source), MangaTag("Gloves", "2913", source), MangaTag("Goat", "43576", source), MangaTag("Goblin", "1460", source), MangaTag("Gokkun", "1261", source), MangaTag("Gorilla", "27514", source), MangaTag("Gothic Lolita", "19892", source), MangaTag("Goudoushi", "20585", source), MangaTag("Granddaughter", "2050", source), MangaTag("Grandfather", "38921", source), MangaTag("Grandmother", "5397", source), MangaTag("Group", "2184", source), MangaTag("Growth", "5063", source), MangaTag("Guro", "643", source), MangaTag("Gyaru", "572", source), MangaTag("Gyaru-Oh", "701", source), MangaTag("Gymshorts", "3772", source), MangaTag("Haigure", "28096", source), MangaTag("Hair Buns", "9814", source), MangaTag("Hairjob", "1988", source), MangaTag("Hairy", "560", source), MangaTag("Hairy Armpits", "1125", source), MangaTag("Halo", "37176", source), MangaTag("Handicapped", "10956", source), MangaTag("Handjob", "722", source), MangaTag("Hanging", "22471", source), MangaTag("Harem", "521", source), MangaTag("Harness", "19326", source), MangaTag("Harpy", "8447", source), MangaTag("Headless", "24608", source), MangaTag("Headphones", "21781", source), MangaTag("Heterochromia", "2849", source), MangaTag("Hidden Sex", "602", source), MangaTag("High Heels", "33815", source), MangaTag("Hijab", "24401", source), MangaTag("Hood", "20632", source), MangaTag("Horns", "705", source), MangaTag("Horse", "1233", source), MangaTag("Horse Boy", "7317", source), MangaTag("Horse Cock", "3713", source), MangaTag("Horse Girl", "2283", source), MangaTag("Hotpants", "624", source), MangaTag("How To", "19960", source), MangaTag("Huge Breasts", "588", source), MangaTag("Huge Penis", "899", source), MangaTag("Human Cattle", "1885", source), MangaTag("Human on Furry", "949", source), MangaTag("Humiliation", "720", source), MangaTag("Hyena Girl", "48851", source), MangaTag("Impregnation", "539", source), MangaTag("Incest", "593", source), MangaTag("Incomplete", "2548", source), MangaTag("Infantilism", "5835", source), MangaTag("Inflation", "724", source), MangaTag("Insect", "3355", source), MangaTag("Insect Boy", "37068", source), MangaTag("Insect Girl", "959", source), MangaTag("Inseki", "832", source), MangaTag("Internal Urination", "19769", source), MangaTag("Inverted Nipples", "745", source), MangaTag("Invisible", "914", source), MangaTag("Josou Seme", "553", source), MangaTag("Kangaroo", "36695", source), MangaTag("Kangaroo Girl", "48492", source), MangaTag("Kappa", "10047", source), MangaTag("Kemonomimi", "711", source), MangaTag("Kigurumi Pajama", "21402", source), MangaTag("Kimono", "712", source), MangaTag("Kindergarten Uniform", "27567", source), MangaTag("Kissing", "509", source), MangaTag("Kneepit Sex", "7309", source), MangaTag("Kodomo Doushi", "42716", source), MangaTag("Kodomo Only", "43498", source), MangaTag("Kunoichi", "1202", source), MangaTag("Lab Coat", "2677", source), MangaTag("Lactation", "721", source), MangaTag("Large Insertions", "774", source), MangaTag("Large Tattoo", "19410", source), MangaTag("Latex", "742", source), MangaTag("Layer Cake", "1172", source), MangaTag("Leash", "11350", source), MangaTag("Leg Lock", "573", source), MangaTag("Legjob", "4435", source), MangaTag("Leotard", "1052", source), MangaTag("Lingerie", "583", source), MangaTag("Lion", "24177", source), MangaTag("Lioness", "47736", source), MangaTag("Lipstick Mark", "33164", source), MangaTag("Living Clothes", "2109", source), MangaTag("Lizard Girl", "2888", source), MangaTag("Lizard Guy", "950", source), MangaTag("Lolicon", "19429", source), MangaTag("Long Tongue", "820", source), MangaTag("Low Bestiality", "2463", source), MangaTag("Low Guro", "29597", source), MangaTag("Low Incest", "46409", source), MangaTag("Low Lolicon", "1546", source), MangaTag("Low Scat", "20053", source), MangaTag("Low Shotacon", "6326", source), MangaTag("Low Smegma", "22748", source), MangaTag("Machine", "3082", source), MangaTag("Maggot", "16161", source), MangaTag("Magical Girl", "1021", source), MangaTag("Maid", "582", source), MangaTag("Makeup", "21366", source), MangaTag("Male on Dickgirl", "4143", source), MangaTag("Males Only", "551", source), MangaTag("Masked Face", "1427", source), MangaTag("Masturbation", "600", source), MangaTag("Mecha Boy", "29192", source), MangaTag("Mecha Girl", "858", source), MangaTag("Menstruation", "14092", source), MangaTag("Mermaid", "4272", source), MangaTag("Merman", "17859", source), MangaTag("Mesugaki", "36081", source), MangaTag("Mesuiki", "22448", source), MangaTag("Metal Armor", "1973", source), MangaTag("Midget", "4738", source), MangaTag("Miko", "977", source), MangaTag("MILF", "784", source), MangaTag("Military", "695", source), MangaTag("Milking", "1784", source), MangaTag("Mind Break", "726", source), MangaTag("Mind Control", "741", source), MangaTag("Minigirl", "4892", source), MangaTag("Miniguy", "2906", source), MangaTag("Minotaur", "3625", source), MangaTag("Missing Cover", "21413", source), MangaTag("MMF Threesome", "678", source), MangaTag("MMM Threesome", "16530", source), MangaTag("MMT Threesome", "16278", source), MangaTag("Monkey", "4679", source), MangaTag("Monkey Boy", "25084", source), MangaTag("Monkey Girl", "3901", source), MangaTag("Monoeye", "13309", source), MangaTag("Monster", "1598", source), MangaTag("Monster Girl", "839", source), MangaTag("Moral Degeneration", "723", source), MangaTag("Mosaic Censorship", "990", source), MangaTag("Mother", "1126", source), MangaTag("Mouse", "44405", source), MangaTag("Mouse Boy", "15530", source), MangaTag("Mouse Girl", "1446", source), MangaTag("Mouth Mask", "20294", source), MangaTag("MTF Threesome", "1127", source), MangaTag("Multi-Work Series", "568", source), MangaTag("Multimouth Blowjob", "20095", source), MangaTag("Multipanel Sequence", "20090", source), MangaTag("Multiple Arms", "10315", source), MangaTag("Multiple Assjob", "45655", source), MangaTag("Multiple Breasts", "924", source), MangaTag("Multiple Footjob", "25452", source), MangaTag("Multiple Handjob", "24220", source), MangaTag("Multiple Orgasms", "20267", source), MangaTag("Multiple Paizuri", "2131", source), MangaTag("Multiple Penises", "921", source), MangaTag("Multiple Straddling", "25009", source), MangaTag("Multiple Tails", "48489", source), MangaTag("Multiple Vaginas", "27053", source), MangaTag("Muscle", "688", source), MangaTag("Muscle Growth", "27743", source), MangaTag("Mute", "6084", source), MangaTag("Nakadashi", "524", source), MangaTag("Navel Birth", "46613", source), MangaTag("Navel Fuck", "1383", source), MangaTag("Nazi", "21454", source), MangaTag("Necrophilia", "3356", source), MangaTag("Netorare", "725", source), MangaTag("Netorase", "37079", source), MangaTag("Niece", "594", source), MangaTag("Ninja", "4069", source), MangaTag("Nipple Birth", "16160", source), MangaTag("Nipple Expansion", "11371", source), MangaTag("Nipple Fuck", "881", source), MangaTag("Nipple Piercing", "29465", source), MangaTag("Nipple Stimulation", "23481", source), MangaTag("No Balls", "44539", source), MangaTag("No Penetration", "9743", source), MangaTag("Nose Fuck", "20515", source), MangaTag("Nose Hook", "2520", source), MangaTag("Novel", "13396", source), MangaTag("Nudism", "29533", source), MangaTag("Nudity Only", "21265", source), MangaTag("Nun", "1149", source), MangaTag("Nurse", "848", source), MangaTag("Object Insertion Only", "45827", source), MangaTag("Octopus", "3231", source), MangaTag("Oil", "1343", source), MangaTag("Old Lady", "15445", source), MangaTag("Old Man", "3646", source), MangaTag("Omorashi", "15965", source), MangaTag("Onahole", "840", source), MangaTag("Oni", "3763", source), MangaTag("Onsen", "49118", source), MangaTag("Oppai Loli", "1107", source), MangaTag("Orc", "1800", source), MangaTag("Orgasm Denial", "754", source), MangaTag("Otokofutanari", "23865", source), MangaTag("Otter Girl", "24463", source), MangaTag("Out of Order", "5419", source), MangaTag("Oyakodon", "794", source), MangaTag("Painted Nails", "32700", source), MangaTag("Paizuri", "578", source), MangaTag("Panda Girl", "40977", source), MangaTag("Panther", "45015", source), MangaTag("Pantyhose", "523", source), MangaTag("Pantyjob", "2167", source), MangaTag("Parasite", "883", source), MangaTag("Pasties", "886", source), MangaTag("Pegasus", "39837", source), MangaTag("Pegging", "789", source), MangaTag("Penis Birth", "16839", source), MangaTag("Penis Enlargement", "37974", source), MangaTag("Penis Reduction", "49324", source), MangaTag("Personality Excretion", "18425", source), MangaTag("Petplay", "12419", source), MangaTag("Petrification", "16013", source), MangaTag("Phimosis", "567", source), MangaTag("Phone Sex", "11507", source), MangaTag("Piercing", "645", source), MangaTag("Pig", "922", source), MangaTag("Pig Girl", "4238", source), MangaTag("Pig Man", "8430", source), MangaTag("Pillory", "773", source), MangaTag("Pirate", "4019", source), MangaTag("Piss Drinking", "1026", source), MangaTag("Pixie Cut", "1551", source), MangaTag("Plant Girl", "9592", source), MangaTag("Pole Dancing", "1217", source), MangaTag("Policeman", "8867", source), MangaTag("Policewoman", "656", source), MangaTag("Ponygirl", "7316", source), MangaTag("Ponytail", "528", source), MangaTag("Possession", "1084", source), MangaTag("Pregnant", "824", source), MangaTag("Prehensile Hair", "833", source), MangaTag("Priest", "9589", source), MangaTag("Prolapse", "1451", source), MangaTag("Property Tag", "48632", source), MangaTag("Prostate Massage", "936", source), MangaTag("Prostitution", "670", source), MangaTag("Pubic Stubble", "1128", source), MangaTag("Public Use", "727", source), MangaTag("Rabbit", "30709", source), MangaTag("Raccoon Boy", "16455", source), MangaTag("Raccoon Girl", "3204", source), MangaTag("Race Queen", "4056", source), MangaTag("Randoseru", "2180", source), MangaTag("Rape", "595", source), MangaTag("Real Doll", "26129", source), MangaTag("Redraw", "20203", source), MangaTag("Reptile", "27901", source), MangaTag("Retractable Penis", "27465", source), MangaTag("Rimjob", "1658", source), MangaTag("Robot", "1357", source), MangaTag("Rough Grammar", "19632", source), MangaTag("Rough Translation", "20231", source), MangaTag("Ryona", "648", source), MangaTag("Saliva", "5077", source), MangaTag("Sample", "21346", source), MangaTag("Sarashi", "24902", source), MangaTag("Scanmark", "6058", source), MangaTag("Scar", "1016", source), MangaTag("Scat", "1561", source), MangaTag("Scat Insertion", "33632", source), MangaTag("School Gym Uniform", "19467", source), MangaTag("School Swimsuit", "850", source), MangaTag("Schoolboy Uniform", "782", source), MangaTag("Schoolgirl Uniform", "559", source), MangaTag("Scrotal Lingerie", "856", source), MangaTag("Selfcest", "504", source), MangaTag("Sex Toys", "604", source), MangaTag("Shared Senses", "1027", source), MangaTag("Shark", "36551", source), MangaTag("Shark Boy", "17467", source), MangaTag("Shark Girl", "17721", source), MangaTag("Shaved Head", "20082", source), MangaTag("Sheep", "36805", source), MangaTag("Sheep Boy", "19915", source), MangaTag("Sheep Girl", "1542", source), MangaTag("Shemale", "1179", source), MangaTag("Shibari", "1075", source), MangaTag("Shimaidon", "12201", source), MangaTag("Shimapan", "2077", source), MangaTag("Short Hair", "2412", source), MangaTag("Shotacon", "550", source), MangaTag("Shrinking", "5065", source), MangaTag("Sister", "780", source), MangaTag("Sketch Lines", "21405", source), MangaTag("Skinsuit", "3157", source), MangaTag("Slave", "1103", source), MangaTag("Sleeping", "617", source), MangaTag("Slime", "1085", source), MangaTag("Slime Boy", "26099", source), MangaTag("Slime Girl", "3241", source), MangaTag("Slug", "3353", source), MangaTag("Small Breasts", "3239", source), MangaTag("Small Penis", "3096", source), MangaTag("Smalldom", "22943", source), MangaTag("Smegma", "2270", source), MangaTag("Smell", "591", source), MangaTag("Smoking", "2608", source), MangaTag("Snail Girl", "26051", source), MangaTag("Snake", "6795", source), MangaTag("Snake Boy", "30194", source), MangaTag("Snake Girl", "11731", source), MangaTag("Snuff", "872", source), MangaTag("Sockjob", "21270", source), MangaTag("Sole Dickgirl", "1846", source), MangaTag("Sole Female", "541", source), MangaTag("Sole Male", "516", source), MangaTag("Sole Pussyboy", "23866", source), MangaTag("Solo Action", "1147", source), MangaTag("Soushuuhen", "10815", source), MangaTag("Spanking", "1299", source), MangaTag("Speculum", "2484", source), MangaTag("Spider", "3354", source), MangaTag("Spider Girl", "961", source), MangaTag("Squid Boy", "29638", source), MangaTag("Squid Girl", "1803", source), MangaTag("Squirrel Boy", "43338", source), MangaTag("Squirrel Girl", "8744", source), MangaTag("Squirting", "584", source), MangaTag("SSBBM", "15882", source), MangaTag("SSBBW", "12508", source), MangaTag("Steward", "44689", source), MangaTag("Stewardess", "5686", source), MangaTag("Stirrup Legwear", "20542", source), MangaTag("Stockings", "522", source), MangaTag("Stomach Deformation", "642", source), MangaTag("Story Arc", "1119", source), MangaTag("Straitjacket", "20066", source), MangaTag("Strap-On", "793", source), MangaTag("Stretching", "2929", source), MangaTag("Stuck in Wall", "623", source), MangaTag("Sumata", "654", source), MangaTag("Sundress", "842", source), MangaTag("Sunglasses", "690", source), MangaTag("Sweating", "581", source), MangaTag("Swimsuit", "527", source), MangaTag("Swinging", "1764", source), MangaTag("Syringe", "2159", source), MangaTag("Tabi Socks", "38077", source), MangaTag("Table Masturbation", "1434", source), MangaTag("Tail", "1881", source), MangaTag("Tail Plug", "865", source), MangaTag("Tailjob", "2080", source), MangaTag("Tailphagia", "9382", source), MangaTag("Tall Girl", "859", source), MangaTag("Tall Man", "2005", source), MangaTag("Tankoubon", "1129", source), MangaTag("Tanlines", "1120", source), MangaTag("Teacher", "748", source), MangaTag("Tentacles", "719", source), MangaTag("Thick Eyebrows", "20311", source), MangaTag("Thigh High Boots", "758", source), MangaTag("Tiara", "1243", source), MangaTag("Tickling", "1394", source), MangaTag("Tiger", "32860", source), MangaTag("Tights", "5027", source), MangaTag("Time Stop", "1553", source), MangaTag("Tomboy", "1124", source), MangaTag("Tomgirl", "2932", source), MangaTag("Tooth Brushing", "24271", source), MangaTag("Torture", "1461", source), MangaTag("Tracksuit", "736", source), MangaTag("Trampling", "1912", source), MangaTag("Transformation", "829", source), MangaTag("Transparent Clothing", "20297", source), MangaTag("Tribadism", "1170", source), MangaTag("Triple Anal", "2503", source), MangaTag("Triple Penetration", "1234", source), MangaTag("Triple Vaginal", "4370", source), MangaTag("TTF Threesome", "3937", source), MangaTag("TTM Threesome", "1965", source), MangaTag("TTT Threesome", "18029", source), MangaTag("Tube", "3083", source), MangaTag("Turtle", "21828", source), MangaTag("Tutor", "851", source), MangaTag("Twins", "655", source), MangaTag("Twintails", "644", source), MangaTag("Unbirth", "3737", source), MangaTag("Uncensored", "763", source), MangaTag("Uncle", "17213", source), MangaTag("Underwater", "2478", source), MangaTag("Unicorn", "29657", source), MangaTag("Unusual Insertions", "19762", source), MangaTag("Unusual Pupils", "2937", source), MangaTag("Unusual Teeth", "903", source), MangaTag("Urethra Insertion", "790", source), MangaTag("Urination", "3467", source), MangaTag("Vacbed", "2678", source), MangaTag("Vaginal Birth", "27911", source), MangaTag("Vaginal Sticker", "2360", source), MangaTag("Vampire", "983", source), MangaTag("Variant Set", "25869", source), MangaTag("Very Long Hair", "19406", source), MangaTag("Virginity", "570", source), MangaTag("Vomit", "1035", source), MangaTag("Vore", "2905", source), MangaTag("Voyeurism", "601", source), MangaTag("Vtuber", "12015", source), MangaTag("Waiter", "5638", source), MangaTag("Waitress", "4989", source), MangaTag("Watermarked", "19993", source), MangaTag("Webtoon", "25449", source), MangaTag("Weight Gain", "2007", source), MangaTag("Western Cg", "49018", source), MangaTag("Western Imageset", "49379", source), MangaTag("Wet Clothes", "878", source), MangaTag("Whale", "45881", source), MangaTag("Whip", "7679", source), MangaTag("Widow", "1770", source), MangaTag("Widower", "19524", source), MangaTag("Wingjob", "25570", source), MangaTag("Wings", "849", source), MangaTag("Witch", "1250", source), MangaTag("Wolf", "2464", source), MangaTag("Wolf Boy", "9674", source), MangaTag("Wolf Girl", "1059", source), MangaTag("Wooden Horse", "6027", source), MangaTag("Worm", "19593", source), MangaTag("Wormhole", "2170", source), MangaTag("Wrestling", "3249", source), MangaTag("X-Ray", "543", source), MangaTag("Yandere", "781", source), MangaTag("Yaoi", "552", source), MangaTag("Yuri", "880", source), MangaTag("Zebra", "47737", source), MangaTag("Zombie", "3357", source)
    )
}
