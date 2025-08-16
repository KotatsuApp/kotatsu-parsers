package org.koitharu.kotatsu.parsers.site.vi.yurigarden

import androidx.collection.arraySetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.util.*

internal abstract class YuriGardenParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	protected val isR18Enable: Boolean = false
) : PagedMangaParser(context, source, 18) {

	private val availableTags = suspendLazy(initializer = ::fetchTags)

	override val configKeyDomain = ConfigKey.Domain(domain)
	private val apiSuffix = "api.$domain"
	private val cdnSuffix = "db.$domain/storage/v1/object/public/yuri-garden-store"

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("x-app-origin", "https://$domain")
		.add("User-Agent", UserAgents.KOTATSU)
		.build()

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.remove(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
			isSearchWithFiltersSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = availableTags.get(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.ABANDONED,
				MangaState.PAUSED,
				MangaState.UPCOMING,
			),
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(apiSuffix)
			append("/comics")
			append("?page=")
			append(page)
			append("&limit=")
			append(pageSize)
			append("&r18=")
			append(isR18Enable)

			append("&sort=")
			append(when (order) {
				SortOrder.NEWEST -> "newest"
				SortOrder.NEWEST_ASC -> "oldest"
				else -> "newest" // default
			})

			if (!filter.query.isNullOrEmpty()) {
				append("&search=")
				append(filter.query.urlEncoded())
			}

			filter.states.oneOrThrowIfMany()?.let { state ->
				append("&status=")
				append(when (state) {
					MangaState.ONGOING -> "ongoing"
					MangaState.FINISHED -> "completed"
					MangaState.PAUSED -> "hiatus"
					MangaState.ABANDONED -> "cancelled"
					MangaState.UPCOMING -> "oncoming"
					else -> "all"
				})
			}

			append("&full=true")

			if (filter.tags.isNotEmpty()) {
				append("&genre=")
				append(filter.tags.joinToString(separator = ",") { it.key })
			}

			if (!filter.author.isNullOrEmpty()) {
				clear()

				append("https://")
				append(apiSuffix)
				append("/creators/authors/")
				append(
					filter.author.substringAfter("(").substringBefore(")")
				)

				return@buildString // end of buildString
			}
		}

		val json = webClient.httpGet(url).parseJson()
		val data = json.getJSONArray("comics")

		return data.mapJSON { jo ->
			val id = jo.getLong("id")
			val altTitles = setOf(jo.optString("anotherName", null))
				.filterNotNull()
				.toSet()
			val tags = fetchTags().let { allTags ->
				jo.optJSONArray("genres")?.asTypedList<String>()?.mapNotNullToSet { g ->
					allTags.find { x -> x.key == g }
				}
			}.orEmpty()

			Manga(
				id = generateUid(id),
				url = "/comics/$id",
				publicUrl = "https://$domain/comic/$id",
				title = jo.getString("title"),
				altTitles = altTitles,
				coverUrl = jo.getString("thumbnail"),
				largeCoverUrl = jo.getString("thumbnail"),
				authors = emptySet(),
				tags = tags,
				state = when(jo.optString("status")) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					"hiatus" -> MangaState.PAUSED
					"cancelled" -> MangaState.ABANDONED
					"oncoming" -> MangaState.UPCOMING
					else -> null
				},
				description = jo.optString("description").orEmpty(),
				contentRating = if (jo.getBooleanOrDefault("r18", false)) ContentRating.ADULT else ContentRating.SUGGESTIVE,
				source = source,
				rating = RATING_UNKNOWN,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val id = manga.url.substringAfter("/comics/")
		val json = webClient.httpGet("https://$apiSuffix/comics/${id}").parseJson()

		val authors = json.optJSONArray("authors")?.mapJSONToSet { jo ->
			jo.getString("name") + " (${jo.getLong("id")})"
		}.orEmpty()

		val altTitles = setOf(json.getString("anotherName"))
		val description = json.getString("description")
		val team = json.optJSONArray("teams")?.getJSONObject(0)?.getString("name")

		val chaptersDeferred = async {
			webClient.httpGet("https://$apiSuffix/chapters/comic/${id}").parseJsonArray()
		}

		manga.copy(
			altTitles = altTitles,
			authors = authors,
			chapters = chaptersDeferred.await().mapChapters { _, jo ->
				val chapId = jo.getLong("id")
				MangaChapter(
					id = generateUid(chapId),
					title = jo.getString("name"),
					number = jo.getFloatOrDefault("order", 0f),
					volume = 0,
					url = "$chapId",
					scanlator = team,
					uploadDate = jo.getLong("lastUpdated"),
					branch = null,
					source = source,
				)
			},
			description = description,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val json = webClient.httpGet("https://$apiSuffix/chapters/${chapter.url}").parseJson()
		val pages = json.getJSONArray("pages").asTypedList<JSONObject>()

		return pages.mapIndexed { index, page ->
			val rawUrl = page.getString("url")

			if (rawUrl.startsWith("comics")) {
				val key = page.optString("key", null)
				val url = "https://$cdnSuffix/$rawUrl".toHttpUrl().newBuilder().apply {
					if (!key.isNullOrEmpty()) {
						fragment("KEY=$key")
					}
				}

				MangaPage(
					id = generateUid(index.toLong()),
					url = url.build().toString(),
					preview = null,
					source = source,
				)
			} else {
				val url = rawUrl.toHttpUrlOrNull()?.toString() ?: rawUrl
				MangaPage(
					id = generateUid(index.toLong()),
					url = url,
					preview = null,
					source = source,
				)
			}
		}
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		val fragment = response.request.url.fragment ?: return response
		if (!fragment.contains("KEY=")) return response

		return context.redrawImageResponse(response) { bitmap ->
			val url = fragment.substringBefore("KEY=")
			val key = fragment.substringAfter("KEY=")
			kotlinx.coroutines.runBlocking {
				unscrambleImage(url, bitmap, key)
			}
		}
	}

	private suspend fun unscrambleImage(url: String, bitmap: Bitmap, key: String): Bitmap {
		val js = """
			(function(Q0,Q1,Q2){"use strict";const A=(()=>{const L=[49,50,51,52,53,54,55,56,57,65,66,67,68,69,70,71,72,74,75,76,77,78,80,81,82,83,84,85,86,87,88,89,90,97,98,99,100,101,102,103,104,105,106,107,109,110,111,112,113,114,115,116,117,118,119,120,121,122];return L.map(c=>String.fromCharCode(c)).join("")})();const F=(()=>{let f=[1];for(let i=1;i<=10;i++)f[i]=f[i-1]*i;return f})();const _I=(E,P)=>{let n=[...Array(P).keys()],r=[];for(let a=P-1;a>=0;a--){let i=F[a],s=Math.floor(E/i);E%=i;r.push(n.splice(s,1)[0])}return r};const _S=str=>{let t=0;for(let ch of str){let r=A.indexOf(ch);if(r<0)throw Error("Invalid Base58 char");t=t*58+r}return t};const _U=(enc,p)=>{if(!/^H[1-9A-HJ-NP-Za-km-z]+${'$'}/.test(enc))throw Error("Bad Base58");let t=enc.slice(1,-1),n=enc.slice(-1),r=_S(t);if(A[r%58]!==n)throw Error("Checksum mismatch");return _I(r,p)};const _P=(h,p)=>{let n=Math.floor(h/p),r=h%p,a=[];for(let i=0;i<p;i++)a.push(n+(i<r?1:0));return a};const _D=e=>{let t=Array(e.length);e.forEach((v,i)=>t[v]=i);return t};const _X=(K,H,P)=>{let e=_U(K.slice(4),P),s=_D(e),u=_P(H-4*(P-1),P),m=e.map(i=>u[i]),pts=[0];for(let i=0;i<m.length;i++)pts[i+1]=pts[i]+m[i];let f=[];for(let i=0;i<m.length;i++)f.push({y:i?pts[i]+4*i:0,h:m[i]});return s.map(i=>f[i])};return _X(Q0,Q1,Q2)})("$key",${bitmap.height},10);
		""".trimIndent()

		val result = context.evaluateJs(url, js)
			?: throw IOException("Oops! Đã xảy ra lỗi khi biên dịch")
		val arr = JSONArray(result)

		val out = context.createBitmap(bitmap.width, bitmap.height)
		var dy = 0
		for (i in 0 until arr.length()) {
			val o = arr.getJSONObject(i)
			val sy = o.getInt("y")
			val h = o.getInt("h")
			val src = Rect(0, sy, bitmap.width, sy + h)
			val dst = Rect(0, dy, bitmap.width, dy + h)
			out.drawBitmap(bitmap, src, dst)
			dy += h
		}
		return out
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val json = webClient.httpGet("https://$apiSuffix/resources/systems_vi.json").parseJson()
		val genres = json.getJSONObject("genres")
		return genres.keys().asSequence().mapTo(arraySetOf()) { key ->
			val genre = genres.getJSONObject(key)
			MangaTag(
				title = genre.getString("name").toTitleCase(sourceLocale),
				key = genre.getString("slug"),
				source = source,
			)
		}
	}
}
