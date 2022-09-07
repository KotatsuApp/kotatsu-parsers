package org.koitharu.kotatsu.parsers.site

import org.json.JSONObject
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.associateByKey
import org.koitharu.kotatsu.parsers.util.json.stringIterator
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("KOMIKTAP", "KomikTap", "id")
class KomikTapParser(
	override val context: MangaLoaderContext,
) : PagedMangaParser(MangaSource.KOMIKTAP, pageSize = 25, searchPageSize = 10) {

	override val configKeyDomain = ConfigKey.Domain("194.233.66.232", arrayOf("194.233.66.232", "komiktap.in"))

	override val sortOrders = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = urlBuilder()
		if (query.isNullOrEmpty()) {
			url.addPathSegment("manga")
				.addQueryParameter("page", page.toString())
				.addQueryParameter("status", "")
				.addQueryParameter("type", "")
				.addQueryParameter("order", sortOrder.asQueryParameter())
			tags?.forEach {
				url.addQueryParameter("genre[]", it.key)
			}
		} else {
			url.addPathSegment("page")
				.addPathSegment(page.toString())
				.addQueryParameter("s", query)
		}
		val root = context.httpGet(url.build()).parseHtml().body()
			.requireElementById("content")
			.selectFirstOrThrow(".listupd")
		return root.select("div.bs").map { div ->
			val a = div.selectFirstOrThrow("a")
			val img = div.selectFirstOrThrow("img")
			val href = a.attrAsRelativeUrl("href")
			val bigor = div.selectFirstOrThrow(".bigor")
			Manga(
				id = generateUid(href),
				title = bigor.selectFirstOrThrow(".tt").text(),
				altTitle = null,
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = bigor.selectFirst(".rating .numscore")
					?.text()?.toFloatOrNull()?.div(10f)
					.assertNotNull("rating") ?: RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = img.attrAsAbsoluteUrl("src"),
				tags = emptySet(),
				state = when (div.selectFirst("span.status")?.text()) {
					"Completed" -> MangaState.FINISHED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml().body()
			.requireElementById("content")
			.selectFirstOrThrow("article")
		val table = root.selectFirstOrThrow(".infotable")
		val dateFormat = SimpleDateFormat("MMM d, yyyy", checkNotNull(sourceLocale))
		val chapters = root.requireElementById("chapterlist")
			.select("li")
			.mapChapters { index, li ->
				val a = li.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(href),
					name = a.selectFirstOrThrow(".chapternum").text(),
					number = index + 1,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(a.selectFirst(".chapterdate")?.text()),
					branch = null,
					source = source,
				)
			}

		return manga.copy(
			largeCoverUrl = root.selectFirst("div.thumb")
				?.selectFirst("img")
				?.attrAsAbsoluteUrlOrNull("src")
				.assertNotNull("largeCoverUrl"),
			author = table.tableValue("Author").assertNotNull("author")?.takeUnless { it == "N/A" },
			state = when (table.tableValue("Status").assertNotNull("status")) {
				"Completed" -> MangaState.FINISHED
				"Ongoing" -> MangaState.ONGOING
				else -> null
			},
			tags = root.selectFirstOrThrow(".seriestugenre")
				.select("a")
				.mapToSet { a ->
					MangaTag(
						title = a.text().toTitleCase(Locale.ENGLISH),
						key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
						source = manga.source,
					)
				},
			description = root.selectFirstOrThrow("[itemprop=\"description\"]").html(),
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet(fullUrl).parseHtml()
		val scripts = doc.select("script")
		for (script in scripts) {
			val content = script.html()
			val pos = content.indexOf("ts_reader.run")
			if (pos < 0) {
				continue
			}
			val json = JSONObject(content.substringBetween("(", ")"))
			val sources = json.getJSONArray("sources").associateByKey("source")
			val images = (sources[json.optString("defaultSource")] ?: sources.values.first()).getJSONArray("images")
			val result = ArrayList<MangaPage>(images.length())
			images.stringIterator().forEach {
				result += MangaPage(
					id = generateUid(it),
					url = it,
					referer = fullUrl,
					preview = null,
					source = source,
				)
			}
			return result
		}
		doc.parseFailed("Script with pages not found")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val root = context.httpGet("https://${getDomain()}/manga/").parseHtml()
			.selectFirstOrThrow("form.filters")
			.selectFirstOrThrow("ul.genrez")
		return root.select("li").mapNotNullToSet { li ->
			val input = li.selectFirstOrThrow("input")
			if (input.attr("name") != "genre[]") {
				return@mapNotNullToSet null
			}
			MangaTag(
				title = li.selectFirstOrThrow("label").text().toTitleCase(sourceLocale ?: Locale.ENGLISH),
				key = input.attrOrNull("value") ?: return@mapNotNullToSet null,
				source = source,
			)
		}
	}

	override fun getFaviconUrl(): String {
		return "https://${getDomain()}/wp-content/uploads/2020/09/cropped-LOGOa-180x180.png"
	}

	private fun SortOrder.asQueryParameter() = when (this) {
		SortOrder.UPDATED -> "update"
		SortOrder.POPULARITY -> "popular"
		SortOrder.NEWEST -> "latest"
		SortOrder.ALPHABETICAL -> "title"
		else -> ""
	}

	private fun Element.tableValue(key: String): String? {
		return getElementsMatchingOwnText(key).singleOrNull()?.parent()?.selectLast("td")?.text()
	}
}