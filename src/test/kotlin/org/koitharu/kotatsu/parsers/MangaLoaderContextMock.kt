package org.koitharu.kotatsu.parsers

import com.koushikdutta.quack.QuackContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.requireBody
import org.koitharu.kotatsu.test_util.BitmapTestImpl
import java.awt.image.BufferedImage
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

internal object MangaLoaderContextMock : MangaLoaderContext() {

	override val cookieJar = InMemoryCookieJar()

	override val httpClient: OkHttpClient = OkHttpClient.Builder()
		.cookieJar(cookieJar)
		.permissiveSSL()
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

	override fun getDefaultUserAgent(): String = UserAgents.FIREFOX_MOBILE

	override fun redrawImageResponse(response: Response, redraw: (Bitmap) -> Bitmap): Response {
		val srcImage = response.requireBody().byteStream().use(ImageIO::read)
		checkNotNull(srcImage) { "Cannot decode image" }
		val resImage = (redraw(BitmapTestImpl(srcImage)) as BitmapTestImpl)
		return response.newBuilder()
			.body(resImage.compress("png").toResponseBody("image/png".toMediaTypeOrNull()))
			.build()
	}

	override fun createBitmap(width: Int, height: Int): Bitmap {
		return BitmapTestImpl(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB))
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

	private fun OkHttpClient.Builder.permissiveSSL() = also { builder ->
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
	}

}
