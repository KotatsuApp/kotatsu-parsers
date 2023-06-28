package org.koitharu.kotatsu.parsers

import com.koushikdutta.quack.QuackContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.await
import java.util.concurrent.TimeUnit

internal object MangaLoaderContextMock : MangaLoaderContext() {

	override val cookieJar = InMemoryCookieJar()

	override val httpClient: OkHttpClient = OkHttpClient.Builder()
		.cookieJar(cookieJar)
		.addInterceptor(CommonHeadersInterceptor())
		.addInterceptor(CloudFlareInterceptor())
		.connectTimeout(20, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.writeTimeout(20, TimeUnit.SECONDS)
		.build()

	init {
		loadTestCookies()
	}

	override suspend fun evaluateJs(script: String): String? {
		return QuackContext.create().use {
			it.evaluate(script)?.toString()
		}
	}

	override fun getConfig(source: MangaSource): MangaSourceConfig {
		return SourceConfigMock()
	}

	suspend fun doRequest(url: String, source: MangaSource?): Response {
		val request = Request.Builder()
			.get()
			.url(url)
		if (source != null) {
			request.tag(MangaSource::class.java, source)
		}
		return httpClient.newCall(request.build()).await()
	}

	private fun loadTestCookies() {
		// https://addons.mozilla.org/ru/firefox/addon/cookies-txt/
		javaClass.getResourceAsStream("/cookies.txt")?.use {
			cookieJar.loadFromStream(it)
		} ?: println("No cookies loaded!")
	}
}
