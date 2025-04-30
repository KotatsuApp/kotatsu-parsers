package org.koitharu.kotatsu.parsers.site.pt

import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.observer.wrap
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import jdk.nashorn.internal.objects.NativeFunction.call
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacySinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import sun.security.krb5.Confounder.bytes
import java.text.SimpleDateFormat
import java.util.zip.ZipInputStream

@Broken // Not dead but changed template
@MangaSourceParser("RANDOMSCANS", "LuratoonScan", "pt")
internal class LuratoonScansParser(context: MangaLoaderContext) :
	LegacySinglePageMangaParser(context, MangaParserSource.RANDOMSCANS) {

	override val configKeyDomain = ConfigKey.Domain("luratoons.com")

	override fun getRequestHeaders() = headersOf(HttpHeaders.UserAgent, config[userAgentKey])

	override val availableSortOrders = setOf(SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		require(filter.query.isNullOrEmpty()) { ErrorMessages.SEARCH_NOT_SUPPORTED }
		val url = urlBuilder()
		val tag = filter.tags.oneOrThrowIfMany()
		if (tag == null) {
			url.appendPathSegments("todas-as-obras")
		} else {
			url.appendPathSegments("pesquisar")
			url.parameters.append("category", tag.key)
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
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = emptySet(),
				state = null,
				source = source,
				contentRating = null,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().body()
		val summaryContainer = doc.selectFirstOrThrow(".sumario__container")
		// 1 de Maio de 2024 às 20:15
		val dateFormat = SimpleDateFormat("dd 'de' MMM 'de' YYYY 'às' HH:mm", sourceLocale)
		val author = summaryContainer.getElementsContainingOwnText("Autor(es)").firstOrNull()
			?.nextElementSibling()?.textOrNull()
		return manga.copy(
			title = doc.selectFirst("h1.desc__titulo__comic")?.textOrNull() ?: manga.title,
			altTitles = setOfNotNull(
				summaryContainer.getElementsContainingOwnText("Alternativo").firstOrNull()
					?.nextElementSibling()?.textOrNull(),
			),
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
			authors = setOfNotNull(author),
			largeCoverUrl = doc.selectFirst("img.sumario__img")?.attrAsAbsoluteUrlOrNull("src"),
			description = summaryContainer.selectFirst(".sumario__sinopse__texto")?.html(),
			chapters = doc.selectFirstOrThrow("ul.capitulos__lista")
				.select("li")
				.mapChapters(reversed = true) { _, li ->
					val href = li.parent()?.attrAsRelativeUrlOrNull("href") ?: return@mapChapters null
					val span = li.selectFirstOrThrow(".numero__capitulo")
					MangaChapter(
						id = generateUid(href),
						title = span.text(),
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

	override suspend fun intercept(sender: Sender, request: HttpRequestBuilder): HttpClientCall {
		val call = sender.execute(request)
		if (call.response.contentType()?.contentSubtype == "octet-stream") {
			val (bytes, name) = ZipInputStream(call.response.bodyAsChannel().toInputStream()).use {
				val entry = it.nextEntry
				it.readBytes() to entry?.name
			}
			val type = if (name?.endsWith(".avif", ignoreCase = true) == true) {
				"image/avif"
			} else {
				"image/*"
			}
			val headers = call.response.headers.withBuilder {
				set(HttpHeaders.ContentType, type)
			}
			return call.wrap(ByteReadChannel(bytes), headers)
		} else {
			return call
		}
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}
}
