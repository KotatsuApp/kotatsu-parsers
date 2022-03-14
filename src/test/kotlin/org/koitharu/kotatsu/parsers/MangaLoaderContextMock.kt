package org.koitharu.kotatsu.parsers

import com.koushikdutta.quack.QuackContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.await
import java.util.concurrent.TimeUnit

internal class MangaLoaderContextMock : MangaLoaderContext() {

	private val userAgent = "Kotatsu/%s (Android %s; %s; %s %s; %s)".format(
		/*BuildConfig.VERSION_NAME*/ "3.0",
		/*Build.VERSION.RELEASE*/ "r",
		/*Build.MODEL*/ "",
		/*Build.BRAND*/ "",
		/*Build.DEVICE*/ "",
		/*Locale.getDefault().language*/"en",
	)

	override val cookieJar: CookieJar = InMemoryCookieJar()

	override val httpClient: OkHttpClient = OkHttpClient.Builder()
		.cookieJar(cookieJar)
		.addInterceptor(UserAgentInterceptor(userAgent))
		.connectTimeout(20, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.writeTimeout(20, TimeUnit.SECONDS)
		.build()

	override suspend fun evaluateJs(script: String): String? {
		return QuackContext.create().use {
			it.evaluate(script)?.toString()
		}
	}

	override fun getConfig(source: MangaSource): MangaSourceConfig {
		return SourceConfigMock()
	}

	suspend fun doRequest(url: String, builder: Request.Builder.() -> Unit): Response {
		val request = Request.Builder()
			.get()
			.url(url)
			.apply(builder)
			.build()
		return httpClient.newCall(request).await()
	}
}