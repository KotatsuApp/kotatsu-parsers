package org.koitharu.kotatsu.parsers

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.*

abstract class MangaLoaderContext {

	abstract val httpClient: OkHttpClient

	abstract val cookieJar: CookieJar

	fun newParserInstance(source: MangaParserSource): MangaParser = source.newParser(this)

	open fun encodeBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

	open fun decodeBase64(data: String): ByteArray = Base64.getDecoder().decode(data)

	open fun getPreferredLocales(): List<Locale> = listOf(Locale.getDefault())

	/**
	 * Execute JavaScript code and return result
	 * @param script JavaScript source code
	 * @return execution result as string, may be null
	 */
	abstract suspend fun evaluateJs(script: String): String?

	abstract fun getConfig(source: MangaSource): MangaSourceConfig

	abstract fun getDefaultUserAgent(): String

	/**
	 * Helper function to be used in an interceptor
	 * to descramble images
	 * @param response Image response
	 * @param redraw lambda function to implement descrambling logic
	 */
	abstract fun redrawImageResponse(
		response: Response,
		redraw: (image: Bitmap) -> Bitmap,
	): Response

	/**
	 * create a new empty Bitmap with given dimensions
	 */
	abstract fun createBitmap(
		width: Int,
		height: Int,
	): Bitmap
}
