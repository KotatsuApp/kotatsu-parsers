package org.koitharu.kotatsu.parsers.site.pt

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.zip.ZipInputStream

@Broken // Not dead but changed template
@MangaSourceParser("RANDOMSCANS", "LuratoonScan", "pt")
internal class LuratoonScansParser(context: MangaLoaderContext) :
	SinglePageMangaParser(context, MangaParserSource.RANDOMSCANS),
	Interceptor {

	override val configKeyDomain = ConfigKey.Domain("luratoons.com")

	override fun getRequestHeaders(): Headers = Headers.Builder().add("User-Agent", config[userAgentKey]).build()

	override val availableSortOrders = setOf(SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		require(filter.query.isNullOrEmpty()) { ErrorMessages.SEARCH_NOT_SUPPORTED }
		val url = urlBuilder()
		val tag = filter.tags.oneOrThrowIfMany()
		if (tag == null) {
			url.addPathSegment("todas-as-obras")
		} else {
			url.addPathSegment("pesquisar").addQueryParameter("category", tag.key)
		}
		val doc = webClient.httpGet(url.build()).parseHtml()
		return doc.selectFirstOrThrow(".todas__as__obras").select(".comics__all__box").map { div ->
			val a = div.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.text(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = false,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().body()
		val summaryContainer = doc.selectFirstOrThrow(".sumario__container")
		// 1 de Maio de 2024 às 20:15
		val dateFormat = SimpleDateFormat("dd 'de' MMM 'de' YYYY 'às' HH:mm", sourceLocale)
		return manga.copy(
			title = doc.selectFirst("h1.desc__titulo__comic")?.textOrNull() ?: manga.title,
			altTitle = summaryContainer.getElementsContainingOwnText("Alternativo").firstOrNull()
				?.nextElementSibling()?.textOrNull(),
			tags = summaryContainer.getElementsByAttributeValueContaining("href", "?category=").mapToSet {
				MangaTag(
					title = it.text().toTitleCase(sourceLocale),
					key = it.attr("href").substringAfterLast('='),
					source = source,
				)
			},
			state = when (summaryContainer.getElementsContainingOwnText("Status").firstOrNull()
				?.nextElementSibling()?.text()?.lowercase()) {
				"em lançamento" -> MangaState.ONGOING
				"hiato" -> MangaState.PAUSED
				"finalizado" -> MangaState.FINISHED
				else -> null
			},
			author = summaryContainer.getElementsContainingOwnText("Autor(es)").firstOrNull()
				?.nextElementSibling()?.textOrNull(),
			largeCoverUrl = doc.selectFirst("img.sumario__img")?.attrAsAbsoluteUrlOrNull("src"),
			description = summaryContainer.selectFirst(".sumario__sinopse__texto")?.html(),
			chapters = doc.selectFirstOrThrow("ul.capitulos__lista")
				.select("li")
				.mapChapters(reversed = true) { _, li ->
					val href = li.parent()?.attrAsRelativeUrlOrNull("href") ?: return@mapChapters null
					val span = li.selectFirstOrThrow(".numero__capitulo")
					MangaChapter(
						id = generateUid(href),
						name = span.text(),
						number = 0.0f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(span.nextElementSibling()?.text()),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val regex = Regex("const\\s+urls\\s*=\\s*(\\[.*])")
		val urls = doc.select("script").firstNotNullOf {
			regex.find(it.data())?.groupValues?.getOrNull(1)
		}
		val ja = JSONArray(urls)
		return (0 until ja.length()).map { i ->
			val url = ja.getString(i)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		if (response.mimeType == "application/octet-stream") {
			val (bytes, name) = response.use { resp ->
				ZipInputStream(resp.requireBody().byteStream()).use {
					val entry = it.nextEntry
					it.readBytes() to entry?.name
				}
			}
			val type = if (name?.endsWith(".avif", ignoreCase = true) == true) {
				"image/avif"
			} else {
				"image/*"
			}.toMediaTypeOrNull()
			return response.newBuilder()
				.setHeader("Content-Type", type?.toString())
				.body(bytes.toResponseBody(type))
				.build()
		} else {
			return response
		}
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}
}
