package org.koitharu.kotatsu.parsers

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.*

abstract class MangaLoaderContext {

	abstract val httpClient: OkHttpClient

	abstract val cookieJar: CookieJar

	@Suppress("DEPRECATION")
	fun newParserInstance(source: MangaSource): MangaParser = source.newParser(this)

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
}
