package org.koitharu.kotatsu.parsers.site.ru

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacySinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("WAMANGA", "WaManga", "ru", type = ContentType.MANGA)
internal class WaMangaParser(
	context: MangaLoaderContext,
) : LegacySinglePageMangaParser(context, MangaParserSource.WAMANGA) {

	override val configKeyDomain = ConfigKey.Domain("wamanga.me")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = fetchAvailableTags())

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		return parseMangaList(webClient.httpGet("https://$domain/api/comics").parseJson())
	}

	private fun parseMangaList(docs: JSONObject): List<Manga> {
		return docs.getJSONArray("comics").mapJSONNotNull { parseSmallMangaObject(it) }
	}

	private fun parseMangaTag(doc: JSONObject): MangaTag {
		return MangaTag(
			doc.getString("name").toTitleCase(sourceLocale),
			doc.getString("slug"),
			source,
		)
	}

	private fun parseSmallMangaObject(doc: JSONObject): Manga {
		val url = doc.getString("url")
		val author = doc.getStringOrNull("author")
		return Manga(
			id = generateUid(url),
			url = url,
			title = doc.getString("title"),
			altTitles = emptySet(),
			publicUrl = url.toAbsoluteUrl(domain),
			rating = doc.getFloatOrDefault("rating", 0f),
			coverUrl = doc.getString("thumbnail_small"),
			tags = doc.getJSONArray("genres").mapJSONToSet { tag -> parseMangaTag(tag) },
			state = when (doc.getString("status").lowercase(sourceLocale)) {
				"продолжается" -> MangaState.ONGOING
				"окончен" -> MangaState.FINISHED
				"закончен" -> MangaState.FINISHED
				else -> MangaState.UPCOMING
			},
			authors = setOfNotNull(author),
			source = source,
			contentRating = if (doc.getIntOrDefault("adult", 0) == 0) {
				ContentRating.SAFE
			} else {
				ContentRating.ADULT
			},
		)
	}


	override suspend fun getDetails(manga: Manga): Manga {

		val url = "https://$domain/api${manga.url}"
		val doc = webClient.httpGet(url).parseJson().getJSONObject("comic")

		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sourceLocale)
		return manga.copy(
			url = doc.getString("url"),
			title = doc.getString("title"),
			largeCoverUrl = doc.getString("thumbnail"),
			description = doc.getStringOrNull("description") ?: manga.description,
			chapters = doc.getJSONArray("chapters").asTypedList<JSONObject>().mapChapters { _, it ->
				val chapterUrl = it.getString("url")
				MangaChapter(
					id = generateUid(chapterUrl),
					url = chapterUrl,
					source = source,
					number = it.getFloatOrDefault("chapter", 0f),
					volume = it.getIntOrDefault("volume", 0),
					title = it.getStringOrNull("full_title"),
					scanlator = it.getJSONArray("teams").getJSONObject(0)?.getStringOrNull("name"),
					uploadDate = dateFormat.tryParse(it.getStringOrNull("published_on")),
					branch = null,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return webClient.httpGet("https://$domain/api${chapter.url}")
			.parseJson()
			.getJSONObject("chapter")
			.getJSONArray("pages")
			.asTypedList<String>()
			.map { img ->
				MangaPage(
					id = generateUid(img),
					url = img,
					preview = null,
					source = source,
				)
			}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/api/comics").parseJson()
		return doc
			.getJSONArray("comics")
			.mapJSONNotNull { it.getJSONArray("genres").mapJSONToSet { tag -> parseMangaTag(tag) } }
			.flatten()
			.distinctBy { it.key }
			.filter { it.key != "" }
			.toSet()
	}
}
