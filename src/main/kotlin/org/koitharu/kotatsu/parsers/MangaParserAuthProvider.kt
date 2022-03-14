package org.koitharu.kotatsu.parsers

interface MangaParserAuthProvider {

	val authUrl: String

	val isAuthorized: Boolean

	suspend fun getUsername(): String
}