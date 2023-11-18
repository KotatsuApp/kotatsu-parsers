package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("LXMANGA", "LxManga", "vi")
internal class LxManga(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.LXMANGA, 60) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override val configKeyDomain = ConfigKey.Domain("lxmanga.net")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!query.isNullOrEmpty() -> {
					val skey = "filter[name]=".urlEncoded()
					append("/tim-kiem?$skey")
					append(query.urlEncoded())
				}

				!tags.isNullOrEmpty() -> {
					append("/the-loai/")
					for (tag in tags) {
						append(tag.key)
					}
				}

				else -> {
					append("/danh-sach")
				}
			}
			append("?page=")
			append(page.toString())
			append("&sort=")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("-views")
				SortOrder.UPDATED -> append("-updated_at")
				SortOrder.NEWEST -> append("-created_at")
				SortOrder.ALPHABETICAL -> append("name")

				else -> append("-updated_at")
			}
		}


		val doc = webClient.httpGet(url).parseHtml()


		return doc.select("div.grid div.manga-vertical")
			.map { div ->
				val href = div.selectFirstOrThrow("a").attr("href")
				val img = div.selectFirstOrThrow(".cover").attr("style").substringAfter("url('").substringBefore("')")
				Manga(
					id = generateUid(href),
					title = div.selectFirstOrThrow("a.text-ellipsis").text(),
					altTitle = null,
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = img,
					tags = setOf(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

		return manga.copy(
			altTitle = root.select(".divider2:contains(Noms associés :)").firstOrNull()?.text(),
			state = when (root.select("div.grow div.mt-2:contains(Tình trạng) a").first()!!.text()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Đã hoàn thành" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("div.grow div.mt-2:contains(Thể loại) span a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = root.select("div.grow div.mt-2:contains(Tác giả) span a")
				.joinToString { it.text().trim(',', ' ') },
			description = root.selectFirst("div.py-4.border-t")?.html(),
			chapters = root.select("ul.overflow-y-auto.overflow-x-hidden a")
				.mapChapters(reversed = true) { i, a ->

					val href = a.attr("href")
					val name = a.selectFirstOrThrow("span.text-ellipsis").text()
					val date = a.selectFirstOrThrow("span.timeago").attr("datetime")
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(date),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)

		val doc = webClient.httpGet(fullUrl).parseHtml()

		return doc.select("div.text-center img.lazy").map { img ->
			val url = img.attrAsRelativeUrlOrNull("data-src") ?: img.attrAsRelativeUrlOrNull("src")
			?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val body = doc.body()
		return body.select("ul.absolute.w-full a").mapToSet { a ->

			MangaTag(
				key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
				title = a.selectFirstOrThrow("span.text-ellipsis").text(),
				source = source,
			)
		}
	}
}
