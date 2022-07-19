package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAOWL", "MangaOwl", "en")
internal class MangaOwlParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.MANGAOWL) {

	override val configKeyDomain = ConfigKey.Domain("mangaowls.com", null)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.UPDATED,
	)

	private val regexNsfw = Regex("(yaoi)|(yuri)|(smut)|(mature)|(adult)", RegexOption.IGNORE_CASE)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val page = (offset / 36f).toIntUp().inc()
		val link = buildString {
			append("https://")
			append(getDomain())
			when {
				!query.isNullOrEmpty() -> {
					append("/search/$page?search=")
					append(query.urlEncoded())
				}

				!tags.isNullOrEmpty() -> {
					for (tag in tags) {
						append(tag.key)
					}
					append("/$page?type=${getAlternativeSortKey(sortOrder)}")
				}

				else -> {
					append("/${getSortKey(sortOrder)}/$page")
				}
			}
		}
		val doc = context.httpGet(link).parseHtml()
		val slides = doc.body().selectOrThrow("ul.slides")
		val items = slides.select("div.col-md-2")
		return items.mapNotNull { item ->
			val href = item.selectFirst("h6 a")?.attrAsRelativeUrlOrNull("href") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = item.selectFirst("h6 a")?.text() ?: return@mapNotNull null,
				coverUrl = item.select("div.img-responsive").attr("abs:data-background-image"),
				altTitle = null,
				author = null,
				rating = runCatching {
					item.selectFirst("div.block-stars")
						?.text()
						?.toFloatOrNull()
						?.div(10f)
				}.getOrNull() ?: RATING_UNKNOWN,
				url = href,
				isNsfw = false,
				tags = emptySet(),
				state = null,
				publicUrl = href.toAbsoluteUrl(getDomain()),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.publicUrl).parseHtml()
		val info = doc.body().selectFirstOrThrow("div.single_detail")
		val table = doc.body().selectFirstOrThrow("div.single-grid-right")
		val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
		val trRegex = "window\\['tr'] = '([^']*)';".toRegex(RegexOption.IGNORE_CASE)
		val trElement = doc.getElementsByTag("script").find { trRegex.find(it.data()) != null }
			?: doc.parseFailed("Oops, tr not found")
		val tr = trRegex.find(trElement.data())!!.groups[1]!!.value
		val s = context.encodeBase64(getDomain().toByteArray())
		var isNsfw = manga.isNsfw
		val parsedTags = info.select("div.col-xs-12.col-md-8.single-right-grid-right > p > a[href*=genres]")
			.mapNotNullToSet {
				val a = it.selectFirst("a") ?: return@mapNotNullToSet null
				val name = a.text()
				if (!isNsfw && isNsfwGenre(name)) {
					isNsfw = true
				}
				MangaTag(
					title = name.toTitleCase(),
					key = a.attr("href"),
					source = source,
				)
			}
		return manga.copy(
			description = info.selectFirst(".description")?.html(),
			largeCoverUrl = info.select("img").first()?.let { img ->
				if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
			},
			isNsfw = isNsfw,
			author = info.selectFirst("p.fexi_header_para a.author_link")?.text(),
			state = parseStatus(info.select("p.fexi_header_para:contains(status)").first()?.ownText()),
			tags = manga.tags + parsedTags,
			chapters = table.select("div.table.table-chapter-list").select("li.list-group-item.chapter_list")
				.asReversed().mapChapters { i, li ->
					val a = li.select("a")
					val href = a.attr("data-href").ifEmpty {
						li.parseFailed("Link is missing")
					}
					MangaChapter(
						id = generateUid(href),
						name = a.select("label").text(),
						number = i + 1,
						url = "$href?tr=$tr&s=$s",
						scanlator = null,
						branch = null,
						uploadDate = dateFormat.tryParse(li.selectFirst("small:last-of-type")?.text()),
						source = MangaSource.MANGAOWL,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectOrThrow("div.item img.owl-lazy")
		return root.map { div ->
			val url = div?.attrAsRelativeUrlOrNull("data-src") ?: doc.parseFailed("Page image not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				referer = url,
				source = MangaSource.MANGAOWL,
			)
		}
	}

	private fun parseStatus(status: String?) = when {
		status == null -> null
		status.contains("Ongoing") -> MangaState.ONGOING
		status.contains("Completed") -> MangaState.FINISHED
		else -> null
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = context.httpGet("https://${getDomain()}/").parseHtml()
		val root = doc.body().select("ul.dropdown-menu.multi-column.columns-3").select("li")
		return root.mapToSet { p ->
			val a = p.selectFirstOrThrow("a")
			MangaTag(
				title = a.text().toTitleCase(),
				key = a.attr("href"),
				source = source,
			)
		}
	}

	private fun getSortKey(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.POPULARITY -> "popular"
			SortOrder.NEWEST -> "new_release"
			SortOrder.UPDATED -> "lastest"
			else -> "lastest"
		}

	private fun getAlternativeSortKey(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.POPULARITY -> "0"
			SortOrder.NEWEST -> "2"
			SortOrder.UPDATED -> "3"
			else -> "3"
		}

	private fun isNsfwGenre(name: String): Boolean = regexNsfw.containsMatchIn(name)
}