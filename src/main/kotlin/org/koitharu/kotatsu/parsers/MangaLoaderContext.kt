package org.koitharu.kotatsu.parsers

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.HttpStatusException
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.exception.GraphQLException
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.parseJson
import java.util.*

abstract class MangaLoaderContext {

	protected abstract val httpClient: OkHttpClient

	abstract val cookieJar: CookieJar

	suspend fun httpGet(url: String, headers: Headers? = null): Response {
		val request = Request.Builder()
			.get()
			.url(url)
		if (headers != null) {
			request.headers(headers)
		}
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	suspend fun httpHead(url: String, headers: Headers? = null): Response {
		val request = Request.Builder()
			.head()
			.url(url)
		if (headers != null) {
			request.headers(headers)
		}
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	suspend fun httpPost(
		url: String,
		form: Map<String, String>,
		headers: Headers? = null,
	): Response {
		val body = FormBody.Builder()
		form.forEach { (k, v) ->
			body.addEncoded(k, v)
		}
		val request = Request.Builder()
			.post(body.build())
			.url(url)
		if (headers != null) {
			request.headers(headers)
		}
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	suspend fun httpPost(
		url: String,
		payload: String,
		headers: Headers?,
	): Response {
		val body = FormBody.Builder()
		payload.split('&').forEach {
			val pos = it.indexOf('=')
			if (pos != -1) {
				val k = it.substring(0, pos)
				val v = it.substring(pos + 1)
				body.addEncoded(k, v)
			}
		}
		val request = Request.Builder()
			.post(body.build())
			.url(url)
		if (headers != null) {
			request.headers(headers)
		}
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	suspend fun graphQLQuery(endpoint: String, query: String): JSONObject {
		val body = JSONObject()
		body.put("operationName", null as Any?)
		body.put("variables", JSONObject())
		body.put("query", "{$query}")
		val mediaType = "application/json; charset=utf-8".toMediaType()
		val requestBody = body.toString().toRequestBody(mediaType)
		val request = Request.Builder()
			.post(requestBody)
			.url(endpoint)
		val json = httpClient.newCall(request.build()).await().ensureSuccess().parseJson()
		json.optJSONArray("errors")?.let {
			if (it.length() != 0) {
				throw GraphQLException(it)
			}
		}
		return json
	}

	open fun encodeBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

	open fun decodeBase64(data: String): ByteArray = Base64.getDecoder().decode(data)

	open fun getPreferredLocales(): List<Locale> = listOf(Locale.getDefault())

	abstract suspend fun evaluateJs(script: String): String?

	abstract fun getConfig(source: MangaSource): MangaSourceConfig

	private fun Response.ensureSuccess() = apply {
		val exception: Exception? = when (code) { // Catch some error codes, not all
			in 500..599 -> HttpStatusException(message, code, request.url.toString())
			else -> null
		}
		if (exception != null) {
			runCatching {
				close()
			}.onFailure {
				exception.addSuppressed(it)
			}
			throw exception
		}
	}
}