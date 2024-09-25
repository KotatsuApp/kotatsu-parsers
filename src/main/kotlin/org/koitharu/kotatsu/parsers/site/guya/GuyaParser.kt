package org.koitharu.kotatsu.parsers.site.guya

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class GuyaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
) : SinglePageMangaParser(context, source) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/api/get_all_series/")
		}
		return if (!filter.query.isNullOrEmpty()) {
			parseMangaList(webClient.httpGet(url).parseJson(), filter.query)
		} else {
			parseMangaList(webClient.httpGet(url).parseJson(), "")
		}
	}

	protected open fun parseMangaList(json: JSONObject, query: String): List<Manga> {
		val manga = ArrayList<Manga>(json.length())
		val keys: Iterator<String> = json.keys()
		while (keys.hasNext()) {
			val key = keys.next()
			if (json.get(key) is JSONObject) {
				if (query.isNotEmpty()) {
					if (key.lowercase().contains(query.lowercase())) manga.add(addManga(json.getJSONObject(key), key))
				} else manga.add(addManga(json.getJSONObject(key), key))
			}
		}
		return manga
	}

	private fun addManga(j: JSONObject, name: String): Manga {
		val url = "https://$domain/read/manga/" + j.getString("slug")
		val apiUrl = "https://$domain/api/series/" + j.getString("slug")
		return Manga(
			id = generateUid(apiUrl),
			url = apiUrl,
			publicUrl = url,
			title = name,
			coverUrl = j.getString("cover").toAbsoluteUrl(domain),
			altTitle = null,
			rating = RATING_UNKNOWN,
			tags = emptySet(),
			description = j.getString("description"),
			state = null,
			author = j.getString("author"),
			isNsfw = isNsfwSource,
			source = source,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseJson().getJSONObject("chapters")
		val slug = manga.url.removeSuffix('/').substringAfterLast('/')
		val keys: Iterator<String> = json.keys()
		val chapters = ArrayList<MangaChapter>()
		var i = 0
		while (keys.hasNext()) {
			val key = keys.next()
			++i
			val chapter = json.getJSONObject(key)
			val url = "https://$domain/api/series/$slug/$key"
			chapters.add(
				MangaChapter(
					id = generateUid(url),
					name = chapter.getString("title"),
					number = i.toFloat(),
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				),
			)
		}
		return manga.copy(chapters = chapters)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val key = chapter.url.substringAfterLast('/')
		val url = chapter.url.substringBeforeLast('/')
		val slug = url.substringAfterLast('/')
		val chapterPages = webClient.httpGet(url.toAbsoluteUrl(domain)).parseJson()
			.getJSONObject("chapters").getJSONObject(key)
		val images = chapterPages.getJSONObject("groups")
		val folder = chapterPages.getString("folder")
		val keysPages: Iterator<String> = images.keys()
		val firstKey = keysPages.next()
		val jsonPages = images.getJSONArray(firstKey)
		val pages = ArrayList<MangaPage>(jsonPages.length())
		for (i in 0 until jsonPages.length()) {
			val urlPage = "https://$domain/media/manga/$slug/chapters/$folder/$firstKey/" + jsonPages.getString(i)
			pages.add(
				MangaPage(
					id = generateUid(urlPage),
					url = urlPage,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
