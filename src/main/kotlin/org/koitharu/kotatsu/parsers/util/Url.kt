@file:JvmName("UrlUtils")

package org.koitharu.kotatsu.parsers.util

import java.net.URI

private val REGEX_SCHEME_PREFIX = Regex("^\\w{2,6}://", RegexOption.IGNORE_CASE)
private const val SCHEME_HTTPS = "https"

public fun resolveLink(
	baseUrl: String,
	link: String,
): String = URI.create(baseUrl).resolve(link).toString()

/**
 * Convert url to relative if it is on [domain]
 * @return an url relative to the [domain] or absolute, if domain is mismatching
 */
public fun String.toRelativeUrl(domain: String): String {
	if (isEmpty() || startsWith("/")) {
		return this
	}
	return replace(Regex("^[^/]{2,6}://${Regex.escape(domain)}+/", RegexOption.IGNORE_CASE), "/")
}

/**
 * Convert url to absolute with specified domain
 * @return an absolute url with [domain] if this is relative
 */
public fun String.toAbsoluteUrl(domain: String): String = when {
	startsWith("//") -> "$SCHEME_HTTPS:$this"
	startsWith('/') -> "$SCHEME_HTTPS://$domain$this"
	REGEX_SCHEME_PREFIX.containsMatchIn(this) -> this
	else -> "$SCHEME_HTTPS://$domain/$this"
}

public fun concatUrl(host: String, path: String): String {
	val hostWithSlash = host.endsWith('/')
	val pathWithSlash = path.startsWith('/')
	val hostWithScheme = if (host.startsWith("//")) "https:$host" else host
	return when {
		hostWithSlash && pathWithSlash -> hostWithScheme + path.drop(1)
		!hostWithSlash && !pathWithSlash -> "$hostWithScheme/$path"
		else -> hostWithScheme + path
	}
}
