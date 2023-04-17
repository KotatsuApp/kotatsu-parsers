package org.koitharu.kotatsu.parsers.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.json.JSONObject

interface WebClient {

	/**
	 * Do a GET http request to specific url
	 * @param url
	 */
	suspend fun httpGet(url: String): Response = httpGet(url.toHttpUrl())

	/**
	 * Do a GET http request to specific url
	 * @param url
	 */
	suspend fun httpGet(url: HttpUrl): Response

	/**
	 * Do a HEAD http request to specific url
	 * @param url
	 */
	suspend fun httpHead(url: String): Response = httpHead(url.toHttpUrl())

	/**
	 * Do a HEAD http request to specific url
	 * @param url
	 */
	suspend fun httpHead(url: HttpUrl): Response

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param form payload as key=>value map
	 */
	suspend fun httpPost(url: String, form: Map<String, String>): Response = httpPost(url.toHttpUrl(), form)

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param form payload as key=>value map
	 */
	suspend fun httpPost(url: HttpUrl, form: Map<String, String>): Response

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param payload payload as `key=value` string with `&` separator
	 */
	suspend fun httpPost(url: String, payload: String): Response = httpPost(url.toHttpUrl(), payload)

	/**
	 * Do a POST http request to specific url with `multipart/form-data` payload
	 * @param url
	 * @param payload payload as `key=value` string with `&` separator
	 */
	suspend fun httpPost(url: HttpUrl, payload: String): Response

	/**
	 * Do a POST http request to specific url with json payload
	 * @param url
	 * @param body
	 */
	suspend fun httpPost(url: String, body: JSONObject): Response = httpPost(url.toHttpUrl(), body)

	/**
	 * Do a POST http request to specific url with json payload
	 * @param url
	 * @param body
	 */
	suspend fun httpPost(url: HttpUrl, body: JSONObject): Response

	/**
	 * Do a GraphQL request to specific url
	 * @param endpoint an url
	 * @param query GraphQL request payload
	 */
	suspend fun graphQLQuery(endpoint: String, query: String): JSONObject
}
