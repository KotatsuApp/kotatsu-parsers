package org.koitharu.kotatsu.parsers.network.oauth

import okhttp3.Interceptor
import org.koitharu.kotatsu.parsers.InternalParsersApi

interface MangaParserOAuthProvider : Interceptor {

	fun getAuthUrl(redirectUrl: String): String

	suspend fun authorize(code: String): OAuthToken

	@InternalParsersApi
	suspend fun refreshToken(token: OAuthToken): OAuthToken
}
