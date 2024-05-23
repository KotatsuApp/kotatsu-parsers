package org.koitharu.kotatsu.parsers.network.oauth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaParser
import java.net.HttpURLConnection

@InternalParsersApi
object OAuthHelper {

	fun <P> intercept(
		chain: Interceptor.Chain,
		parser: P,
	): Response where P : MangaParser, P : MangaParserOAuthProvider {
		val token = parser.context.getAuthToken(parser.source) ?: return chain.proceed(chain.request())
		val request = chain.request()
		val response = chain.proceed(request.withAccessToken(token))
		if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
			response.close()
			val newToken = runBlocking { parser.refreshToken(token) }
			return chain.proceed(request.withAccessToken(newToken))
		}
		return response
	}

	private fun Request.withAccessToken(token: OAuthToken) = newBuilder()
		.header("Authorization", "${token.tokenType} ${token.accessToken}")
		.build()
}
