package org.koitharu.kotatsu.parsers.network

import io.ktor.client.statement.*
import io.ktor.http.*
import org.json.JSONObject

public interface WebClient {

	/**
	 * Do a GET http request to specific url
	 * @param url
	 */
	public suspend fun httpGet(url: String): HttpResponse = httpGet(Url(url))

	public suspend fun httpGet(url: String, extraHeaders: Headers?): HttpResponse = httpGet(Url(url), extraHeaders)

	/**
	 * Do a GET http request to specific url
	 * @param url
	 */
	public suspend fun httpGet(url: Url): HttpResponse = httpGet(url, null)

	/**
	 * Do a GET http request to specific url
	 * @param url
	 * @param extraHeaders additional HTTP headers for request
	 */
	public suspend fun httpGet(url: Url, extraHeaders: Headers?): HttpResponse

	/**
	 * Do a HEAD http request to specific url
	 * @param url
	 */
	public suspend fun httpHead(url: String): HttpResponse = httpHead(Url(url))

	/**
	 * Do a HEAD http request to specific url
	 * @param url
	 */
	public suspend fun httpHead(url: Url): HttpResponse

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param form payload as key=>value map
	 */
	public suspend fun httpPost(url: String, form: Map<String, String>): HttpResponse =
		httpPost(Url(url), form, null)

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param form payload as key=>value map
	 */
	public suspend fun httpPost(url: Url, form: Map<String, String>): HttpResponse = httpPost(url, form, null)

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param form payload as key=>value map
	 * @param extraHeaders additional HTTP headers for request
	 */
	public suspend fun httpPost(url: Url, form: Map<String, String>, extraHeaders: Headers?): HttpResponse

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param payload payload as `key=value` string with `&` separator
	 */
	public suspend fun httpPost(url: String, payload: String): HttpResponse = httpPost(Url(url), payload, null)

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param payload payload as `key=value` string with `&` separator
	 */
	public suspend fun httpPost(url: Url, payload: String): HttpResponse = httpPost(url, payload, null)

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param payload payload as `key=value` string with `&` separator
	 * @param extraHeaders additional HTTP headers for request
	 */
	public suspend fun httpPost(url: Url, payload: String, extraHeaders: Headers?): HttpResponse

	/**
	 * Do a POST http request to specific url with json payload
	 * @param url
	 * @param body
	 */
	public suspend fun httpPost(url: String, body: JSONObject): HttpResponse = httpPost(Url(url), body, null)

	/**
	 * Do a POST http request to specific url with json payload
	 * @param url
	 * @param body
	 */
	public suspend fun httpPost(url: Url, body: JSONObject): HttpResponse = httpPost(url, body, null)

	/**
	 * Do a POST http request to specific url with json payload
	 * @param url
	 * @param body
	 * @param extraHeaders additional HTTP headers for request
	 */
	public suspend fun httpPost(url: Url, body: JSONObject, extraHeaders: Headers?): HttpResponse

	/**
	 * Do a GraphQL request to specific url
	 * @param endpoint an url
	 * @param query GraphQL request payload
	 */
	public suspend fun graphQLQuery(endpoint: String, query: String): JSONObject
}
