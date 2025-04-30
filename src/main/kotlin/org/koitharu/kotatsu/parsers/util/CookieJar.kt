@file:JvmName("CookieJarUtils")

package org.koitharu.kotatsu.parsers.util

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*

public suspend fun CookiesStorage.insertCookies(domain: String, vararg cookies: String) {
	val url = safeUrlOf(domain) ?: return
	cookies.map {
		parseServerSetCookieHeader(it)
	}.forEach {
		addCookie(url, it)
	}
}

public suspend fun CookiesStorage.insertCookie(domain: String, cookie: Cookie) {
	val url = safeUrlOf(domain) ?: return
	addCookie(url, cookie)
}

public suspend fun CookiesStorage.getCookies(domain: String): List<Cookie> {
	val url = safeUrlOf(domain) ?: return emptyList()
	return get(url)
}

public suspend fun CookiesStorage.copyCookies(oldDomain: String, newDomain: String, names: Array<String>? = null) {
	val url = URLBuilder()
	url.protocol = URLProtocol.HTTPS
	url.host = oldDomain
	var cookies = get(url.build())
	if (names != null) {
		cookies = cookies.filter { c -> c.name in names }
	}
	url.host = newDomain
	for (cookie in cookies) {
		addCookie(url.build(), cookie)
	}
}

private fun safeUrlOf(domain: String): Url? = try {
	URLBuilder().apply {
		host = domain
		protocol = URLProtocol.HTTPS
	}.build()
} catch (_: IllegalArgumentException) {
	null
}
