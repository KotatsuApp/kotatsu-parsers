@file:JvmName("ParseUtils")

package org.koitharu.kotatsu.parsers.util

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.InternalParsersApi
import java.text.DateFormat

/**
 * Parse [HttpResponse] body as html document using Jsoup
 * @see [parseJson]
 * @see [parseJsonArray]
 */
public suspend fun HttpResponse.parseHtml(): Document = withContext(Dispatchers.Default) {
	Jsoup.parse(
		bodyAsChannel().toInputStream(),
		charset()?.name(),
		call.request.url.toString(),
	)
}

/**
 * Parse [HttpResponse] body as [JSONObject]
 * @see [parseJsonArray]
 * @see [parseHtml]
 */
public suspend fun HttpResponse.parseJson(): JSONObject = JSONObject(bodyAsText())

/**
 * Parse [HttpResponse] body as [JSONArray]
 * @see [parseJson]
 * @see [parseHtml]
 */
public suspend fun HttpResponse.parseJsonArray(): JSONArray = JSONArray(bodyAsText())

public suspend fun HttpResponse.parseRaw(): String = bodyAsText()

public suspend fun HttpResponse.parseBytes(): ByteArray = bodyAsBytes()

@InternalParsersApi
public fun DateFormat.tryParse(str: String?): Long = if (str.isNullOrEmpty()) {
//	assert(false) { "Date string is null or empty" }
	0L
} else {
	runCatching {
		parse(str)?.time ?: 0L
	}.onFailure {
		if (javaClass.desiredAssertionStatus()) {
			throw AssertionError("Cannot parse date $str", it)
		}
	}.getOrDefault(0L)
}
