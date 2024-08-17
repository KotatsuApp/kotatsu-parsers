package org.koitharu.kotatsu.parsers.site.tr

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRWEBTOON", "TrWebtoon", "tr")
class TrWebtoon(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.TRWEBTOON, pageSize = 21) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("trwebtoon.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.POPULARITY, SortOrder.ALPHABETICAL, SortOrder.ALPHABETICAL_DESC, SortOrder.UPDATED)

	override val availableStates: Set<MangaState> = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)

	override val isMultipleTagsSupported = false

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		when (filter) {
			is MangaListFilter.Search -> {
				val url = buildString {
					append("https://")
					append(domain)
					append("/webtoon-listesi?page=")
					append(page.toString())
					append("&q=")
					append(filter.query.urlEncoded())
					append("&sort=views&short_type=DESC")
				}
				return parseMangaList(webClient.httpGet(url).parseHtml())
			}

			is MangaListFilter.Advanced -> {

				if (filter.sortOrder == SortOrder.UPDATED) {
					if (filter.tags.isNotEmpty()) {
						throw IllegalArgumentException("Sort order updated + Tags or States is not supported by this source")
					}
					val url = buildString {
						append("https://")
						append(domain)
						append("/son-eklenenler?page=")
						append(page.toString())
					}
					return parseMangaListUpdated(webClient.httpGet(url).parseHtml())
				} else {
					val url = buildString {
						append("https://")
						append(domain)
						append("/webtoon-listesi?page=")
						append(page.toString())
						filter.tags.oneOrThrowIfMany()?.let {
							append("&genre=")
							append(it.key)
						}
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							append(
								when (it) {
									MangaState.ONGOING -> "continues"
									MangaState.FINISHED -> "complated"
									else -> ""
								},
							)
						}
						append("&sort=")
						when (filter.sortOrder) {
							SortOrder.POPULARITY -> append("views&short_type=DESC")
							SortOrder.ALPHABETICAL -> append("name&short_type=ASC")
							SortOrder.ALPHABETICAL_DESC -> append("name&short_type=DESC")
							else -> append("views&short_type=DESC")
						}
					}

					return parseMangaList(webClient.httpGet(url).parseHtml())
				}

			}

			null -> {
				val url = buildString {
					append("https://")
					append(domain)
					append("/son-eklenenler?page=")
					append(page.toString())
				}
				return parseMangaListUpdated(webClient.httpGet(url).parseHtml())
			}
		}
	}


	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(".row .col-xl-4 .card-body").map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = li.selectFirst("img")?.src().orEmpty(),
				title = li.selectFirst(".table-responsive a")?.text().orEmpty(),
				altTitle = null,
				rating = li.selectFirst(".row .col-xl-4 .mt-2 .my-1 .text-muted")?.text()?.substringBefore("/")
					?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = when (doc.selectLast(".row .col-xl-4 .mt-2 .rounded-pill")?.text()) {
					"Devam Ediyor", "Güncel" -> MangaState.ONGOING
					"Tamamlandı" -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	private fun parseMangaListUpdated(doc: Document): List<Manga> {
		return doc.select(".page-content div.bslist_item").map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = li.selectFirst(".figure img")?.src().orEmpty(),
				title = li.selectFirst(".title")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = when (doc.selectFirst("d-inline .badge")?.text()) {
					"Devam Ediyor", "Güncel" -> MangaState.ONGOING
					"Tamamlandı" -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val tags =
			webClient.httpGet("https://$domain/webtoon-listesi").parseHtml().requireElementById("collapseExample")
				.select(".pt-12 a").drop(1)
		return tags.mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast("genre=").substringBefore("&sort"),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			tags = doc.body().select("li.movie__year a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text(),
					source = source,
				)
			},
			description = doc.select("p.movie__plot").html(),
			state = when (doc.selectFirstOrThrow(".movie__credits span.rounded-pill").text()) {
				"Devam Ediyor", "Güncel" -> MangaState.ONGOING
				"Tamamlandı" -> MangaState.FINISHED
				else -> null
			},
			chapters = doc.requireElementById("chapters").select("tbody tr").mapChapters(reversed = true) { i, tr ->
				val url = tr.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = tr.selectFirstOrThrow("a").text(),
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = parseChapterDate(
						SimpleDateFormat("dd/MM/yyyy", sourceLocale),
						tr.selectLastOrThrow("td").selectFirstOrThrow("span").text(),
					),
					branch = null,
					source = source,
				)
			},
		)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.startsWith("saat ") -> Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			d.endsWith(" önce") -> parseRelativeDate(date)

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("saat").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("gün").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("hafta").anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
			WordSet("ay").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("yıl").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("images")
		return doc.select("img").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
