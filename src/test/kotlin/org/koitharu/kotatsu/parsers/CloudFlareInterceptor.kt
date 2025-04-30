package org.koitharu.kotatsu.parsers

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.network.HttpInterceptor

internal class CloudFlareInterceptor : HttpInterceptor {

	override suspend fun intercept(sender: Sender, request: HttpRequestBuilder): HttpClientCall {
		val call = sender.execute(request)
		if (CloudFlareHelper.checkResponseForProtection(call.response) != CloudFlareHelper.PROTECTION_NOT_DETECTED) {
			throw CloudFlareProtectedException(
				url = call.request.url.toString(),
				headers = call.request.headers,
			)
		}
		return call
	}
}
