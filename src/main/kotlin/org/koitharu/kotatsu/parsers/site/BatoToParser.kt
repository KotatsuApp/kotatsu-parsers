package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArraySet
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@MangaSourceParser("BATOTO", "Bato.To")
internal class BatoToParser(context: MangaLoaderContext) : PagedMangaParser(
    context = context,
    source = MangaSource.BATOTO,
    pageSize = 60,
    searchPageSize = 20,
) {

    override val sortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.NEWEST,
        SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.ALPHABETICAL,
    )

    override val configKeyDomain = ConfigKey.Domain(
        "bato.to",
        arrayOf("bato.to", "mto.to", "mangatoto.com", "battwo.com", "batotwo.com", "comiko.net", "batotoo.com"),
    )

    override suspend fun getListPage(
        page: Int,
        query: String?,
        tags: Set<MangaTag>?,
        sortOrder: SortOrder,
    ): List<Manga> {
        if (!query.isNullOrEmpty()) {
            return search(page, query)
        }
        @Suppress("NON_EXHAUSTIVE_WHEN_STATEMENT")
        val url = buildString {
            append("https://")
            append(domain)
            append("/browse?sort=")
            when (sortOrder) {
                SortOrder.UPDATED,
                -> append("update.za")

                SortOrder.POPULARITY -> append("views_a.za")
                SortOrder.NEWEST -> append("create.za")
                SortOrder.ALPHABETICAL -> append("title.az")
            }
            if (!tags.isNullOrEmpty()) {
                append("&genres=")
                appendAll(tags, ",") { it.key }
            }
            append("&page=")
            append(page)
        }
        return parseList(url, page)
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
            .requireElementById("mainer")
        val details = root.selectFirstOrThrow(".detail-set")
        val attrs = details.selectFirst(".attr-main")?.select(".attr-item")?.associate {
            it.child(0).text().trim() to it.child(1)
        }.orEmpty()
        return manga.copy(
            title = root.selectFirst("h3.item-title")?.text() ?: manga.title,
            isNsfw = !root.selectFirst("alert")?.getElementsContainingOwnText("NSFW").isNullOrEmpty(),
            largeCoverUrl = details.selectFirst("img[src]")?.absUrl("src"),
            description = details.getElementById("limit-height-body-summary")
                ?.selectFirst(".limit-html")
                ?.html(),
            tags = manga.tags + attrs["Genres:"]?.parseTags().orEmpty(),
            state = when (attrs["Release status:"]?.text()) {
                "Ongoing" -> MangaState.ONGOING
                "Completed" -> MangaState.FINISHED
                else -> manga.state
            },
            author = attrs["Authors:"]?.text()?.trim() ?: manga.author,
            chapters = root.selectFirst(".episode-list")
                ?.selectFirst(".main")
                ?.children()
                ?.reversed()
                ?.mapChapters { i, div ->
                    div.parseChapter(i)
                }.orEmpty(),
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain)
        val scripts = webClient.httpGet(fullUrl).parseHtml().select("script")
        for (script in scripts) {
            val scriptSrc = script.html()
            val p = scriptSrc.indexOf("const imgHttpLis =")
            if (p == -1) continue
            val start = scriptSrc.indexOf('[', p)
            val end = scriptSrc.indexOf(';', start)
            if (start == -1 || end == -1) {
                continue
            }
            val images = JSONArray(scriptSrc.substring(start, end))
            val batoPass = scriptSrc.substringBetweenFirst("batoPass =", ";")?.trim(' ', '"', '\n')
                ?: script.parseFailed("Cannot find batoPass")
            val batoWord = scriptSrc.substringBetweenFirst("batoWord =", ";")?.trim(' ', '"', '\n')
                ?: script.parseFailed("Cannot find batoWord")
            val password = context.evaluateJs(batoPass)?.removeSurrounding('"')
                ?: script.parseFailed("Cannot evaluate batoPass")
            val args = JSONArray(decryptAES(batoWord, password))
            val result = ArrayList<MangaPage>(images.length())
            repeat(images.length()) { i ->
                val url = images.getString(i)
                result += MangaPage(
                    id = generateUid(url),
                    url = url + "?" + args.getString(i),
                    referer = fullUrl,
                    preview = null,
                    source = source,
                )
            }
            return result
        }
        throw ParseException("Cannot find images list", fullUrl)
    }

    override suspend fun getTags(): Set<MangaTag> {
        val scripts = webClient.httpGet(
            "https://${domain}/browse",
        ).parseHtml().selectOrThrow("script")
        for (script in scripts) {
            val genres = script.html().substringBetweenFirst("const _genres =", ";") ?: continue
            val jo = JSONObject(genres)
            val result = ArraySet<MangaTag>(jo.length())
            jo.keys().forEach { key ->
                val item = jo.getJSONObject(key)
                result += MangaTag(
                    title = item.getString("text").toTitleCase(),
                    key = item.getString("file"),
                    source = source,
                )
            }
            return result
        }
        throw ParseException("Cannot find gernes list", scripts[0].baseUri())
    }

    override fun getFaviconUrl(): String = "https://styles.amarkcdn.com/img/batoto/favicon.ico?v0"

    private suspend fun search(page: Int, query: String): List<Manga> {
        val url = buildString {
            append("https://")
            append(domain)
            append("/search?word=")
            append(query.replace(' ', '+'))
            append("&page=")
            append(page)
        }
        return parseList(url, page)
    }

    private fun getActivePage(body: Element): Int = body.select("nav ul.pagination > li.page-item.active")
        .lastOrNull()
        ?.text()
        ?.toIntOrNull() ?: body.parseFailed("Cannot determine current page")

    private suspend fun parseList(url: String, page: Int): List<Manga> {
        val body = webClient.httpGet(url).parseHtml().body()
        if (body.selectFirst(".browse-no-matches") != null) {
            return emptyList()
        }
        val activePage = getActivePage(body)
        if (activePage != page) {
            return emptyList()
        }
        val root = body.requireElementById("series-list")
        return root.children().map { div ->
            val a = div.selectFirstOrThrow("a")
            val href = a.attrAsRelativeUrl("href")
            val title = div.selectFirstOrThrow(".item-title").text()
            Manga(
                id = generateUid(href),
                title = title,
                altTitle = div.selectFirst(".item-alias")?.text()?.takeUnless { it == title },
                url = href,
                publicUrl = a.absUrl("href"),
                rating = RATING_UNKNOWN,
                isNsfw = false,
                coverUrl = div.selectFirst("img[src]")?.absUrl("src").orEmpty(),
                largeCoverUrl = null,
                description = null,
                tags = div.selectFirst(".item-genre")?.parseTags().orEmpty(),
                state = null,
                author = null,
                source = source,
            )
        }
    }

    private fun Element.parseTags() = children().mapToSet { span ->
        val text = span.ownText()
        MangaTag(
            title = text.toTitleCase(),
            key = text.lowercase(Locale.ENGLISH).replace(' ', '_'),
            source = source,
        )
    }

    private fun Element.parseChapter(index: Int): MangaChapter? {
        val a = selectFirst("a.chapt") ?: return null
        val extra = selectFirst(".extra")
        val href = a.attrAsRelativeUrl("href")
        return MangaChapter(
            id = generateUid(href),
            name = a.text(),
            number = index + 1,
            url = href,
            scanlator = extra?.getElementsByAttributeValueContaining("href", "/group/")?.text(),
            uploadDate = runCatching {
                parseChapterDate(extra?.select("i")?.lastOrNull()?.ownText())
            }.getOrDefault(0),
            branch = null,
            source = source,
        )
    }

    private fun parseChapterDate(date: String?): Long {
        if (date.isNullOrEmpty()) {
            return 0
        }
        val value = date.substringBefore(' ').toInt()
        val field = when {
            "sec" in date -> Calendar.SECOND
            "min" in date -> Calendar.MINUTE
            "hour" in date -> Calendar.HOUR
            "day" in date -> Calendar.DAY_OF_MONTH
            "week" in date -> Calendar.WEEK_OF_YEAR
            "month" in date -> Calendar.MONTH
            "year" in date -> Calendar.YEAR
            else -> return 0
        }
        val calendar = Calendar.getInstance()
        calendar.add(field, -value)
        return calendar.timeInMillis
    }

    private fun decryptAES(encrypted: String, password: String): String {
        val cipherData = context.decodeBase64(encrypted)
        val saltData = cipherData.copyOfRange(8, 16)
        val (key, iv) = generateKeyAndIV(
            keyLength = 32,
            ivLength = 16,
            iterations = 1,
            salt = saltData,
            password = password.toByteArray(StandardCharsets.UTF_8),
            md = MessageDigest.getInstance("MD5"),
        )
        val encryptedData = cipherData.copyOfRange(16, cipherData.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(encryptedData).toString(Charsets.UTF_8)
    }

    @Suppress("SameParameterValue")
    private fun generateKeyAndIV(
        keyLength: Int,
        ivLength: Int,
        iterations: Int,
        salt: ByteArray,
        password: ByteArray,
        md: MessageDigest,
    ): Pair<SecretKeySpec, IvParameterSpec> {
        val digestLength = md.digestLength
        val requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0
        md.reset()
        while (generatedLength < keyLength + ivLength) {
            if (generatedLength > 0) {
                md.update(generatedData, generatedLength - digestLength, digestLength)
            }
            md.update(password)
            md.update(salt, 0, 8)
            md.digest(generatedData, generatedLength, digestLength)
            repeat(iterations - 1) {
                md.update(generatedData, generatedLength, digestLength)
                md.digest(generatedData, generatedLength, digestLength)
            }
            generatedLength += digestLength
        }

        return SecretKeySpec(generatedData.copyOfRange(0, keyLength), "AES") to IvParameterSpec(
            if (ivLength > 0) {
                generatedData.copyOfRange(keyLength, keyLength + ivLength)
            } else byteArrayOf(),
        )
    }
}
