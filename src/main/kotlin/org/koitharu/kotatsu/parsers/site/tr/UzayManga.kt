package org.koitharu.kotatsu.parsers.site.custom.tr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.custom.CustomMangaParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("UZAYMANGA", "Uzay Manga", "tr")
internal class UzayManga(context: MangaLoaderContext) : CustomMangaParser(context, MangaParserSource.UZAYMANGA) {

    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl = "https://uzaymanga.com"
    private val cdnUrl = "https://cdn1.uzaymanga.com"

    private val dateFormat = SimpleDateFormat("MMM d ,yyyy", Locale("tr"))

    override suspend fun fetchPopularManga(page: Int): MangaPageResult {
        val document = client.get("$baseUrl/search?page=$page&search=&order=4").asJsoup()
        val mangaElements = document.select("section[aria-label='series area'] .card")
        val mangaList = mangaElements.map { element ->
            Manga(
                title = element.selectFirst("h2")!!.text(),
                url = element.selectFirst("a")!!.absUrl("href").toRelativeUrl(),
                thumbnailUrl = element.selectFirst("img")?.absUrl("src")
            )
        }
        val hasNextPage = document.selectFirst("section[aria-label='navigation'] li:has(a[class~='!text-gray-800']) + li > a") != null
        return MangaPageResult(mangaList, hasNextPage)
    }

    override suspend fun fetchLatestManga(page: Int): MangaPageResult {
        val document = client.get("$baseUrl/search?page=$page&search=&order=3").asJsoup()
        val mangaElements = document.select("section[aria-label='series area'] .card")
        val mangaList = mangaElements.map { element ->
            Manga(
                title = element.selectFirst("h2")!!.text(),
                url = element.selectFirst("a")!!.absUrl("href").toRelativeUrl(),
                thumbnailUrl = element.selectFirst("img")?.absUrl("src")
            )
        }
        val hasNextPage = document.selectFirst("section[aria-label='navigation'] li:has(a[class~='!text-gray-800']) + li > a") != null
        return MangaPageResult(mangaList, hasNextPage)
    }

    override suspend fun fetchSearchManga(page: Int, query: String, filters: List<Filter>): MangaPageResult {
        if (query.startsWith("slug:")) {
            val slug = query.removePrefix("slug:")
            val document = client.get("$baseUrl/manga/$slug").asJsoup()
            return if (document.selectFirst("div.grid h2 + p") != null) {
                val manga = parseMangaDetails(document)
                MangaPageResult(listOf(manga), hasNextPage = false)
            } else {
                MangaPageResult(emptyList(), hasNextPage = false)
            }
        } else {
            val response = client.get("$cdnUrl/series/search/navbar?search=$query")
            val body = response.body.string()
            val results = json.decodeFromString<List<SearchDto>>(body)
            val mangas = results.map {
                Manga(
                    title = it.name,
                    url = "/manga/${it.id}/${it.name.lowercase().replace(" ", "-")}",
                    thumbnailUrl = "$cdnUrl${it.image}"
                )
            }
            return MangaPageResult(mangas, hasNextPage = false)
        }
    }

    override suspend fun fetchMangaDetails(url: String): Manga {
        val document = client.get(fixUrl(url)).asJsoup()
        return parseMangaDetails(document)
    }

    private fun parseMangaDetails(document: Document): Manga {
        val content = document.selectFirst("#content")!!
        val title = content.selectFirst("h1")!!.text()
        val thumbnail = content.selectFirst("img")?.absUrl("src")
        val genres = content.select("a[href^='search?categories']").joinToString(", ") { it.text() }
        val description = content.selectFirst("div.grid h2 + p")?.text()
        val statusText = content.selectFirst("span:contains(Durum) + span")?.text() ?: ""
        val status = when {
            statusText.containsAny("Devam Ediyor", "Birakildi") -> MangaStatus.ONGOING
            statusText.contains("Tamamlandi") -> MangaStatus.COMPLETED
            statusText.contains("Ara Veridi") -> MangaStatus.ON_HIATUS
            else -> MangaStatus.UNKNOWN
        }
        return Manga(
            title = title,
            url = document.location().toRelativeUrl(),
            thumbnailUrl = thumbnail,
            genres = genres,
            description = description,
            status = status
        )
    }

    override suspend fun fetchChapterList(url: String): List<Chapter> {
        val document = client.get(fixUrl(url)).asJsoup()
        return document.select("div.list-episode a").map { element ->
            Chapter(
                name = element.selectFirst("h3")!!.text(),
                url = element.absUrl("href").toRelativeUrl(),
                uploadDate = element.selectFirst("span")?.text()?.parseDate() ?: 0L
            )
        }
    }

    override suspend fun fetchPageList(url: String): List<Page> {
        val document = client.get(fixUrl(url)).asJsoup()
        val scriptContent = document.select("script").map { it.html() }.firstOrNull { PAGE_REGEX.find(it) != null }
            ?: return emptyList()

        return PAGE_REGEX.findAll(scriptContent).mapIndexed { index, match ->
            Page(index, imageUrl = "$cdnUrl/upload/series/${match.groups["path"]!!.value}")
        }.toList()
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else baseUrl + url
    }

    private fun String.parseDate(): Long {
        return try {
            dateFormat.parse(this)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }

    companion object {
        private val PAGE_REGEX = """\\"path\\":\\"(?<path>[^"]+)\\""".toRegex()
    }

    @Serializable
    data class SearchDto(
        val id: Int,
        val name: String,
        val image: String
    )
}
