package org.koitharu.kotatsu.parsers

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.network.HttpInterceptor
import org.koitharu.kotatsu.parsers.network.MangaSourceAttributeKey

private const val HEADER_REFERER = "Referer"

internal class CommonHeadersInterceptor : HttpInterceptor {

	override suspend fun intercept(
		sender: Sender,
		request: HttpRequestBuilder,
	): HttpClientCall {
		val source = request.attributes[MangaSourceAttributeKey]
		val parser = if (source is MangaParserSource) {
			MangaLoaderContextMock.newParserInstance(source)
		} else {
			return sender.execute(request)
		}
		request.headers.appendIfNameAbsent(HEADER_REFERER, "https://${parser.domain}/")
		return parser.intercept(sender, request)
	}
}
