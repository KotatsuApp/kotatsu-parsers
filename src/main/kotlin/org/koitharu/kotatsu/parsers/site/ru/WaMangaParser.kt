package org.koitharu.kotatsu.parsers.site.ru

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("WAMANGA", "WaManga", "ru", type = ContentType.MANGA)
internal class WaMangaParser(
	context: MangaLoaderContext,
) : SinglePageMangaParser(context, MangaParserSource.WAMANGA) {

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
			doc.getString("name")
				.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
			doc.getString("slug"),
			MangaParserSource.WAMANGA,
		)
	}

	private fun parseSmallMangaObject(doc: JSONObject): Manga {
		val url = doc.getString("url")
		return Manga(
			id = generateUid(url),
			url = url,
			title = doc.getString("title"),
			altTitle = null,
			publicUrl = "https://$domain$url",
			rating = doc.getFloatOrDefault("rating", 0f),
			coverUrl = doc.getString("thumbnail_small"),
			tags = doc.getJSONArray("genres").mapJSONToSet { tag -> parseMangaTag(tag) },
			state = when (doc.getString("status").lowercase(Locale.getDefault())) {
				"продолжается" -> MangaState.ONGOING
				"окончен" -> MangaState.FINISHED
				"закончен" -> MangaState.FINISHED
				else -> MangaState.UPCOMING
			},
			author = doc.getStringOrNull("author"),
			source = source,
			contentRating = if (doc.getIntOrDefault("adult", 0) == 0) ContentRating.SAFE else ContentRating.ADULT,
		)
	}


	override suspend fun getDetails(manga: Manga): Manga {

		val url = "https://$domain/api${manga.url}"
		val doc = webClient.httpGet(url).parseJson().getJSONObject("comic")

		val thumbnailUrl = doc.getString("thumbnail")
		val mangaNamePath =
			thumbnailUrl.slice(thumbnailUrl.indexOf(doc.getString("slug"))..<thumbnailUrl.length).split('/')[0]

		val mangaFolderUrl = "https://$domain/public/storage/comics/$mangaNamePath"
		val chapters = webClient
			.httpGet(mangaFolderUrl)
			.parseHtml()
			.getElementsByTag("a")
			.map { it.ownText() }

		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sourceLocale)
		return manga.copy(
			id = generateUid(manga.url),
			url = doc.getString("url"),
			title = doc.getString("title"),
			largeCoverUrl = thumbnailUrl,
			description = doc.getString("description") ?: manga.description,
			chapters = doc.getJSONArray("chapters").mapJSONNotNull {
				val chapterPrefix = it.getString("slug_lang_vol_ch_sub")
				val chapterName = chapters.first { chap -> chap.startsWith(chapterPrefix) }
				val chapterUrl = "$mangaFolderUrl/$chapterName"
				MangaChapter(
					id = generateUid(chapterUrl),
					url = chapterUrl,
					source = source,
					number = it.getIntOrDefault("chapter", 0).toFloat(),
					volume = it.getIntOrDefault("volume", 0),
					name = it.getStringOrNull("full_title") ?: manga.title,
					scanlator = it.getJSONArray("teams").optJSONObject(0, null)?.getStringOrNull("name"),
					uploadDate = dateFormat.tryParse(it.getStringOrNull("published_on")),
					branch = null,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url).parseHtml()
		val images = doc.getElementsByTag("a").map { it.ownText() }

		return images
			.drop(1) // drop first, because of first is "Parent directory" link
			.map { img ->
			val imageUrl = "${chapter.url}$img"
			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}.toList()
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
