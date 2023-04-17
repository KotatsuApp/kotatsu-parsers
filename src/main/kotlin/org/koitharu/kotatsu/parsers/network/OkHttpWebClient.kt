package org.koitharu.kotatsu.parsers.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.HttpStatusException
import org.koitharu.kotatsu.parsers.exception.GraphQLException
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.parseJson

class OkHttpWebClient(
	private val httpClient: OkHttpClient,
	private val mangaSource: MangaSource,
) : WebClient {

	override suspend fun httpGet(url: HttpUrl): Response {
		val request = Request.Builder()
			.get()
			.url(url)
			.addTags()
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpHead(url: HttpUrl): Response {
		val request = Request.Builder()
			.head()
			.url(url)
			.addTags()
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, form: Map<String, String>): Response {
		val body = FormBody.Builder()
		form.forEach { (k, v) ->
			body.addEncoded(k, v)
		}
		val request = Request.Builder()
			.post(body.build())
			.url(url)
			.addTags()
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, payload: String): Response {
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
			.addTags()
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, body: JSONObject): Response {
		val mediaType = "application/json; charset=utf-8".toMediaType()
		val requestBody = body.toString().toRequestBody(mediaType)
		val request = Request.Builder()
			.post(requestBody)
			.url(url)
			.addTags()
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun graphQLQuery(endpoint: String, query: String): JSONObject {
		val body = JSONObject()
		body.put("operationName", null as Any?)
		body.put("variables", JSONObject())
		body.put("query", "{$query}")
		val json = httpPost(endpoint, body).parseJson()
		json.optJSONArray("errors")?.let {
			if (it.length() != 0) {
				throw GraphQLException(it)
			}
		}
		return json
	}

	private fun Request.Builder.addTags(): Request.Builder {
		tag(MangaSource::class.java, mangaSource)
		return this
	}

	private fun Response.ensureSuccess(): Response {
		val exception: Exception? = when (code) { // Catch some error codes, not all
			404 -> NotFoundException(message, request.url.toString())
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
		return this
	}
}
