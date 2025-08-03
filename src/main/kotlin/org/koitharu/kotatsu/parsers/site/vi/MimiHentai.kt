package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MIMIHENTAI", "MimiHentai", "vi", type = ContentType.HENTAI)
internal class MimiHentai(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MIMIHENTAI, 18) {

	private val apiSuffix = "api/v1/manga"
	override val configKeyDomain = ConfigKey.Domain("mimihentai.com", "hentaihvn.com")

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon(
					"https://raw.githubusercontent.com/dragonx943/plugin-sdk/refs/heads/sources/mimihentai/app/src/main/ic_launcher-playstore.png",
					512,
					null),
			),
			domain,
		)
	}

	private val preferredServerKey = ConfigKey.PreferredImageServer(
		presetValues = mapOf(
			"original" to "Server ảnh gốc (Original)",
			"600" to "Nén xuống 600x",
			"800" to "Nén xuống 800x",
			"1200" to "Nén xuống 1200x",
			"1600" to "Nén xuống 1600x",
		),
		defaultValue = "original",
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(preferredServerKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        	SortOrder.UPDATED,
        	SortOrder.ALPHABETICAL,
        	SortOrder.POPULARITY,
        	SortOrder.POPULARITY_TODAY,
			SortOrder.POPULARITY_WEEK,
			SortOrder.POPULARITY_MONTH,
        	SortOrder.RATING,
    )

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
			isAuthorSearchSupported = true,
			isTagsExclusionSupported = true,
		)

	init {
		setFirstPage(0)
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(availableTags = fetchTags())

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
    	val url = buildString {
            append("https://")
            append("$domain/$apiSuffix")
                
            if (!filter.query.isNullOrEmpty() || !filter.author.isNullOrEmpty() || filter.tags.isNotEmpty()) {
                append("/advance-search?page=")
                append(page)
                append("&max=18") // page size, avoid rate limit
                                
                when {
                	!filter.query.isNullOrEmpty() -> {
                    	append("&name=")
                        append(filter.query.urlEncoded())
                    }

                    !filter.author.isNullOrEmpty() -> {
                        append("&author=")
                        append(filter.author.urlEncoded())
                    }

                    filter.tags.isNotEmpty() -> {
                        append("&genre=")
                        append(filter.tags.joinToString(",") { it.key })
                    }

			  		filter.tagsExclude.isNotEmpty() -> {
						append("&ex=")
						append(filter.tagsExclude.joinToString(",") { it.key })
			  		}
                }
                                
                append("&sort=")
                append(
                    when (order) {
                        SortOrder.UPDATED -> "updated_at"
                        SortOrder.ALPHABETICAL -> "title"
                        SortOrder.POPULARITY -> "follows"
                        SortOrder.POPULARITY_TODAY,
                        SortOrder.POPULARITY_WEEK,
						SortOrder.POPULARITY_MONTH -> "views"
                        SortOrder.RATING -> "likes"
                        else -> ""
                    }
				)
            }
                        
            else {
                append(
                    when (order) {
                        SortOrder.UPDATED -> "/tatcatruyen?page=$page&sort=updated_at"
                        SortOrder.ALPHABETICAL -> "/tatcatruyen?page=$page&sort=title"
                        SortOrder.POPULARITY -> "/tatcatruyen?page=$page&sort=follows"
                        SortOrder.POPULARITY_TODAY -> "/tatcatruyen?page=$page&sort=views"
                        SortOrder.POPULARITY_WEEK -> "/top-manga?page=$page&timeType=1&limit=18"
						SortOrder.POPULARITY_MONTH -> "/top-manga?page=$page&timeType=2&limit=18"
                        SortOrder.RATING -> "/tatcatruyen?page=$page&sort=likes"
                        else -> "/tatcatruyen?page=$page&sort=updated_at" // default
                    }
                )

				if (filter.tagsExclude.isNotEmpty()) {
					append("&ex=")
					append(filter.tagsExclude.joinToString(",") { it.key })
				}
            }
        }

	    val raw = webClient.httpGet(url)
		return if (url.contains("/top-manga")) {
			val data = raw.parseJsonArray()
			parseTopMangaList(data)
		} else {
			val data = raw.parseJson().getJSONArray("data")
			parseMangaList(data)
		}
	}

	private fun parseTopMangaList(data: JSONArray): List<Manga> {
		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val title = jo.getString("title").takeIf { it.isNotEmpty() } ?: "Web chưa đặt tên"
			val description = jo.getStringOrNull("description")

			val differentNames = mutableSetOf<String>().apply {
				jo.optJSONArray("differentNames")?.let { namesArray ->
					for (i in 0 until namesArray.length()) {
						namesArray.optString(i)?.takeIf { it.isNotEmpty() }?.let { name ->
							add(name)
						}
					}
				}
			}

			val authors = jo.optJSONArray("authors")?.mapJSON { 
				it.getString("name")
			}?.toSet() ?: emptySet()

			val tags = jo.optJSONArray("genres")?.mapJSON { genre ->
				MangaTag(
					key = genre.getLong("id").toString(),
					title = genre.getString("name"),
					source = source
				)
			}?.toSet() ?: emptySet()

			Manga(
				id = generateUid(id),
				title = title,
				altTitles = differentNames,
				url = "/$apiSuffix/info/$id",
				publicUrl = "https://$domain/g/$id",
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = jo.getString("coverUrl"),
				state = null,
				description = description,
				tags = tags,
				authors = authors,
				source = source,
			)
		}
	}

	private fun parseMangaList(data: JSONArray): List<Manga> {
		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val title = jo.getString("title").takeIf { it.isNotEmpty() } ?: "Web chưa đặt tên"
			val description = jo.getStringOrNull("description")

            val differentNames = mutableSetOf<String>().apply {
                jo.optJSONArray("differentNames")?.let { namesArray ->
                    for (i in 0 until namesArray.length()) {
                        namesArray.optString(i)?.takeIf { it.isNotEmpty() }?.let { name ->
                            add(name)
                        }
                    }
                }
            }

            val authors = jo.getJSONArray("authors").mapJSON { 
            	it.getString("name")
            }.toSet()
			
			val tags = jo.getJSONArray("genres").mapJSON { genre ->
				MangaTag(
					key = genre.getLong("id").toString(),
					title = genre.getString("name"),
					source = source
				)
			}.toSet()

			Manga(
				id = generateUid(id),
				title = title,
				altTitles = differentNames,
				url = "/$apiSuffix/info/$id",
				publicUrl = "https://$domain/g/$id",
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = jo.getString("coverUrl"),
				state = null,
				tags = tags,
				description = description,
				authors = authors,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(domain)
		val json = webClient.httpGet(url).parseJson()
        val id = json.getLong("id")
		val description = json.getStringOrNull("description")
		val uploaderName = json.getJSONObject("uploader").getString("displayName")

		val tags = json.getJSONArray("genres").mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name").toTitleCase(sourceLocale),
				key = jo.getLong("id").toString(),
				source = source,
			)
		}

		val urlChaps = "https://$domain/$apiSuffix/gallery/$id"
		val parsedChapters = webClient.httpGet(urlChaps).parseJsonArray()
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)
		val chapters = parsedChapters.mapJSON { jo ->
			MangaChapter(
				id = generateUid(jo.getLong("id")),
				title = jo.getStringOrNull("title"),
				number = jo.getFloatOrDefault("order", 0f),
				url = "/$apiSuffix/chapter?id=${jo.getLong("id")}",
				uploadDate = dateFormat.parse(jo.getString("createdAt"))?.time ?: 0L,
				source = source,
				scanlator = uploaderName,
				branch = null,
				volume = 0,
			)
		}.reversed()

		return manga.copy(
			tags = tags,
			description = description,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseJson()
		val imageUrls = json.getJSONArray("pages").asTypedList<String>()
		val server = config[preferredServerKey] ?: "original"
		return imageUrls.map { url ->
			val finalUrl = when (server) {
				"original" -> url
				else -> {
					val cleanUrl = url.removePrefix("http://").removePrefix("https://")
					"https://i0.wp.com/$cleanUrl?w=$server"
				}
			}
			MangaPage(
				id = generateUid(url),
				url = finalUrl,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val url = "https://$domain/$apiSuffix/genres"
		val response = webClient.httpGet(url).parseJsonArray()
		return response.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name").toTitleCase(sourceLocale),
				key = jo.getLong("id").toString(),
				source = source,
			)
		}
	}
}
