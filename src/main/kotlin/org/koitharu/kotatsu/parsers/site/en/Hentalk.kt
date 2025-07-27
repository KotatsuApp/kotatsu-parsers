package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.ArraySet
import androidx.collection.MutableIntList
import androidx.collection.MutableIntObjectMap
import org.json.JSONObject
import org.jsoup.HttpStatusException
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.*

private const val SERVER_DATA_SAVER = "?type="
private const val SERVER_DATA = ""

@MangaSourceParser("HENTALK", "Hentalk", "en", type = ContentType.HENTAI)
internal class Hentalk(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.HENTALK, 24) {

	override val configKeyDomain = ConfigKey.Domain("hentalk.pw", "fakku.cc")
	override val userAgentKey = ConfigKey.UserAgent(UserAgents.KOTATSU)

	private val preferredServerKey = ConfigKey.PreferredImageServer(
		presetValues = mapOf(
			SERVER_DATA to "Original quality",
			SERVER_DATA_SAVER to "Compressed quality",
		),
		defaultValue = SERVER_DATA,
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
		keys.add(preferredServerKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isSearchWithFiltersSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions() // not found any URLs for it

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/__data.json?x-sveltekit-trailing-slash=1&x-sveltekit-invalidated=001")

			when {
				!filter.query.isNullOrEmpty() || filter.tags.isNotEmpty() || !filter.author.isNullOrEmpty() -> {
					append("&q=")

					if (!filter.author.isNullOrEmpty()) {
						append("artist:\"${space2plus(filter.author)}\"")
						append('+')
					}

					if (filter.tags.isNotEmpty()) {
						filter.tags.forEach { tag ->
							append("tag:\"${space2plus(tag.key)}\"")
							append('+')
						}
					}

					if (!filter.query.isNullOrEmpty()) {
						append(space2plus(filter.query))
					} else {
						append('+')
					}
				}
			}

			when (order) {
				SortOrder.UPDATED -> append("&sort=released_at")
				SortOrder.NEWEST_ASC -> append("&sort=created_at&order=asc")
				SortOrder.NEWEST -> append("&sort=created_at&order=desc")
				SortOrder.ALPHABETICAL -> append("&sort=title&order=asc")
				SortOrder.ALPHABETICAL_DESC -> append("&sort=title&order=desc")
				else -> {}
			}

			if (page > 1) {
				append("&page=")
				append(page)
			}
		}

		val json = try {
			webClient.httpGet(url).parseJson()
		} catch (e: HttpStatusException) {
			if (e.statusCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
				return emptyList()
			} else {
				throw ParseException("Can't get data from source!", url)
			}
		}

		val dataArray = json.getJSONArray("nodes")
			.optJSONObject(2)
			?.optJSONArray("data")
			?: return emptyList()

		val dataValues = MutableIntObjectMap<Any>(dataArray.length())
		for (i in 0 until dataArray.length()) {
			dataValues[i] = dataArray.get(i)
		}

		val archiveH = MutableIntList(dataArray.length())
		for (i in 0 until dataArray.length()) {
			val item = dataArray.opt(i)
			if (item is JSONObject && item.has("id") && item.has("hash") &&
				item.has("title") && item.has("thumbnail") && item.has("tags")
			) {
				archiveH.add(i)
			}
		}

		val mangaList = ArrayList<Manga>()
		archiveH.forEach { tempIndex ->
			val temp = dataArray.getJSONObject(tempIndex)
			val idRef = temp.getInt("id")
			val hashRef = temp.getInt("hash")
			val titleRef = temp.getInt("title")
			val thumbnailRef = temp.getInt("thumbnail")
			val tagsRef = temp.getInt("tags")

			val mangaId = dataArray.getLong(idRef)

			val key = dataArray.getString(hashRef)
			val title = dataArray.getString(titleRef)
			val idThumbnail = dataArray.getInt(thumbnailRef)

			val tagsList = dataArray.optJSONArray(tagsRef)
			val tags = ArraySet<MangaTag>()
			var author: String? = null

			if (tagsList != null) {
				var i = 0
				while (i < tagsList.length()) {
					val tagRefIndex = tagsList.getInt(i)

					if (dataValues.containsKey(tagRefIndex) &&
						dataValues[tagRefIndex] is JSONObject &&
						(dataValues[tagRefIndex] as JSONObject).has("namespace")
					) {

						val nsObj = dataValues[tagRefIndex] as JSONObject
						val nsIndex = nsObj.getInt("namespace")
						val nameIndex = nsObj.getInt("name")

						val nsValue = if (dataValues.containsKey(nsIndex)) dataValues[nsIndex].toString() else null
						val nameValue =
							if (dataValues.containsKey(nameIndex)) dataValues[nameIndex].toString() else null

						if (nsValue == "artist") {
							author = nameValue?.nullIfEmpty()
						} else if (nsValue == "tag" && nameValue != null) {
							tags.add(
								MangaTag(
									key = nameValue,
									title = nameValue,
									source = source,
								),
							)
						}
					}
					i++
				}
			}

			mangaList.add(
				Manga(
					id = generateUid(mangaId),
					url = "/g/$mangaId/__data.json?x-sveltekit-invalidated=001",
					publicUrl = "https://$domain/g/$mangaId",
					title = title,
					altTitles = emptySet(),
					coverUrl = "https://$domain/image/$key/$idThumbnail?type=cover",
					largeCoverUrl = null,
					authors = setOfNotNull(author),
					tags = tags,
					state = null,
					description = null,
					contentRating = ContentRating.ADULT,
					source = source,
					rating = RATING_UNKNOWN,
				),
			)
		}

		return mangaList
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val json = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseJson()
		val mangaId = manga.url.substringAfter("/g/").substringBefore('/')

		val dataArray = json.getJSONArray("nodes")
			.optJSONObject(2)
			?.optJSONArray("data")
			?: return manga

		var createdAt = ""

		for (i in 0 until dataArray.length()) {
			val item = dataArray.opt(i)
			if (item is JSONObject && item.has("createdAt")) {
				val addedAt = item.getInt("createdAt")
				if (dataArray.length() > addedAt) {
					createdAt = dataArray.optString(addedAt, "")
					break
				}
			}
		}

		val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
		val parseTime = dateFormat.parseSafe(createdAt)
		val chapter = MangaChapter(
			id = generateUid("/g/$mangaId/read/1"),
			url = "/g/$mangaId/read/1/__data.json?x-sveltekit-invalidated=011",
			title = "Oneshot", // for all, just has 1 chapter
			number = 0f,
			uploadDate = parseTime,
			volume = 0,
			branch = null,
			scanlator = null,
			source = source,
		)

		return manga.copy(
			chapters = listOf(chapter),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseJson()
		val dataArray = json.getJSONArray("nodes")
			.optJSONObject(2)
			?.optJSONArray("data")
			?: return emptyList()

		var compressID = ""
		for (i in 0 until dataArray.length()) {
			val item = dataArray.opt(i)
			if (item is JSONObject && item.has("hash")) {
				if (i < 20) {
					val hashValue = dataArray.getString(item.getInt("hash"))
					if (hashValue.length == 8) {
						compressID = hashValue
						break
					}
				}
			}
		}

		var hashID = ""
		for (i in 0 until dataArray.length()) {
			val item = dataArray.opt(i)
			if (item is JSONObject && item.has("hash") && item.has("id")) {
				val hashIndex = item.getInt("hash")
				hashID = dataArray.getString(hashIndex)
				break
			}
		}

		if (hashID.isEmpty()) {
			for (i in 0 until dataArray.length()) {
				val item = dataArray.opt(i)
				if (item is JSONObject && item.has("gallery")) {
					val galleryIndex = item.getInt("gallery")
					val galleryTemplate = dataArray.optJSONObject(galleryIndex)
					if (galleryTemplate != null && galleryTemplate.has("hash")) {
						val hashIndex = galleryTemplate.getInt("hash")
						hashID = dataArray.getString(hashIndex)
						break
					}
				}
			}
		}

		val imgList = ArrayList<String>(dataArray.length())
		for (i in 0 until dataArray.length()) {
			val item = dataArray.opt(i)
			if (item is JSONObject && item.has("filename")) {
				val filenameIndex = item.getInt("filename")
				if (dataArray.length() > filenameIndex) {
					val filename = dataArray.optString(filenameIndex, "")
					if (filename.isNotEmpty()) {
						imgList.add(filename)
					}
				}
			}
		}

		val server = config[preferredServerKey] ?: SERVER_DATA
		return imgList.map { imgEx ->
			val baseUrl = "https://$domain/image/$hashID/$imgEx"
			val imageUrl = when (server) {
				SERVER_DATA -> baseUrl
				SERVER_DATA_SAVER -> baseUrl + SERVER_DATA_SAVER + compressID
				else -> baseUrl
			}

			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	private fun space2plus(input: String): String = input.replace(' ', '+')
}
