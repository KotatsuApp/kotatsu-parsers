package org.koitharu.kotatsu.parsers.site.madara.en

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireSrc
import org.koitharu.kotatsu.parsers.util.selectOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl

@MangaSourceParser("MADARADEX", "MadaraDex", "en", ContentType.HENTAI)
internal class MadaraDex(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MADARADEX, "madaradex.org") {

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
                val url = img.requireSrc().toRelativeUrl(domain).toHttpUrl().newBuilder()
                    url.fragment(F_URL + fullUrl)
                MangaPage(
                    id = generateUid(url.toString()),
                    url = url.build().toString(),
                    preview = null,
                    source = source,
                )
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val fragment = response.request.url.fragment

        if (fragment == null || !fragment.contains(F_URL)) {
            return response
        }

        val fullUrl = fragment.substringAfter(F_URL)
        val newReq = chain.request().newBuilder()
            .header("Referer", fullUrl)
            .build()

        return super.intercept(object: Interceptor.Chain by chain {
            override fun request() = newReq
        })
    }

    private companion object {
        const val F_URL = "fullUrl="
    }
}
