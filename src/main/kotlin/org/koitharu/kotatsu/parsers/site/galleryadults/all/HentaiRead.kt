package org.koitharu.kotatsu.parsers.site.galleryadults.all

import okhttp3.Headers
import okhttp3.HttpUrl
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
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.lang.Error
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("HENTAIREAD", "HentaiRead", type = ContentType.HENTAI)
internal class HentaiRead(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.HENTAIREAD, "hentairead.com", 30) {
	override val selectGallery = ".container div section .manga-grid"
	override val selectGalleryLink = "a.btn-read"
	override val selectGalleryTitle = ".manga-item__wrapper div:nth-child(3) a"
	override val selectTitle = ".manga-titles h1"
	override val selectTag = ""
	override val selectAuthor = ""
	override val selectLanguageChapter = ""
	override val selectUrlChapter = ""
	override val selectTotalPage = "[data-page]"

	val selectGalleryDetails = ".manga-item__detail div"
	val selectGalleryTags = ".manga-item__tags span"
	val selectGalleryRating = ".manga-item__rating span:nth-child(2)"

	val selectAltTitle = ".manga-titles h2"
	val selectParody = "div.text-primary:contains(Parody:)"
	val selectAuthors = "div.text-primary:contains(Artist:)"
	val selectUploadedDate = "div.text-primary:contains(Uploaded:)"

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
			availableTags = mangaTags, // updated-at 2025-05-07, 699 tags. https://hentairead.com/?s=
			availableContentTypes = setOf(
				ContentType.DOUJINSHI,
				ContentType.HENTAI,
				ContentType.COMICS,
				ContentType.ARTIST_CG
			),
		)
	}

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
			append("https://$domain/page/$page")

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
					ContentType.HENTAI -> queries.add("categories[]=52")
					ContentType.ARTIST_CG -> queries.add("categories[]=4798")
					ContentType.COMICS -> queries.add("categories[]=36278")
					else -> {
						// Do nothing
					}
				}
			}

			if (!filter.author.isNullOrEmpty()) {
				var jsonResponse = webClient.httpGet(
					"https://$domain/wp-admin/admin-ajax.php?action=search_manga_terms&search=${filter.author}&taxonomy=manga_artist"
				).parseHtml().text()
				when {
					jsonResponse.indexOf(" \"results\": []") == -1 -> {
						var foundIndex = jsonResponse.indexOf(",", 0)
						do {
							val authorId = jsonResponse
								.substringAfter("\"id\":")
								.substringBefore(",")

							queries.add("artists[]=$authorId")

							jsonResponse = jsonResponse.substring(foundIndex)
							foundIndex = jsonResponse.indexOf(",", 0)
						} while (foundIndex > -1)
					}
					else -> {
						queries.add("artists[]=")
					}
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
		return doc.selectFirstOrThrow(selectGallery).select(".manga-item").map {div ->
			val href = div.selectFirstOrThrow(selectGalleryLink).attrAsRelativeUrl("href")

			val mangaDetails = div.select(selectGalleryDetails)
			val l = mangaDetails.count()

			val title = buildString {
				append( div.select(selectGalleryTitle).text().cleanupTitle() )
				val parody = mangaDetails.get(l - 3).text()
				when {
					!parody.contentEquals("Original") -> {
						append(" (${parody.trim().cleanupTitle()})")
					}
				}
			}

			val altTitleSet = when {
				l == 4 -> {
					setOf(mangaDetails.get(0).text())
				}
				else -> {
					emptySet()
				}
			}


			Manga(
				id = generateUid(href),
				title = title,
				altTitles = altTitleSet,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = div.selectFirstOrThrow(selectGalleryRating).text().toFloat(),
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = div.selectFirst(selectGalleryImg)?.src(),
				tags = div.select(selectGalleryTags).mapToSet {
					val title = it.text()
					MangaTag (
						title = title,
						key = mangaTags.firstOrNull { it.title == title }?.key ?: "", // <-- this is f**king slow
						source = source
					)
				},
				state = null,
				authors = setOf(mangaDetails.get(l - 2).text()), // <-- should bug here, there are not always only one author
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val title = buildString {
			val mangaTitle = doc.selectFirst(selectTitle)?.text()?.cleanupTitle()
			val parody = doc.selectFirst(selectParody)?.nextElementSibling()?.select("span:first-child")?.text()
			when {
				!parody.isNullOrEmpty() and !parody.contentEquals("Original") -> {
					append(
						"$mangaTitle (${parody?.trim()?.cleanupTitle()})"
					)
				}
				else -> {
					append(mangaTitle)
				}
			}
		}
		val altTitleSet = doc.selectFirst(selectAltTitle)?.text()?.let { setOf(it) } ?: emptySet()
		val authors = doc.selectFirst(selectAuthors)?.nextElementSibling()?.parent()?.select("a")?.mapToSet {
			it.select("span:first-child").text()
		}
		val uploadedDateString = doc.selectFirst(selectUploadedDate)?.nextElementSibling()?.text()
		return manga.copy(
			title = title,
			description = "",
			altTitles = altTitleSet,
			authors = authors ?: emptySet(),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					title = manga.title,
					number = 0f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = parseDateString(uploadedDateString),
					branch = "English",
					source = source,
				)
			)
		)
	}

	private fun parseDateString(dateString: String?): Long {
		if (dateString == null) return 0
		val format = SimpleDateFormat("MMMM d, yyyy h:mm a", Locale.ENGLISH)
		return format.parse(dateString).time
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
			throw Error("It should be not null. Something wrong!")
		}

		// preview page url: https://hencover.xyz/preview/${mangaId}/${chapterId}/hr_${index.padLeft(4)}.jpg
		// page url: https://henread.xyz/${mangaId}/${chapterId}/hr_${index.padLeft(4)}.jpg
		val index = page.url.split("/").last()
		val t = page.preview.split("/")
		val mangaId = t[4]
		val chapterId = t[5]

		return "https://henread.xyz/$mangaId/$chapterId/hr_${index.padStart(4, '0')}.jpg"
	}

	override fun getRequestHeaders(): Headers {
		return super.getRequestHeaders().newBuilder()
			.add("referer", "https://$domain/")
			.build()
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val mangaId = "/${link.pathSegments[0]}/${link.pathSegments[1]}/"
		return resolver.resolveManga(this, mangaId)
	}

	private val mangaTags: Set<MangaTag> = setOf(
		MangaTag(title = "Abortion", key = "2091", source), MangaTag(title = "Absorption", key = "2928", source), MangaTag(title = "Additional Eyes", key = "47284", source), MangaTag(title = "Adventitious Mouth", key = "32896", source), MangaTag(title = "Adventitious Penis", key = "23899", source), MangaTag(title = "Adventitious Vagina", key = "27052", source), MangaTag(title = "Afro", key = "35756", source), MangaTag(title = "Age Progression", key = "1922", source), MangaTag(title = "Age Regression", key = "542", source), MangaTag(title = "Ahegao", key = "566", source), MangaTag(title = "Ai Generated", key = "36466", source), MangaTag(title = "Albino", key = "20092", source), MangaTag(title = "Alien", key = "6860", source), MangaTag(title = "Alien Girl", key = "2160", source), MangaTag(title = "All The Way Through", key = "898", source), MangaTag(title = "Already Uploaded", key = "49088", source), MangaTag(title = "Amputee", key = "646", source), MangaTag(title = "Anal", key = "506", source), MangaTag(title = "Anal Birth", key = "958", source), MangaTag(title = "Anal Intercourse", key = "19411", source), MangaTag(title = "Anal Prolapse", key = "30212", source), MangaTag(title = "Analphagia", key = "30668", source), MangaTag(title = "Angel", key = "3185", source), MangaTag(title = "Animal on Animal", key = "4861", source), MangaTag(title = "Animal On Furry", key = "36637", source), MangaTag(title = "Animated", key = "19625", source), MangaTag(title = "Animegao", key = "19630", source), MangaTag(title = "Anorexic", key = "3725", source), MangaTag(title = "Anthology", key = "3397", source), MangaTag(title = "Apparel Bukkake", key = "21374", source), MangaTag(title = "Apron", key = "847", source), MangaTag(title = "Armpit Licking", key = "801", source), MangaTag(title = "Armpit Sex", key = "1749", source), MangaTag(title = "Artbook", key = "855", source), MangaTag(title = "Asphyxiation", key = "1637", source), MangaTag(title = "Ass Expansion", key = "13918", source), MangaTag(title = "Assjob", key = "653", source), MangaTag(title = "Aunt", key = "2531", source), MangaTag(title = "Autofellatio", key = "1148", source), MangaTag(title = "Autopaizuri", key = "5058", source), MangaTag(title = "Bald", key = "677", source), MangaTag(title = "Ball Caressing", key = "36205", source), MangaTag(title = "Ball Sucking", key = "1116", source), MangaTag(title = "Ball-less Shemale", key = "45409", source), MangaTag(title = "Balljob", key = "845", source), MangaTag(title = "Balls Expansion", key = "2114", source), MangaTag(title = "Bandages", key = "2004", source), MangaTag(title = "Bandaid", key = "1393", source), MangaTag(title = "Bat", key = "44404", source), MangaTag(title = "Bat Girl", key = "17792", source), MangaTag(title = "BBM", key = "592", source), MangaTag(title = "BBW", key = "1331", source), MangaTag(title = "BDSM", key = "787", source), MangaTag(title = "Bear", key = "5910", source), MangaTag(title = "Bear Girl", key = "17932", source), MangaTag(title = "Beauty Mark", key = "590", source), MangaTag(title = "Bee Girl", key = "2073", source), MangaTag(title = "Bestiality", key = "923", source), MangaTag(title = "Big Areolae", key = "9869", source), MangaTag(title = "Big Ass", key = "700", source), MangaTag(title = "Big Balls", key = "900", source), MangaTag(title = "Big Breasts", key = "515", source), MangaTag(title = "Big Clit", key = "876", source), MangaTag(title = "Big Lips", key = "4323", source), MangaTag(title = "Big Muscles", key = "20909", source), MangaTag(title = "Big Nipples", key = "817", source), MangaTag(title = "Big Penis", key = "569", source), MangaTag(title = "Big Vagina", key = "2172", source), MangaTag(title = "Bike Shorts", key = "775", source), MangaTag(title = "Bikini", key = "520", source), MangaTag(title = "Bird Boy", key = "45931", source), MangaTag(title = "Bird Girl", key = "44917", source), MangaTag(title = "Bisexual", key = "967", source), MangaTag(title = "Bite Mark", key = "48917", source), MangaTag(title = "Blackmail", key = "749", source), MangaTag(title = "Blind", key = "6652", source), MangaTag(title = "Blindfold", key = "519", source), MangaTag(title = "Blood", key = "647", source), MangaTag(title = "Bloomers", key = "1074", source), MangaTag(title = "Blowjob", key = "507", source), MangaTag(title = "Blowjob Face", key = "730", source), MangaTag(title = "Body Modification", key = "879", source), MangaTag(title = "Body Painting", key = "7388", source), MangaTag(title = "Body Swap", key = "1842", source), MangaTag(title = "Body Writing", key = "1375", source), MangaTag(title = "Bodystocking", key = "740", source), MangaTag(title = "Bodysuit", key = "792", source), MangaTag(title = "Bondage", key = "625", source), MangaTag(title = "Braces", key = "38079", source), MangaTag(title = "Brain Fuck", key = "1086", source), MangaTag(title = "Breast Expansion", key = "877", source), MangaTag(title = "Breast Feeding", key = "2480", source), MangaTag(title = "Breast Reduction", key = "20459", source), MangaTag(title = "Bride", key = "1020", source), MangaTag(title = "Brother", key = "831", source), MangaTag(title = "Bukkake", key = "616", source), MangaTag(title = "Bull", key = "12834", source), MangaTag(title = "Bunny Boy", key = "6327", source), MangaTag(title = "Bunny Girl", key = "671", source), MangaTag(title = "Burping", key = "21803", source), MangaTag(title = "Business Suit", key = "1809", source), MangaTag(title = "Butler", key = "5516", source), MangaTag(title = "Cannibalism", key = "12799", source), MangaTag(title = "Caption", key = "45633", source), MangaTag(title = "Cashier", key = "5584", source), MangaTag(title = "Cat", key = "10418", source), MangaTag(title = "Catboy", key = "1469", source), MangaTag(title = "Catfight", key = "1513", source), MangaTag(title = "Catgirl", key = "609", source), MangaTag(title = "CBT", key = "1934", source), MangaTag(title = "Centaur", key = "3754", source), MangaTag(title = "Cervix Penetration", key = "580", source), MangaTag(title = "Cervix Prolapse", key = "20087", source), MangaTag(title = "Chastity Belt", key = "755", source), MangaTag(title = "Cheating", key = "571", source), MangaTag(title = "Cheerleader", key = "756", source), MangaTag(title = "Chikan", key = "1539", source), MangaTag(title = "Chinese Dress", key = "2627", source), MangaTag(title = "Chloroform", key = "895", source), MangaTag(title = "Christmas", key = "1121", source), MangaTag(title = "Clamp", key = "2971", source), MangaTag(title = "Clit Growth", key = "2006", source), MangaTag(title = "Clit Insertion", key = "20536", source), MangaTag(title = "Clit Stimulation", key = "20942", source), MangaTag(title = "Clone", key = "12596", source), MangaTag(title = "Closed Eyes", key = "5411", source), MangaTag(title = "Clothed Female Nude Male", key = "2136", source), MangaTag(title = "Clothed Male Nude Female", key = "19754", source), MangaTag(title = "Clothed Paizuri", key = "3858", source), MangaTag(title = "Clown", key = "23376", source), MangaTag(title = "Coach", key = "3506", source), MangaTag(title = "Cock Ring", key = "27964", source), MangaTag(title = "Cockphagia", key = "32897", source), MangaTag(title = "Cockslapping", key = "21667", source), MangaTag(title = "Collar", key = "540", source), MangaTag(title = "Comic", key = "31046", source), MangaTag(title = "Compilation", key = "29776", source), MangaTag(title = "Condom", key = "1154", source), MangaTag(title = "Confinement", key = "46487", source), MangaTag(title = "Conjoined", key = "24499", source), MangaTag(title = "Coprophagia", key = "13897", source), MangaTag(title = "Corpse", key = "47871", source), MangaTag(title = "Corruption", key = "816", source), MangaTag(title = "Corset", key = "1259", source), MangaTag(title = "Cosplaying", key = "19508", source), MangaTag(title = "Cousin", key = "2249", source), MangaTag(title = "Cow", key = "1882", source), MangaTag(title = "Cowgirl", key = "1280", source), MangaTag(title = "Cowman", key = "18047", source), MangaTag(title = "Crossdressing", key = "554", source), MangaTag(title = "Crotch Tattoo", key = "806", source), MangaTag(title = "Crown", key = "951", source), MangaTag(title = "Crying", key = "22005", source), MangaTag(title = "Cum Bath", key = "1384", source), MangaTag(title = "Cum in Eye", key = "5479", source), MangaTag(title = "Cum Swap", key = "1450", source), MangaTag(title = "Cumflation", key = "20039", source), MangaTag(title = "Cunnilingus", key = "579", source), MangaTag(title = "Cuntboy", key = "2216", source), MangaTag(title = "Cuntbusting", key = "22725", source), MangaTag(title = "Dark Nipples", key = "2173", source), MangaTag(title = "Dark Sclera", key = "1822", source), MangaTag(title = "Dark Skin", key = "565", source), MangaTag(title = "Daughter", key = "791", source), MangaTag(title = "Deepthroat", key = "843", source), MangaTag(title = "Deer", key = "47735", source), MangaTag(title = "Deer Boy", key = "12174", source), MangaTag(title = "Deer Girl", key = "18703", source), MangaTag(title = "Defloration", key = "517", source), MangaTag(title = "Demon", key = "4093", source), MangaTag(title = "Demon Girl", key = "819", source), MangaTag(title = "Denki Anma", key = "33586", source), MangaTag(title = "Detached Sleeves", key = "23000", source), MangaTag(title = "Diaper", key = "3633", source), MangaTag(title = "Dick Growth", key = "818", source), MangaTag(title = "Dickgirl on Dickgirl", key = "3137", source), MangaTag(title = "Dickgirl On Female", key = "38076", source), MangaTag(title = "Dickgirl on Male", key = "935", source), MangaTag(title = "Dickgirls Only", key = "3138", source), MangaTag(title = "Dicknipples", key = "920", source), MangaTag(title = "DILF", key = "805", source), MangaTag(title = "Dinosaur", key = "25687", source), MangaTag(title = "Dismantling", key = "22357", source), MangaTag(title = "Dog", key = "2021", source), MangaTag(title = "Dog Boy", key = "5146", source), MangaTag(title = "Dog Girl", key = "1283", source), MangaTag(title = "Doll Joints", key = "20167", source), MangaTag(title = "Dolphin", key = "9227", source), MangaTag(title = "Domination Loss", key = "20093", source), MangaTag(title = "Double Anal", key = "788", source), MangaTag(title = "Double Blowjob", key = "1834", source), MangaTag(title = "Double Penetration", key = "662", source), MangaTag(title = "Double Vaginal", key = "1400", source), MangaTag(title = "Dougi", key = "2499", source), MangaTag(title = "Dragon", key = "4271", source), MangaTag(title = "Drill Hair", key = "19763", source), MangaTag(title = "Drugs", key = "618", source), MangaTag(title = "Drunk", key = "694", source), MangaTag(title = "Ear Fuck", key = "5067", source), MangaTag(title = "Eel", key = "10861", source), MangaTag(title = "Eggs", key = "19699", source), MangaTag(title = "Electric Shocks", key = "1282", source), MangaTag(title = "Elf", key = "518", source), MangaTag(title = "Emotionless Sex", key = "1552", source), MangaTag(title = "Enema", key = "752", source), MangaTag(title = "Exhibitionism", key = "689", source), MangaTag(title = "Exposed Clothing", key = "18718", source), MangaTag(title = "Eye Penetration", key = "20645", source), MangaTag(title = "Eye-covering Bang", key = "19412", source), MangaTag(title = "Eyemask", key = "1216", source), MangaTag(title = "Eyepatch", key = "1093", source), MangaTag(title = "Facesitting", key = "1307", source), MangaTag(title = "Facial Hair", key = "13734", source), MangaTag(title = "Fairy", key = "6977", source), MangaTag(title = "Fanny Packing", key = "33447", source), MangaTag(title = "Farting", key = "1939", source), MangaTag(title = "Father", key = "7560", source), MangaTag(title = "Females Only", key = "1104", source), MangaTag(title = "Femdom", key = "669", source), MangaTag(title = "Feminization", key = "1258", source), MangaTag(title = "FFF Threesome", key = "15868", source), MangaTag(title = "FFM Threesome", key = "635", source), MangaTag(title = "FFT Threesome", key = "2576", source), MangaTag(title = "Filming", key = "1073", source), MangaTag(title = "Fingering", key = "508", source), MangaTag(title = "First Person Perspective", key = "2616", source), MangaTag(title = "Fish", key = "9679", source), MangaTag(title = "Fishnets", key = "5735", source), MangaTag(title = "Fisting", key = "776", source), MangaTag(title = "Focus Anal", key = "19509", source), MangaTag(title = "Focus Blowjob", key = "19644", source), MangaTag(title = "Focus Paizuri", key = "20164", source), MangaTag(title = "Food On Body", key = "25508", source), MangaTag(title = "Foot Insertion", key = "6378", source), MangaTag(title = "Foot Licking", key = "1298", source), MangaTag(title = "Footjob", key = "762", source), MangaTag(title = "Forbidden Content", key = "27175", source), MangaTag(title = "Forced Exposure", key = "19638", source), MangaTag(title = "Forniphilia", key = "1783", source), MangaTag(title = "Fox", key = "38847", source), MangaTag(title = "Fox Boy", key = "2666", source), MangaTag(title = "Fox Girl", key = "976", source), MangaTag(title = "Freckles", key = "1767", source), MangaTag(title = "Frog", key = "13055", source), MangaTag(title = "Frog Girl", key = "17940", source), MangaTag(title = "Frottage", key = "19664", source), MangaTag(title = "Full Censorship", key = "2198", source), MangaTag(title = "Full Color", key = "526", source), MangaTag(title = "Full-packaged Futanari", key = "37309", source), MangaTag(title = "Fundoshi", key = "2471", source), MangaTag(title = "Furry", key = "4133", source), MangaTag(title = "Futanari", key = "772", source), MangaTag(title = "Futanarization", key = "10419", source), MangaTag(title = "Gag", key = "902", source), MangaTag(title = "Gang Rape", key = "35950", source), MangaTag(title = "Gaping", key = "821", source), MangaTag(title = "Garter Belt", key = "704", source), MangaTag(title = "Gasmask", key = "3512", source), MangaTag(title = "Gender Change", key = "19477", source), MangaTag(title = "Gender Morph", key = "19478", source), MangaTag(title = "Genital Piercing", key = "29426", source), MangaTag(title = "Ghost", key = "1424", source), MangaTag(title = "Giant", key = "5692", source), MangaTag(title = "Giant Sperm", key = "31910", source), MangaTag(title = "Giantess", key = "2999", source), MangaTag(title = "Gigantic Breasts", key = "20243", source), MangaTag(title = "Gijinka", key = "28460", source), MangaTag(title = "Giraffe Girl", key = "35803", source), MangaTag(title = "Glasses", key = "683", source), MangaTag(title = "Glory Hole", key = "2692", source), MangaTag(title = "Gloves", key = "2913", source), MangaTag(title = "Goat", key = "43576", source), MangaTag(title = "Goblin", key = "1460", source), MangaTag(title = "Gokkun", key = "1261", source), MangaTag(title = "Gorilla", key = "27514", source), MangaTag(title = "Gothic Lolita", key = "19892", source), MangaTag(title = "Goudoushi", key = "20585", source), MangaTag(title = "Granddaughter", key = "2050", source), MangaTag(title = "Grandfather", key = "38921", source), MangaTag(title = "Grandmother", key = "5397", source), MangaTag(title = "Group", key = "2184", source), MangaTag(title = "Growth", key = "5063", source), MangaTag(title = "Guro", key = "643", source), MangaTag(title = "Gyaru", key = "572", source), MangaTag(title = "Gyaru-Oh", key = "701", source), MangaTag(title = "Gymshorts", key = "3772", source), MangaTag(title = "Haigure", key = "28096", source), MangaTag(title = "Hair Buns", key = "9814", source), MangaTag(title = "Hairjob", key = "1988", source), MangaTag(title = "Hairy", key = "560", source), MangaTag(title = "Hairy Armpits", key = "1125", source), MangaTag(title = "Halo", key = "37176", source), MangaTag(title = "Handicapped", key = "10956", source), MangaTag(title = "Handjob", key = "722", source), MangaTag(title = "Hanging", key = "22471", source), MangaTag(title = "Harem", key = "521", source), MangaTag(title = "Harness", key = "19326", source), MangaTag(title = "Harpy", key = "8447", source), MangaTag(title = "Headless", key = "24608", source), MangaTag(title = "Headphones", key = "21781", source), MangaTag(title = "Heterochromia", key = "2849", source), MangaTag(title = "Hidden Sex", key = "602", source), MangaTag(title = "High Heels", key = "33815", source), MangaTag(title = "Hijab", key = "24401", source), MangaTag(title = "Hood", key = "20632", source), MangaTag(title = "Horns", key = "705", source), MangaTag(title = "Horse", key = "1233", source), MangaTag(title = "Horse Boy", key = "7317", source), MangaTag(title = "Horse Cock", key = "3713", source), MangaTag(title = "Horse Girl", key = "2283", source), MangaTag(title = "Hotpants", key = "624", source), MangaTag(title = "How To", key = "19960", source), MangaTag(title = "Huge Breasts", key = "588", source), MangaTag(title = "Huge Penis", key = "899", source), MangaTag(title = "Human Cattle", key = "1885", source), MangaTag(title = "Human on Furry", key = "949", source), MangaTag(title = "Humiliation", key = "720", source), MangaTag(title = "Hyena Girl", key = "48851", source), MangaTag(title = "Impregnation", key = "539", source), MangaTag(title = "Incest", key = "593", source), MangaTag(title = "Incomplete", key = "2548", source), MangaTag(title = "Infantilism", key = "5835", source), MangaTag(title = "Inflation", key = "724", source), MangaTag(title = "Insect", key = "3355", source), MangaTag(title = "Insect Boy", key = "37068", source), MangaTag(title = "Insect Girl", key = "959", source), MangaTag(title = "Inseki", key = "832", source), MangaTag(title = "Internal Urination", key = "19769", source), MangaTag(title = "Inverted Nipples", key = "745", source), MangaTag(title = "Invisible", key = "914", source), MangaTag(title = "Josou Seme", key = "553", source), MangaTag(title = "Kangaroo", key = "36695", source), MangaTag(title = "Kangaroo Girl", key = "48492", source), MangaTag(title = "Kappa", key = "10047", source), MangaTag(title = "Kemonomimi", key = "711", source), MangaTag(title = "Kigurumi Pajama", key = "21402", source), MangaTag(title = "Kimono", key = "712", source), MangaTag(title = "Kindergarten Uniform", key = "27567", source), MangaTag(title = "Kissing", key = "509", source), MangaTag(title = "Kneepit Sex", key = "7309", source), MangaTag(title = "Kodomo Doushi", key = "42716", source), MangaTag(title = "Kodomo Only", key = "43498", source), MangaTag(title = "Kunoichi", key = "1202", source), MangaTag(title = "Lab Coat", key = "2677", source), MangaTag(title = "Lactation", key = "721", source), MangaTag(title = "Large Insertions", key = "774", source), MangaTag(title = "Large Tattoo", key = "19410", source), MangaTag(title = "Latex", key = "742", source), MangaTag(title = "Layer Cake", key = "1172", source), MangaTag(title = "Leash", key = "11350", source), MangaTag(title = "Leg Lock", key = "573", source), MangaTag(title = "Legjob", key = "4435", source), MangaTag(title = "Leotard", key = "1052", source), MangaTag(title = "Lingerie", key = "583", source), MangaTag(title = "Lion", key = "24177", source), MangaTag(title = "Lioness", key = "47736", source), MangaTag(title = "Lipstick Mark", key = "33164", source), MangaTag(title = "Living Clothes", key = "2109", source), MangaTag(title = "Lizard Girl", key = "2888", source), MangaTag(title = "Lizard Guy", key = "950", source), MangaTag(title = "Lolicon", key = "19429", source), MangaTag(title = "Long Tongue", key = "820", source), MangaTag(title = "Low Bestiality", key = "2463", source), MangaTag(title = "Low Guro", key = "29597", source), MangaTag(title = "Low Incest", key = "46409", source), MangaTag(title = "Low Lolicon", key = "1546", source), MangaTag(title = "Low Scat", key = "20053", source), MangaTag(title = "Low Shotacon", key = "6326", source), MangaTag(title = "Low Smegma", key = "22748", source), MangaTag(title = "Machine", key = "3082", source), MangaTag(title = "Maggot", key = "16161", source), MangaTag(title = "Magical Girl", key = "1021", source), MangaTag(title = "Maid", key = "582", source), MangaTag(title = "Makeup", key = "21366", source), MangaTag(title = "Male on Dickgirl", key = "4143", source), MangaTag(title = "Males Only", key = "551", source), MangaTag(title = "Masked Face", key = "1427", source), MangaTag(title = "Masturbation", key = "600", source), MangaTag(title = "Mecha Boy", key = "29192", source), MangaTag(title = "Mecha Girl", key = "858", source), MangaTag(title = "Menstruation", key = "14092", source), MangaTag(title = "Mermaid", key = "4272", source), MangaTag(title = "Merman", key = "17859", source), MangaTag(title = "Mesugaki", key = "36081", source), MangaTag(title = "Mesuiki", key = "22448", source), MangaTag(title = "Metal Armor", key = "1973", source), MangaTag(title = "Midget", key = "4738", source), MangaTag(title = "Miko", key = "977", source), MangaTag(title = "MILF", key = "784", source), MangaTag(title = "Military", key = "695", source), MangaTag(title = "Milking", key = "1784", source), MangaTag(title = "Mind Break", key = "726", source), MangaTag(title = "Mind Control", key = "741", source), MangaTag(title = "Minigirl", key = "4892", source), MangaTag(title = "Miniguy", key = "2906", source), MangaTag(title = "Minotaur", key = "3625", source), MangaTag(title = "Missing Cover", key = "21413", source), MangaTag(title = "MMF Threesome", key = "678", source), MangaTag(title = "MMM Threesome", key = "16530", source), MangaTag(title = "MMT Threesome", key = "16278", source), MangaTag(title = "Monkey", key = "4679", source), MangaTag(title = "Monkey Boy", key = "25084", source), MangaTag(title = "Monkey Girl", key = "3901", source), MangaTag(title = "Monoeye", key = "13309", source), MangaTag(title = "Monster", key = "1598", source), MangaTag(title = "Monster Girl", key = "839", source), MangaTag(title = "Moral Degeneration", key = "723", source), MangaTag(title = "Mosaic Censorship", key = "990", source), MangaTag(title = "Mother", key = "1126", source), MangaTag(title = "Mouse", key = "44405", source), MangaTag(title = "Mouse Boy", key = "15530", source), MangaTag(title = "Mouse Girl", key = "1446", source), MangaTag(title = "Mouth Mask", key = "20294", source), MangaTag(title = "MTF Threesome", key = "1127", source), MangaTag(title = "Multi-Work Series", key = "568", source), MangaTag(title = "Multimouth Blowjob", key = "20095", source), MangaTag(title = "Multipanel Sequence", key = "20090", source), MangaTag(title = "Multiple Arms", key = "10315", source), MangaTag(title = "Multiple Assjob", key = "45655", source), MangaTag(title = "Multiple Breasts", key = "924", source), MangaTag(title = "Multiple Footjob", key = "25452", source), MangaTag(title = "Multiple Handjob", key = "24220", source), MangaTag(title = "Multiple Orgasms", key = "20267", source), MangaTag(title = "Multiple Paizuri", key = "2131", source), MangaTag(title = "Multiple Penises", key = "921", source), MangaTag(title = "Multiple Straddling", key = "25009", source), MangaTag(title = "Multiple Tails", key = "48489", source), MangaTag(title = "Multiple Vaginas", key = "27053", source), MangaTag(title = "Muscle", key = "688", source), MangaTag(title = "Muscle Growth", key = "27743", source), MangaTag(title = "Mute", key = "6084", source), MangaTag(title = "Nakadashi", key = "524", source), MangaTag(title = "Navel Birth", key = "46613", source), MangaTag(title = "Navel Fuck", key = "1383", source), MangaTag(title = "Nazi", key = "21454", source), MangaTag(title = "Necrophilia", key = "3356", source), MangaTag(title = "Netorare", key = "725", source), MangaTag(title = "Netorase", key = "37079", source), MangaTag(title = "Niece", key = "594", source), MangaTag(title = "Ninja", key = "4069", source), MangaTag(title = "Nipple Birth", key = "16160", source), MangaTag(title = "Nipple Expansion", key = "11371", source), MangaTag(title = "Nipple Fuck", key = "881", source), MangaTag(title = "Nipple Piercing", key = "29465", source), MangaTag(title = "Nipple Stimulation", key = "23481", source), MangaTag(title = "No Balls", key = "44539", source), MangaTag(title = "No Penetration", key = "9743", source), MangaTag(title = "Nose Fuck", key = "20515", source), MangaTag(title = "Nose Hook", key = "2520", source), MangaTag(title = "Novel", key = "13396", source), MangaTag(title = "Nudism", key = "29533", source), MangaTag(title = "Nudity Only", key = "21265", source), MangaTag(title = "Nun", key = "1149", source), MangaTag(title = "Nurse", key = "848", source), MangaTag(title = "Object Insertion Only", key = "45827", source), MangaTag(title = "Octopus", key = "3231", source), MangaTag(title = "Oil", key = "1343", source), MangaTag(title = "Old Lady", key = "15445", source), MangaTag(title = "Old Man", key = "3646", source), MangaTag(title = "Omorashi", key = "15965", source), MangaTag(title = "Onahole", key = "840", source), MangaTag(title = "Oni", key = "3763", source), MangaTag(title = "Onsen", key = "49118", source), MangaTag(title = "Oppai Loli", key = "1107", source), MangaTag(title = "Orc", key = "1800", source), MangaTag(title = "Orgasm Denial", key = "754", source), MangaTag(title = "Otokofutanari", key = "23865", source), MangaTag(title = "Otter Girl", key = "24463", source), MangaTag(title = "Out of Order", key = "5419", source), MangaTag(title = "Oyakodon", key = "794", source), MangaTag(title = "Painted Nails", key = "32700", source), MangaTag(title = "Paizuri", key = "578", source), MangaTag(title = "Panda Girl", key = "40977", source), MangaTag(title = "Panther", key = "45015", source), MangaTag(title = "Pantyhose", key = "523", source), MangaTag(title = "Pantyjob", key = "2167", source), MangaTag(title = "Parasite", key = "883", source), MangaTag(title = "Pasties", key = "886", source), MangaTag(title = "Pegasus", key = "39837", source), MangaTag(title = "Pegging", key = "789", source), MangaTag(title = "Penis Birth", key = "16839", source), MangaTag(title = "Penis Enlargement", key = "37974", source), MangaTag(title = "Penis Reduction", key = "49324", source), MangaTag(title = "Personality Excretion", key = "18425", source), MangaTag(title = "Petplay", key = "12419", source), MangaTag(title = "Petrification", key = "16013", source), MangaTag(title = "Phimosis", key = "567", source), MangaTag(title = "Phone Sex", key = "11507", source), MangaTag(title = "Piercing", key = "645", source), MangaTag(title = "Pig", key = "922", source), MangaTag(title = "Pig Girl", key = "4238", source), MangaTag(title = "Pig Man", key = "8430", source), MangaTag(title = "Pillory", key = "773", source), MangaTag(title = "Pirate", key = "4019", source), MangaTag(title = "Piss Drinking", key = "1026", source), MangaTag(title = "Pixie Cut", key = "1551", source), MangaTag(title = "Plant Girl", key = "9592", source), MangaTag(title = "Pole Dancing", key = "1217", source), MangaTag(title = "Policeman", key = "8867", source), MangaTag(title = "Policewoman", key = "656", source), MangaTag(title = "Ponygirl", key = "7316", source), MangaTag(title = "Ponytail", key = "528", source), MangaTag(title = "Possession", key = "1084", source), MangaTag(title = "Pregnant", key = "824", source), MangaTag(title = "Prehensile Hair", key = "833", source), MangaTag(title = "Priest", key = "9589", source), MangaTag(title = "Prolapse", key = "1451", source), MangaTag(title = "Property Tag", key = "48632", source), MangaTag(title = "Prostate Massage", key = "936", source), MangaTag(title = "Prostitution", key = "670", source), MangaTag(title = "Pubic Stubble", key = "1128", source), MangaTag(title = "Public Use", key = "727", source), MangaTag(title = "Rabbit", key = "30709", source), MangaTag(title = "Raccoon Boy", key = "16455", source), MangaTag(title = "Raccoon Girl", key = "3204", source), MangaTag(title = "Race Queen", key = "4056", source), MangaTag(title = "Randoseru", key = "2180", source), MangaTag(title = "Rape", key = "595", source), MangaTag(title = "Real Doll", key = "26129", source), MangaTag(title = "Redraw", key = "20203", source), MangaTag(title = "Reptile", key = "27901", source), MangaTag(title = "Retractable Penis", key = "27465", source), MangaTag(title = "Rimjob", key = "1658", source), MangaTag(title = "Robot", key = "1357", source), MangaTag(title = "Rough Grammar", key = "19632", source), MangaTag(title = "Rough Translation", key = "20231", source), MangaTag(title = "Ryona", key = "648", source), MangaTag(title = "Saliva", key = "5077", source), MangaTag(title = "Sample", key = "21346", source), MangaTag(title = "Sarashi", key = "24902", source), MangaTag(title = "Scanmark", key = "6058", source), MangaTag(title = "Scar", key = "1016", source), MangaTag(title = "Scat", key = "1561", source), MangaTag(title = "Scat Insertion", key = "33632", source), MangaTag(title = "School Gym Uniform", key = "19467", source), MangaTag(title = "School Swimsuit", key = "850", source), MangaTag(title = "Schoolboy Uniform", key = "782", source), MangaTag(title = "Schoolgirl Uniform", key = "559", source), MangaTag(title = "Scrotal Lingerie", key = "856", source), MangaTag(title = "Selfcest", key = "504", source), MangaTag(title = "Sex Toys", key = "604", source), MangaTag(title = "Shared Senses", key = "1027", source), MangaTag(title = "Shark", key = "36551", source), MangaTag(title = "Shark Boy", key = "17467", source), MangaTag(title = "Shark Girl", key = "17721", source), MangaTag(title = "Shaved Head", key = "20082", source), MangaTag(title = "Sheep", key = "36805", source), MangaTag(title = "Sheep Boy", key = "19915", source), MangaTag(title = "Sheep Girl", key = "1542", source), MangaTag(title = "Shemale", key = "1179", source), MangaTag(title = "Shibari", key = "1075", source), MangaTag(title = "Shimaidon", key = "12201", source), MangaTag(title = "Shimapan", key = "2077", source), MangaTag(title = "Short Hair", key = "2412", source), MangaTag(title = "Shotacon", key = "550", source), MangaTag(title = "Shrinking", key = "5065", source), MangaTag(title = "Sister", key = "780", source), MangaTag(title = "Sketch Lines", key = "21405", source), MangaTag(title = "Skinsuit", key = "3157", source), MangaTag(title = "Slave", key = "1103", source), MangaTag(title = "Sleeping", key = "617", source), MangaTag(title = "Slime", key = "1085", source), MangaTag(title = "Slime Boy", key = "26099", source), MangaTag(title = "Slime Girl", key = "3241", source), MangaTag(title = "Slug", key = "3353", source), MangaTag(title = "Small Breasts", key = "3239", source), MangaTag(title = "Small Penis", key = "3096", source), MangaTag(title = "Smalldom", key = "22943", source), MangaTag(title = "Smegma", key = "2270", source), MangaTag(title = "Smell", key = "591", source), MangaTag(title = "Smoking", key = "2608", source), MangaTag(title = "Snail Girl", key = "26051", source), MangaTag(title = "Snake", key = "6795", source), MangaTag(title = "Snake Boy", key = "30194", source), MangaTag(title = "Snake Girl", key = "11731", source), MangaTag(title = "Snuff", key = "872", source), MangaTag(title = "Sockjob", key = "21270", source), MangaTag(title = "Sole Dickgirl", key = "1846", source), MangaTag(title = "Sole Female", key = "541", source), MangaTag(title = "Sole Male", key = "516", source), MangaTag(title = "Sole Pussyboy", key = "23866", source), MangaTag(title = "Solo Action", key = "1147", source), MangaTag(title = "Soushuuhen", key = "10815", source), MangaTag(title = "Spanking", key = "1299", source), MangaTag(title = "Speculum", key = "2484", source), MangaTag(title = "Spider", key = "3354", source), MangaTag(title = "Spider Girl", key = "961", source), MangaTag(title = "Squid Boy", key = "29638", source), MangaTag(title = "Squid Girl", key = "1803", source), MangaTag(title = "Squirrel Boy", key = "43338", source), MangaTag(title = "Squirrel Girl", key = "8744", source), MangaTag(title = "Squirting", key = "584", source), MangaTag(title = "SSBBM", key = "15882", source), MangaTag(title = "SSBBW", key = "12508", source), MangaTag(title = "Steward", key = "44689", source), MangaTag(title = "Stewardess", key = "5686", source), MangaTag(title = "Stirrup Legwear", key = "20542", source), MangaTag(title = "Stockings", key = "522", source), MangaTag(title = "Stomach Deformation", key = "642", source), MangaTag(title = "Story Arc", key = "1119", source), MangaTag(title = "Straitjacket", key = "20066", source), MangaTag(title = "Strap-On", key = "793", source), MangaTag(title = "Stretching", key = "2929", source), MangaTag(title = "Stuck in Wall", key = "623", source), MangaTag(title = "Sumata", key = "654", source), MangaTag(title = "Sundress", key = "842", source), MangaTag(title = "Sunglasses", key = "690", source), MangaTag(title = "Sweating", key = "581", source), MangaTag(title = "Swimsuit", key = "527", source), MangaTag(title = "Swinging", key = "1764", source), MangaTag(title = "Syringe", key = "2159", source), MangaTag(title = "Tabi Socks", key = "38077", source), MangaTag(title = "Table Masturbation", key = "1434", source), MangaTag(title = "Tail", key = "1881", source), MangaTag(title = "Tail Plug", key = "865", source), MangaTag(title = "Tailjob", key = "2080", source), MangaTag(title = "Tailphagia", key = "9382", source), MangaTag(title = "Tall Girl", key = "859", source), MangaTag(title = "Tall Man", key = "2005", source), MangaTag(title = "Tankoubon", key = "1129", source), MangaTag(title = "Tanlines", key = "1120", source), MangaTag(title = "Teacher", key = "748", source), MangaTag(title = "Tentacles", key = "719", source), MangaTag(title = "Thick Eyebrows", key = "20311", source), MangaTag(title = "Thigh High Boots", key = "758", source), MangaTag(title = "Tiara", key = "1243", source), MangaTag(title = "Tickling", key = "1394", source), MangaTag(title = "Tiger", key = "32860", source), MangaTag(title = "Tights", key = "5027", source), MangaTag(title = "Time Stop", key = "1553", source), MangaTag(title = "Tomboy", key = "1124", source), MangaTag(title = "Tomgirl", key = "2932", source), MangaTag(title = "Tooth Brushing", key = "24271", source), MangaTag(title = "Torture", key = "1461", source), MangaTag(title = "Tracksuit", key = "736", source), MangaTag(title = "Trampling", key = "1912", source), MangaTag(title = "Transformation", key = "829", source), MangaTag(title = "Transparent Clothing", key = "20297", source), MangaTag(title = "Tribadism", key = "1170", source), MangaTag(title = "Triple Anal", key = "2503", source), MangaTag(title = "Triple Penetration", key = "1234", source), MangaTag(title = "Triple Vaginal", key = "4370", source), MangaTag(title = "TTF Threesome", key = "3937", source), MangaTag(title = "TTM Threesome", key = "1965", source), MangaTag(title = "TTT Threesome", key = "18029", source), MangaTag(title = "Tube", key = "3083", source), MangaTag(title = "Turtle", key = "21828", source), MangaTag(title = "Tutor", key = "851", source), MangaTag(title = "Twins", key = "655", source), MangaTag(title = "Twintails", key = "644", source), MangaTag(title = "Unbirth", key = "3737", source), MangaTag(title = "Uncensored", key = "763", source), MangaTag(title = "Uncle", key = "17213", source), MangaTag(title = "Underwater", key = "2478", source), MangaTag(title = "Unicorn", key = "29657", source), MangaTag(title = "Unusual Insertions", key = "19762", source), MangaTag(title = "Unusual Pupils", key = "2937", source), MangaTag(title = "Unusual Teeth", key = "903", source), MangaTag(title = "Urethra Insertion", key = "790", source), MangaTag(title = "Urination", key = "3467", source), MangaTag(title = "Vacbed", key = "2678", source), MangaTag(title = "Vaginal Birth", key = "27911", source), MangaTag(title = "Vaginal Sticker", key = "2360", source), MangaTag(title = "Vampire", key = "983", source), MangaTag(title = "Variant Set", key = "25869", source), MangaTag(title = "Very Long Hair", key = "19406", source), MangaTag(title = "Virginity", key = "570", source), MangaTag(title = "Vomit", key = "1035", source), MangaTag(title = "Vore", key = "2905", source), MangaTag(title = "Voyeurism", key = "601", source), MangaTag(title = "Vtuber", key = "12015", source), MangaTag(title = "Waiter", key = "5638", source), MangaTag(title = "Waitress", key = "4989", source), MangaTag(title = "Watermarked", key = "19993", source), MangaTag(title = "Webtoon", key = "25449", source), MangaTag(title = "Weight Gain", key = "2007", source), MangaTag(title = "Western Cg", key = "49018", source), MangaTag(title = "Western Imageset", key = "49379", source), MangaTag(title = "Wet Clothes", key = "878", source), MangaTag(title = "Whale", key = "45881", source), MangaTag(title = "Whip", key = "7679", source), MangaTag(title = "Widow", key = "1770", source), MangaTag(title = "Widower", key = "19524", source), MangaTag(title = "Wingjob", key = "25570", source), MangaTag(title = "Wings", key = "849", source), MangaTag(title = "Witch", key = "1250", source), MangaTag(title = "Wolf", key = "2464", source), MangaTag(title = "Wolf Boy", key = "9674", source), MangaTag(title = "Wolf Girl", key = "1059", source), MangaTag(title = "Wooden Horse", key = "6027", source), MangaTag(title = "Worm", key = "19593", source), MangaTag(title = "Wormhole", key = "2170", source), MangaTag(title = "Wrestling", key = "3249", source), MangaTag(title = "X-Ray", key = "543", source), MangaTag(title = "Yandere", key = "781", source), MangaTag(title = "Yaoi", key = "552", source), MangaTag(title = "Yuri", key = "880", source), MangaTag(title = "Zebra", key = "47737", source), MangaTag(title = "Zombie", key = "3357", source)
	)
}
