package org.koitharu.kotatsu.parsers.network.oauth

data class OAuthToken(
	val tokenType: String,
	val expiresAt: Long,
	val accessToken: String,
	val refreshToken: String,
)
