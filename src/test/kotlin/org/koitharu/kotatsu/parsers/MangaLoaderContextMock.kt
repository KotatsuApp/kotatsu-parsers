package org.koitharu.kotatsu.parsers

import com.koushikdutta.quack.QuackContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.MangaSourceAttributeKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.insertCookie
import org.koitharu.kotatsu.parsers.util.interceptor
import org.koitharu.kotatsu.parsers.util.splitByWhitespace
import org.koitharu.kotatsu.test_util.BitmapTestImpl
import org.koitharu.kotatsu.test_util.component6
import org.koitharu.kotatsu.test_util.component7
import java.awt.image.BufferedImage
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

internal object MangaLoaderContextMock : MangaLoaderContext() {

	override val cookiesStorage = AcceptAllCookiesStorage()

	override val httpClient = HttpClient(Java) {
		install(HttpCookies) {
			storage = cookiesStorage
		}
	}

	init {
		httpClient.interceptor(CommonHeadersInterceptor())
		httpClient.interceptor(CloudFlareInterceptor())
	}
//		.permissiveSSL()
//		.connectTimeout(20, TimeUnit.SECONDS)
//		.readTimeout(60, TimeUnit.SECONDS)
//		.writeTimeout(20, TimeUnit.SECONDS)
//		.build()

	init {
		runBlocking {
			loadTestCookies()
		}
	}

	override suspend fun evaluateJs(script: String): String? {
		return QuackContext.create().use {
			it.evaluate(script)?.toString()
		}
	}

	override fun getConfig(source: MangaSource): MangaSourceConfig {
		return SourceConfigMock()
	}

	override fun getDefaultUserAgent(): String = UserAgents.FIREFOX_MOBILE

	override suspend fun redrawImageResponse(call: HttpClientCall, redraw: (Bitmap) -> Bitmap): HttpClientCall {
		val srcImage = call.response.bodyAsChannel().toInputStream().use(ImageIO::read)
		checkNotNull(srcImage) { "Cannot decode image" }
		val resImage = (redraw(BitmapTestImpl(srcImage)) as BitmapTestImpl)
		val headers = HeadersBuilder()
		headers.appendAll(call.response.headers)
		headers.append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
		return call.wrap(ByteReadChannel(resImage.compress("png")), headers.build())

	}

	override fun createBitmap(width: Int, height: Int): Bitmap {
		return BitmapTestImpl(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB))
	}

	suspend fun doRequest(url: String, source: MangaSource?): HttpResponse {
		return httpClient.get {
			url(url)
			if (source != null) {
				attributes.put(MangaSourceAttributeKey, source)
			}
		}
	}

	private suspend fun loadTestCookies() {
		// https://addons.mozilla.org/ru/firefox/addon/cookies-txt/
		javaClass.getResourceAsStream("/cookies.txt")?.use {
			cookiesStorage.loadFromStream(it)
		} ?: println("No cookies loaded!")
	}

	/*private fun OkHttpClient.Builder.permissiveSSL() = also { builder ->
		runCatching {
			val trustAllCerts = object : X509TrustManager {
				override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

				override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

				override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
			}
			val sslContext = SSLContext.getInstance("SSL")
			sslContext.init(null, arrayOf(trustAllCerts), SecureRandom())
			val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
			builder.sslSocketFactory(sslSocketFactory, trustAllCerts)
			builder.hostnameVerifier { _, _ -> true }
		}.onFailure {
			it.printStackTrace()
		}
	}*/

	private suspend fun CookiesStorage.loadFromStream(stream: InputStream) {
		val reader = stream.bufferedReader()
		for (line in reader.lineSequence()) {
			if (line.isBlank() || line.startsWith("# ")) {
				continue
			}
			val (host, _, path, secure, expire, name, value) = line.splitByWhitespace()
			val domain = host.removePrefix("#HttpOnly_").trimStart('.')
			val httpOnly = host.startsWith("#HttpOnly_")
			val cookie = Cookie(
				name = name,
				value = value,
				expires = GMTDate(TimeUnit.SECONDS.toMillis(expire.toLong())),
				domain = domain,
				path = path,
				secure = secure.lowercase(Locale.ROOT).toBooleanStrict(),
				httpOnly = httpOnly,
			)
			insertCookie(domain, cookie)
		}
	}
}
