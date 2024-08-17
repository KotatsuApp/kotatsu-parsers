package org.koitharu.kotatsu.parsers

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.mergeWith

private const val HEADER_REFERER = "Referer"

internal class CommonHeadersInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val source = request.tag(MangaSource::class.java)
		val parser = if (source is MangaParserSource) {
			MangaLoaderContextMock.newParserInstance(source)
		} else {
			null
		}
		val sourceHeaders = parser?.getRequestHeaders()
		val headersBuilder = request.headers.newBuilder()
		if (sourceHeaders != null) {
			headersBuilder.mergeWith(sourceHeaders, replaceExisting = false)
		}
		if (headersBuilder[HEADER_REFERER] == null && parser != null) {
			headersBuilder[HEADER_REFERER] = "https://${parser.domain}/"
		}
		val newRequest = request.newBuilder().headers(headersBuilder.build()).build()
		return if (parser is Interceptor) {
			parser.intercept(ProxyChain(chain, newRequest))
		} else {
			chain.proceed(newRequest)
		}
	}

	private class ProxyChain(
		private val delegate: Interceptor.Chain,
		private val request: Request,
	) : Interceptor.Chain by delegate {

		override fun request(): Request = request
	}
}
