package org.koitharu.kotatsu.parsers.site.zh

import androidx.collection.ArrayMap
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.util.*
import kotlin.collections.HashSet

@MangaSourceParser("BAOZIMH", "Baozimh", "zh")
internal class Baozimh(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.BAOZIMH, pageSize = 36) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY)

	override val availableStates: Set<MangaState> = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)

	override val configKeyDomain = ConfigKey.Domain("www.baozimh.com")

	override val isMultipleTagsSupported = false

	private val tagsMap = SuspendLazy(::parseTags)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {

		when (filter) {
			is MangaListFilter.Search -> {
				if (page > 1) return emptyList()
				val url = buildString {
					append("https://")
					append(domain)
					append("/search?q=")
					append(filter.query.urlEncoded())
				}
				return parseMangaListSearch(webClient.httpGet(url).parseHtml())
			}

			is MangaListFilter.Advanced -> {
				val url = buildString {
					append("https://")
					append(domain)
					append("/api/bzmhq/amp_comic_list?filter=*&region=all")

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("&type=")
							append(it.key)
						}
					} else {
						append("&type=all")
					}

					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append("&state=")
							append(
								when (it) {
									MangaState.ONGOING -> "serial"
									MangaState.FINISHED -> "pub"
									else -> "all"
								},
							)
						}
					} else {
						append("&state=all")
					}

					append("&limit=36&page=")
					append(page.toString())
				}

				return parseMangaList(webClient.httpGet(url).parseJson().getJSONArray("items"))
			}

			null -> {
				val url = buildString {
					append("https://")
					append(domain)
					append("/api/bzmhq/amp_comic_list?filter=*&region=all&type=all&state=all&limit=36&page=")
					append(page.toString())
				}
				return parseMangaList(webClient.httpGet(url).parseJson().getJSONArray("items"))
			}
		}
	}

	private fun parseMangaList(json: JSONArray): List<Manga> {
		return json.mapJSON { j ->
			val href = "https://$domain/comic/" + j.getString("comic_id")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				coverUrl = "https://static-tw${domain.removePrefix("www")}/cover/" + j.getString("topic_img"),
				title = j.getString("name"),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = j.getString("author"),
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	private fun parseMangaListSearch(doc: Document): List<Manga> {
		return doc.select("div.comics-card").map { div ->
			val href = "https://$domain" + div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				coverUrl = div.selectFirst("amp-img")?.src().orEmpty(),
				title = div.selectFirstOrThrow(".comics-card__title h3").text(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return tagsMap.get().values.toSet()
	}

	private suspend fun parseTags(): Map<String, MangaTag> {
		val tagElements = webClient.httpGet("https://$domain/classify").parseHtml()
			.select("div.nav")[3].select("a.item:not(.active)")
		val tagMap = ArrayMap<String, MangaTag>(tagElements.size)
		for (el in tagElements) {
			val name = el.text()
			if (name.isEmpty()) continue
			tagMap[name] = MangaTag(
				key = el.attr("href").substringAfter("type=").substringBefore("&"),
				title = name,
				source = source,
			)
		}
		return tagMap
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val state = doc.selectFirstOrThrow(".tag-list span.tag").text()
		val tagMap = tagsMap.get()
		val selectTag = doc.select(".tag-list span.tag").drop(1)
		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }
		return manga.copy(
			description = doc.selectFirst(".comics-detail__desc")?.text().orEmpty(),
			state = when (state) {
				"連載中" -> MangaState.ONGOING
				"已完結" -> MangaState.FINISHED
				else -> null
			},
			tags = tags,
			chapters = (doc.requireElementById("chapter-items").select("div.comics-chapters a")
				+ doc.requireElementById("chapters_other_list").select("div.comics-chapters a"))
				.mapChapters { i, a ->
					val url = a.attrAsRelativeUrl("href").toAbsoluteUrl(domain)
					MangaChapter(
						id = generateUid(url),
						name = a.selectFirstOrThrow("span").text(),
						number = i + 1,
						url = url,
						scanlator = null,
						uploadDate = 0,
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val pagesList = doc.requireElementById("__nuxt")
		var chapterLink = doc.select("link[rel=canonical]").attr("href")
		var nextChapterLink = doc.select("a#next-chapter").attr("href")
		var part = 2
		val idSet = HashSet<Long>()
		var pages = pagesList.select("button.pure-button").map { btn ->
			val urlPage = btn.attr("on").substringAfter(": '").substringBefore("?t=")
			val id = generateUid(urlPage)
			idSet.add(id)
			MangaPage(
				id = id,
				url = urlPage,
				preview = null,
				source = source,
			)
		}

		var chapterPart = chapterLink.substringAfterLast("/").substringBefore(".html")
		var nexChapterPart = nextChapterLink.substringAfterLast("/").substringBefore(".html")
		while (nextChapterLink != "" && (nexChapterPart == chapterPart + "_" + part.toString())){
			val doc2 = webClient.httpGet(nextChapterLink).parseHtml()
			val pages2 = doc2.requireElementById("__nuxt").select("button.pure-button").mapNotNull { btn ->
				val urlPage = btn.attr("on").substringAfter(": '").substringBefore("?t=")
				val id = generateUid(urlPage)
				if(!idSet.add(id)){
					 null
				}else{
					MangaPage(
						id = id,
						url = urlPage,
						preview = null,
						source = source,
					)}
			}
			pages = pages+pages2
			part++
			chapterLink = doc2.select("link[rel=canonical]").attr("href")
			nextChapterLink = doc2.select("a#next-chapter").attr("href")
			chapterPart = chapterLink.substringAfterLast("/").substringBefore(".html").substringBeforeLast("_")
			nexChapterPart = nextChapterLink.substringAfterLast("/").substringBefore(".html")
		}
		return pages
	}
}
