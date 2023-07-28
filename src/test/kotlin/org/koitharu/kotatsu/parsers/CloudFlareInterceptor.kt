package org.koitharu.kotatsu.parsers

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.net.HttpURLConnection

private const val HEADER_SERVER = "Server"
private const val SERVER_CLOUDFLARE = "cloudflare"

internal class CloudFlareInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)
		if (response.code == HttpURLConnection.HTTP_FORBIDDEN || response.code == HttpURLConnection.HTTP_UNAVAILABLE) {
			if (response.header(HEADER_SERVER)?.startsWith(SERVER_CLOUDFLARE) == true) {
				response.closeQuietly()
				throw CloudFlareProtectedException(
					url = response.request.url.toString(),
					headers = request.headers,
				)
			}
		}
		return response
	}
}
