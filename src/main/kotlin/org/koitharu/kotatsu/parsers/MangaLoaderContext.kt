package org.koitharu.kotatsu.parsers

import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.LinkResolver
import java.util.*

public abstract class MangaLoaderContext {

	public abstract val httpClient: OkHttpClient

	public abstract val cookieJar: CookieJar

	public fun newParserInstance(source: MangaParserSource): MangaParser = source.newParser(this)

	public fun newLinkResolver(link: HttpUrl): LinkResolver = LinkResolver(this, link)

	public fun newLinkResolver(link: String): LinkResolver = newLinkResolver(link.toHttpUrl())

	public open fun encodeBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

	public open fun decodeBase64(data: String): ByteArray = Base64.getDecoder().decode(data)

	public open fun getPreferredLocales(): List<Locale> = listOf(Locale.getDefault())

	/**
	 * Execute JavaScript code and return result
	 * @param script JavaScript source code
	 * @return execution result as string, may be null
	 */
	public abstract suspend fun evaluateJs(script: String): String?

	public abstract fun getConfig(source: MangaSource): MangaSourceConfig

	public abstract fun getDefaultUserAgent(): String

	/**
	 * Helper function to be used in an interceptor
	 * to descramble images
	 * @param response Image response
	 * @param redraw lambda function to implement descrambling logic
	 */
	public abstract fun redrawImageResponse(
		response: Response,
		redraw: (image: Bitmap) -> Bitmap,
	): Response

	/**
	 * create a new empty Bitmap with given dimensions
	 */
	public abstract fun createBitmap(
		width: Int,
		height: Int,
	): Bitmap
}
