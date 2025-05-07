package org.koitharu.kotatsu.parsers.site.gallery.all

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.gallery.GalleryParser

@MangaSourceParser("KIUTAKU", "Kiutaku", type = ContentType.OTHER)
internal class Kiutaku(context: MangaLoaderContext) :
    GalleryParser(context, MangaParserSource.KIUTAKU, "kiutaku.com") {

    companion object {
        private val mutex = Mutex()
        private var lastImageRequestTime = 0L
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (IMAGE_EXTENSIONS.any { url.endsWith(it, ignoreCase = true) }) {
            runBlocking {
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    val wait = 500L - (now - lastImageRequestTime)
                    if (wait > 0) {
                        delay(wait)
                    }
                    lastImageRequestTime = System.currentTimeMillis()
                }
            }
        }

        return chain.proceed(request)
    }
}
