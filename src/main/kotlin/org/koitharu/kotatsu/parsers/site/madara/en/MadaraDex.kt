package org.koitharu.kotatsu.parsers.site.madara.en

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("MADARADEX", "MadaraDex", "en", ContentType.HENTAI)
internal class MadaraDex(context: MangaLoaderContext) :
    MadaraParser(context, MangaParserSource.MADARADEX, "madaradex.org") {

    override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.remove(userAgentKey)
    }

    override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
        .add("sec-fetch-site", "same-site")
        .build()

    override val listUrl = "title/"
    override val tagPrefix = "genre/"
    override val postReq = true

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val fullUrl = chapter.url.toAbsoluteUrl(domain)
        val doc = webClient.httpGet(fullUrl).parseHtml()
        val root = doc.body().selectFirst(selectBodyPage)
            ?: throw ParseException("No image found, try to log in", fullUrl)
        return root.select(selectPage).flatMap { div ->
            div.selectOrThrow("img").map { img ->
                val fragUrl = img.requireSrc().toRelativeUrl(domain).toHttpUrl().newBuilder()
                    .fragment(F_URL + fullUrl)
                    .build()
                val cleanUrl = fragUrl.newBuilder().fragment(null).build()
                MangaPage(
                    id = generateUid(cleanUrl.toString()),
                    url = fragUrl.toString(),
                    preview = null,
                    source = source,
                )
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val fullUrl = url.fragment?.substringAfter(F_URL)
        return if (!fullUrl.isNullOrEmpty()) {
            val cleanUrl = url.newBuilder().fragment(null).toString()
            val newReq = request.newBuilder()
                .header("sec-fetch-site", "same-site")
                .header("Referer", fullUrl)
                .header("User-Agent", userAgentKey.toString())
                .url(cleanUrl)
                .build()
            chain.proceed(newReq)
        } else {
            super.intercept(chain)
        }
    }

    private companion object {
        const val F_URL = "fullUrl="
    }
}
