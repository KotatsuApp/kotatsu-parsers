@file:JvmName("ParseUtils")

package org.koitharu.kotatsu.parsers.util

import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.Elements
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

@Deprecated(
	message = "",
	level = DeprecationLevel.ERROR,
	replaceWith = ReplaceWith("firstNotNullOfOrNull { it.ownText().takeIf(predicate) }"),
)
inline fun Elements.findOwnText(predicate: (String) -> Boolean): String? {
	for (x in this) {
		val ownText = x.ownText()
		if (predicate(ownText)) {
			return ownText
		}
	}
	return null
}

@Deprecated(
	message = "",
	level = DeprecationLevel.ERROR,
	replaceWith = ReplaceWith("firstNotNullOfOrNull { it.text().takeIf(predicate) }"),
)
inline fun Elements.findText(predicate: (String) -> Boolean): String? {
	for (x in this) {
		val text = x.text()
		if (predicate(text)) {
			return text
		}
	}
	return null
}

@Deprecated(
	message = "Use toAbsoluteUrl() instead",
	level = DeprecationLevel.ERROR,
	replaceWith = ReplaceWith("toAbsoluteUrl(node.host)"),
)
fun String.inContextOf(node: Node): String {
	return if (this.isEmpty()) {
		""
	} else {
		StringUtil.resolve(node.baseUri(), this)
	}
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

/**
 * Convert url to absolute with specified domain and subdomain
 * @return an absolute url with [subdomain].[domain] if this is relative
 */
fun String.toAbsoluteUrl(domain: String, subdomain: String): String {
	if (!this.startsWith('/')) return this
	return toAbsoluteUrl(subdomain + "." + domain.removePrefix("www."))
}

@Deprecated(
	message = "",
	level = DeprecationLevel.ERROR,
	replaceWith = ReplaceWith("attrAsRelativeUrl(attributeKey)"),
)
fun Element.relUrl(attributeKey: String): String {
	val attr = attr(attributeKey).trim()
	if (attr.isEmpty()) {
		return ""
	}
	if (attr.startsWith("/")) {
		return attr
	}
	val baseUrl = REGEX_URL_BASE.find(baseUri())?.value ?: return attr
	return attr.removePrefix(baseUrl.dropLast(1))
}

private val REGEX_URL_BASE = Regex("^[^/]{2,6}://[^/]+/", RegexOption.IGNORE_CASE)

@Deprecated(
	message = "",
	level = DeprecationLevel.ERROR,
	replaceWith = ReplaceWith("styleValueOrNull(property)"),
)
fun Element.css(property: String): String? = styleValueOrNull(property)

fun DateFormat.tryParse(str: String?): Long = if (str.isNullOrEmpty()) {
	0L
} else {
	runCatching {
		parse(str)?.time ?: 0L
	}.getOrDefault(0L)
}

private fun Response.requireBody(): ResponseBody = requireNotNull(body) { "Response body is null" }