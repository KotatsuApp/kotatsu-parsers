@file:JvmName("JsoupUtils")

package org.koitharu.kotatsu.parsers.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.select.QueryParser
import org.jsoup.select.Selector
import org.koitharu.kotatsu.parsers.exception.ParseException

val Element.host: String?
	get() {
		val uri = baseUri()
		return if (uri.isEmpty()) {
			null
		} else {
			uri.toHttpUrlOrNull()?.host
		}
	}

/**
 * Return an attribute value or null if it is missing or empty
 * @see [Element.attr] which returns empty string instead of null
 */
fun Element.attrOrNull(attributeKey: String) = attr(attributeKey).takeUnless { it.isBlank() }?.trim()


/**
 * Return an attribute value or throw an exception if it is missing
 * @see [Element.attr] which returns empty string instead
 */
fun Element.attrOrThrow(attributeKey: String): String = if (hasAttr(attributeKey)) {
	attr(attributeKey)
} else {
	throw ParseException("Attribute \"$attributeKey\" is missing at element \"$this\"", baseUri())
}

/**
 * Return an attribute value as relative url or null if it is missing or empty
 * @see attrAsRelativeUrl
 * @see attrAsAbsoluteUrlOrNull
 * @see attrAsAbsoluteUrl
 */
fun Element.attrAsRelativeUrlOrNull(attributeKey: String): String? {
	val attr = attrOrNull(attributeKey) ?: return null
	if (attr.isEmpty() || attr.startsWith("data:")) {
		return null
	}
	if (attr.startsWith("/")) {
		return attr
	}
	val host = baseUri().toHttpUrlOrNull()?.host ?: return null
	return attr.substringAfter(host)
}

/**
 * Return an attribute value as relative url or throw an exception if it is missing or empty
 * @throws IllegalArgumentException if attribute value is missing or empty
 * @see attrAsRelativeUrlOrNull
 * @see attrAsAbsoluteUrlOrNull
 * @see attrAsAbsoluteUrl
 */
fun Element.attrAsRelativeUrl(attributeKey: String): String {
	return requireNotNull(attrAsRelativeUrlOrNull(attributeKey)) {
		"Cannot get relative url for $attributeKey: \"${attr(attributeKey)}\""
	}
}

/**
 * Return an attribute value as absolute url or null if it is missing or empty
 * @see attrAsAbsoluteUrl
 * @see attrAsRelativeUrl
 * @see attrAsRelativeUrlOrNull
 */
fun Element.attrAsAbsoluteUrlOrNull(attributeKey: String): String? {
	val attr = attrOrNull(attributeKey) ?: return null
	if (attr.isEmpty() || attr.startsWith("data:")) {
		return null
	}
	return (baseUri().toHttpUrlOrNull()?.newBuilder(attr) ?: return null).toString()
}

/**
 * Return an attribute value as absolute url or throw an exception if it is missing or empty
 * @throws IllegalArgumentException if attribute value is missing or empty
 * @see attrAsAbsoluteUrlOrNull
 * @see attrAsRelativeUrl
 * @see attrAsRelativeUrlOrNull
 */
fun Element.attrAsAbsoluteUrl(attributeKey: String): String {
	return requireNotNull(attrAsAbsoluteUrlOrNull(attributeKey)) {
		"Cannot get absolute url for $attributeKey: \"${attr(attributeKey)}\""
	}
}

/**
 * Return css value from `style` attribute or null if it is missing
 */
fun Element.styleValueOrNull(property: String): String? {
	val regex = Regex("${Regex.escape(property)}\\s*:\\s*[^;]+")
	val css = attr("style").find(regex) ?: return null
	return css.substringAfter(':').removeSuffix(';').trim()
}

/**
 * Like a `expectFirst` but with detailed error message
 */
fun Element.selectFirstOrThrow(cssQuery: String): Element {
	return Selector.selectFirst(cssQuery, this) ?: throw ParseException("Cannot find \"$cssQuery\"", baseUri())
}

fun Element.selectOrThrow(cssQuery: String): Elements {
	return Selector.select(cssQuery, this).ifEmpty {
		throw ParseException("Empty result for \"$cssQuery\"", baseUri())
	}
}

fun Element.requireElementById(id: String): Element {
	return getElementById(id) ?: throw ParseException("Cannot find \"#$id\"", baseUri())
}

fun Element.selectLast(cssQuery: String): Element? {
	return select(cssQuery).lastOrNull()
}

fun Element.selectLastOrThrow(cssQuery: String): Element {
	return selectLast(cssQuery) ?: throw ParseException("Cannot find \"$cssQuery\"", baseUri())
}

fun Element.textOrNull(): String? = text().takeUnless { it.isEmpty() }

fun Element.ownTextOrNull(): String? = ownText().takeUnless { it.isEmpty() }

fun Element.selectFirstParent(query: String): Element? {
	val selector = QueryParser.parse(query)
	val parents = parents()
	val root = parents.lastOrNull() ?: return null
	return parents.firstOrNull {
		selector.matches(root, it)
	}
}

/**
 * Return a first non-empty attribute value of [names] or null if it is missing or empty
 */
fun Element.attrOrNull(vararg names: String): String? {
	for (name in names) {
		val value = attr(name)
		if (value.isNotEmpty()) {
			return value.trim()
		}
	}
	return null
}

@JvmOverloads
fun Element.src(names: Array<String> = arrayOf("data-src", "data-cfsrc", "data-original", "data-cdn", "data-sizes", "data-lazy-src", "data-srcset", "original-src", "data-wpfc-original-src", "src")): String? {
	for (name in names) {
		val value = attrAsAbsoluteUrlOrNull(name)
		if (value != null) {
			return value
		}
	}
	return null
}
