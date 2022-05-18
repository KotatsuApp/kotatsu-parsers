@file:JvmName("JsoupUtils")

package org.koitharu.kotatsu.parsers.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element

val Element.host: String?
	get() {
		val uri = baseUri()
		return if (uri.isEmpty()) {
			null
		} else {
			uri.toHttpUrlOrNull()?.host
		}
	}

fun Element.attrOrNull(attributeKey: String) = attr(attributeKey).takeUnless { it.isEmpty() }

fun Element.attrAsRelativeUrlOrNull(attributeKey: String): String? {
	val attr = attr(attributeKey).trim()
	if (attr.isEmpty()) {
		return null
	}
	if (attr.startsWith("/")) {
		return attr
	}
	val host = baseUri().toHttpUrlOrNull()?.host ?: return null
	return attr.substringAfter(host)
}

fun Element.attrAsRelativeUrl(attributeKey: String): String {
	return requireNotNull(attrAsRelativeUrlOrNull(attributeKey)) {
		"Cannot get relative url for $attributeKey: \"${attr(attributeKey)}\""
	}
}

fun Element.attrAsAbsoluteUrlOrNull(attributeKey: String): String? {
	val attr = attr(attributeKey).trim()
	if (attr.isEmpty()) {
		return null
	}
	return (baseUri().toHttpUrlOrNull()?.newBuilder(attr) ?: return null).toString()
}

fun Element.attrAsAbsoluteUrl(attributeKey: String): String {
	return requireNotNull(attrAsAbsoluteUrlOrNull(attributeKey)) {
		"Cannot get absolute url for $attributeKey: \"${attr(attributeKey)}\""
	}
}

fun Element.styleValueOrNull(property: String): String? {
	val regex = Regex("${Regex.escape(property)}\\s*:\\s*[^;]+")
	val css = attr("style").find(regex) ?: return null
	return css.substringAfter(':').removeSuffix(';').trim()
}