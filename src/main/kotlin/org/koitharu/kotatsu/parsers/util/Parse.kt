@file:JvmName("ParseUtils")

package org.koitharu.kotatsu.parsers.util

import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.DateFormat

/**
 * Parse [Response] body as html document using Jsoup
 * @see [parseJson]
 * @see [parseJsonArray]
 */
fun Response.parseHtml(): Document = try {
	val body = requireBody()
	val charset = body.contentType()?.charset()?.name()
	Jsoup.parse(body.byteStream(), charset, request.url.toString())
} finally {
	closeQuietly()
}

/**
 * Parse [Response] body as [JSONObject]
 * @see [parseJsonArray]
 * @see [parseHtml]
 */
fun Response.parseJson(): JSONObject = try {
	JSONObject(requireBody().string())
} finally {
	closeQuietly()
}

/**
 * Parse [Response] body as [JSONArray]
 * @see [parseJson]
 * @see [parseHtml]
 */
fun Response.parseJsonArray(): JSONArray = try {
	JSONArray(requireBody().string())
} finally {
	closeQuietly()
}

/**
 * Convert url to relative if it is on [domain]
 * @return an url relative to the [domain] or absolute, if domain is mismatching
 */
fun String.toRelativeUrl(domain: String): String {
	if (isEmpty() || startsWith("/")) {
		return this
	}
	return replace(Regex("^[^/]{2,6}://${Regex.escape(domain)}+/", RegexOption.IGNORE_CASE), "/")
}

/**
 * Convert url to absolute with specified domain
 * @return an absolute url with [domain] if this is relative
 */
fun String.toAbsoluteUrl(domain: String): String = when {
	this.startsWith("//") -> "https:$this"
	this.startsWith('/') -> "https://$domain$this"
	else -> this
}

fun DateFormat.tryParse(str: String?): Long = if (str.isNullOrEmpty()) {
	0L
} else {
	runCatching {
		parse(str)?.time ?: 0L
	}.getOrDefault(0L)
}

private fun Response.requireBody(): ResponseBody = requireNotNull(body) { "Response body is null" }