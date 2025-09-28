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

private const val F_URL = "fullUrl="

@MangaSourceParser("MADARADEX", "MadaraDex", "en", ContentType.HENTAI)
internal class MadaraDex(context: MangaLoaderContext) :
    MadaraParser(context, MangaParserSource.MADARADEX, "madaradex.org") {

    init {
        context.cookieJar.insertCookies(domain, "wpmanga-adault=1")
    }

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.remove(userAgentKey)
    }

    override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
        .set("User-Agent", UserAgents.CHROME_DESKTOP)
        .build()

    override val authUrl: String
        get() = "https://${domain}"

    override suspend fun isAuthorized(): Boolean {
        return context.cookieJar.getCookies(domain).any {
            it.name.contains("cm_uaid")
        }
    }

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
        val fullUrl = url.fragment?.substringAfter(F_URL, "")
        return if (!fullUrl.isNullOrEmpty()) {
            copyCookies()
            val cleanUrl = url.newBuilder().fragment(null).toString()
            val newReq = request.newBuilder()
                .header("Referer", fullUrl)
                .url(cleanUrl)
                .build()
            chain.proceed(newReq)
        } else {
            super.intercept(chain)
        }
    }

    private fun copyCookies() = context.cookieJar.copyCookies(domain, "cdn.$domain")
}
