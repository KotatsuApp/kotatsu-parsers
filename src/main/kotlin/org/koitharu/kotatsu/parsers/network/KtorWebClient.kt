package org.koitharu.kotatsu.parsers.network

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.json.JSONObject
import org.jsoup.HttpStatusException
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.GraphQLException
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.parseJson
import java.net.HttpURLConnection

public class KtorWebClient(
	private val httpClient: HttpClient,
	private val mangaSource: MangaSource,
) : WebClient {

	override suspend fun httpGet(url: Url, extraHeaders: Headers?): HttpResponse {
		return httpClient.get {
			url(url)
			addTags()
			addExtraHeaders(extraHeaders)
		}.ensureSuccess()
	}

	override suspend fun httpHead(url: Url): HttpResponse {
		return httpClient.head {
			url(url)
			addTags()
		}.ensureSuccess()
	}

	override suspend fun httpPost(url: Url, form: Map<String, String>, extraHeaders: Headers?): HttpResponse {
		val body = ParametersBuilder(form.size)
		form.forEach { k, v -> body.append(k, v) }
		return httpClient.post {
			url(url)
			addTags()
			addExtraHeaders(extraHeaders)
			setBody(FormDataContent(body.build()))
		}.ensureSuccess()
	}

	override suspend fun httpPost(url: Url, payload: String, extraHeaders: Headers?): HttpResponse {
		val body = ParametersBuilder()
		payload.split('&').forEach {
			val pos = it.indexOf('=')
			if (pos != -1) {
				val k = it.substring(0, pos)
				val v = it.substring(pos + 1)
				body.append(k, v)
			}
		}
		return httpClient.post {
			url(url)
			addTags()
			addExtraHeaders(extraHeaders)
			setBody(FormDataContent(body.build()))
		}.ensureSuccess()
	}

	override suspend fun httpPost(url: Url, body: JSONObject, extraHeaders: Headers?): HttpResponse {
		return httpClient.post {
			url(url)
			addTags()
			addExtraHeaders(extraHeaders)
			contentType(ContentType.Application.Json)
			setBody(body.toString())
		}.ensureSuccess()
	}

	override suspend fun graphQLQuery(endpoint: String, query: String): JSONObject {
		val body = JSONObject()
		body.put("operationName", null as Any?)
		body.put("variables", JSONObject())
		body.put("query", "{$query}")

		val json = httpClient.post {
			url(endpoint)
			addTags()
			contentType(ContentType.Application.Json)
			setBody(body.toString())
		}.parseJson()
		json.optJSONArray("errors")?.let {
			if (it.length() != 0) {
				throw GraphQLException(it)
			}
		}
		return json
	}

	private fun HttpRequestBuilder.addTags() = apply {
		attributes.put(MangaSourceAttributeKey, mangaSource)
	}

	private fun HttpRequestBuilder.addExtraHeaders(extraHeaders: Headers?) = apply {
		if (extraHeaders != null) {
			headers.appendAll(extraHeaders)
		}
	}

	private fun HttpResponse.ensureSuccess(): HttpResponse {
		val exception: Exception? = when (status.value) { // Catch some error codes, not all
			HttpURLConnection.HTTP_NOT_FOUND -> NotFoundException(status.description, request.url.toString())
			HttpURLConnection.HTTP_UNAUTHORIZED -> request.attributes.getOrNull(MangaSourceAttributeKey)?.let {
				AuthRequiredException(it)
			} ?: HttpStatusException(status.description, status.value, request.url.toString())

			in 400..599 -> HttpStatusException(status.description, status.value, request.url.toString())
			else -> null
		}
		if (exception != null) {
			throw exception
		}
		return this
	}
}
