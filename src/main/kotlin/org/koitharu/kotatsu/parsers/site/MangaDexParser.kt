package org.koitharu.kotatsu.parsers.site

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 20
private const val CONTENT_RATING =
	"contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic"
private const val LOCALE_FALLBACK = "en"

@MangaSourceParser("MANGADEX", "MangaDex")
internal class MangaDexParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.MANGADEX) {

	override val configKeyDomain = ConfigKey.Domain("mangadex.org", null)

	override val sortOrders: EnumSet<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
	)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder?,
	): List<Manga> {
		val domain = getDomain()
		val url = buildString {
			append("https://api.")
			append(domain)
			append("/manga?limit=")
			append(PAGE_SIZE)
			append("&offset=")
			append(offset)
			append("&includes[]=cover_art&includes[]=author&includes[]=artist&")
			tags?.forEach { tag ->
				append("includedTags[]=")
				append(tag.key)
				append('&')
			}
			if (!query.isNullOrEmpty()) {
				append("title=")
				append(query.urlEncoded())
				append('&')
			}
			append(CONTENT_RATING)
			append("&order")
			append(
				when (sortOrder) {
					null,
					SortOrder.UPDATED,
					-> "[latestUploadedChapter]=desc"
					SortOrder.ALPHABETICAL -> "[title]=asc"
					SortOrder.NEWEST -> "[createdAt]=desc"
					SortOrder.POPULARITY -> "[followedCount]=desc"
					else -> "[followedCount]=desc"
				},
			)
		}
		val json = context.httpGet(url).parseJson().getJSONArray("data")
		return json.mapJSON { jo ->
			val id = jo.getString("id")
			val attrs = jo.getJSONObject("attributes")
			val relations = jo.getJSONArray("relationships").associateByKey("type")
			val cover = relations["cover_art"]
				?.getJSONObject("attributes")
				?.getString("fileName")
				?.let {
					"https://uploads.$domain/covers/$id/$it"
				}
			Manga(
				id = generateUid(id),
				title = requireNotNull(attrs.getJSONObject("title").selectByLocale()) {
					"Title should not be null"
				},
				altTitle = attrs.optJSONObject("altTitles")?.selectByLocale(),
				url = id,
				publicUrl = "https://$domain/title/$id",
				rating = RATING_UNKNOWN,
				isNsfw = attrs.getStringOrNull("contentRating") == "erotica",
				coverUrl = cover?.plus(".256.jpg").orEmpty(),
				largeCoverUrl = cover,
				description = attrs.optJSONObject("description")?.selectByLocale(),
				tags = attrs.getJSONArray("tags").mapJSONToSet { tag ->
					MangaTag(
						title = tag.getJSONObject("attributes")
							.getJSONObject("name")
							.firstStringValue()
							.toTitleCase(),
						key = tag.getString("id"),
						source = source,
					)
				},
				state = when (jo.getStringOrNull("status")) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					else -> null
				},
				author = (relations["author"] ?: relations["artist"])
					?.getJSONObject("attributes")
					?.getStringOrNull("name"),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val domain = getDomain()
		val attrsDeferred = async {
			context.httpGet(
				"https://api.$domain/manga/${manga.url}?includes[]=artist&includes[]=author&includes[]=cover_art",
			).parseJson().getJSONObject("data").getJSONObject("attributes")
		}
		val feedDeferred = async {
			val url = buildString {
				append("https://api.")
				append(domain)
				append("/manga/")
				append(manga.url)
				append("/feed")
				append("?limit=96&includes[]=scanlation_group&order[volume]=asc&order[chapter]=asc&offset=0&")
				append(CONTENT_RATING)
			}
			context.httpGet(url).parseJson().getJSONArray("data")
		}
		val mangaAttrs = attrsDeferred.await()
		val feed = feedDeferred.await()
		// 2022-01-02T00:27:11+00:00
		val dateFormat = SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss'+00:00'",
			Locale.ROOT,
		)
		manga.copy(
			description = mangaAttrs.getJSONObject("description").selectByLocale()
				?: manga.description,
			chapters = feed.mapJSONNotNull { jo ->
				val id = jo.getString("id")
				val attrs = jo.getJSONObject("attributes")
				if (!attrs.isNull("externalUrl")) {
					return@mapJSONNotNull null
				}
				val locale = Locale.forLanguageTag(attrs.getString("translatedLanguage"))
				val relations = jo.getJSONArray("relationships").associateByKey("type")
				val number = attrs.getIntOrDefault("chapter", 0)
				MangaChapter(
					id = generateUid(id),
					name = attrs.getStringOrNull("title")?.takeUnless(String::isEmpty)
						?: "Chapter #$number",
					number = number,
					url = id,
					scanlator = relations["scanlation_group"]?.getStringOrNull("name"),
					uploadDate = dateFormat.tryParse(attrs.getString("publishAt")),
					branch = locale.getDisplayName(locale).toTitleCase(locale),
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val domain = getDomain()
		val chapterJson = context.httpGet("https://api.$domain/at-home/server/${chapter.url}?forcePort443=false")
			.parseJson()
			.getJSONObject("chapter")
		val pages = chapterJson.getJSONArray("data")
		val prefix = "https://uploads.$domain/data/${chapterJson.getString("hash")}/"
		val referer = "https://$domain/"
		return List(pages.length()) { i ->
			val url = prefix + pages.getString(i)
			MangaPage(
				id = generateUid(url),
				url = url,
				referer = referer,
				preview = null, // TODO prefix + dataSaver.getString(i),
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val tags = context.httpGet("https://api.${getDomain()}/manga/tag").parseJson()
			.getJSONArray("data")
		return tags.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getJSONObject("attributes").getJSONObject("name").firstStringValue().toTitleCase(),
				key = jo.getString("id"),
				source = source,
			)
		}
	}

	private fun JSONObject.firstStringValue() = values().next() as String

	private fun JSONObject.selectByLocale(): String? {
		val preferredLocales = context.getPreferredLocales()
		for (locale in preferredLocales) {
			getStringOrNull(locale.language)?.let { return it }
			getStringOrNull(locale.toLanguageTag())?.let { return it }
		}
		return getStringOrNull(LOCALE_FALLBACK) ?: values().nextOrNull() as? String
	}
}