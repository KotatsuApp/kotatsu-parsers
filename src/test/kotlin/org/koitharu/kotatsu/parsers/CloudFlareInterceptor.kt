package org.koitharu.kotatsu.parsers

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper

internal class CloudFlareInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val response = chain.proceed(request)
		if (CloudFlareHelper.checkResponseForProtection(response) != CloudFlareHelper.PROTECTION_NOT_DETECTED) {
			response.closeQuietly()
			throw CloudFlareProtectedException(
				url = response.request.url.toString(),
				headers = request.headers,
			)
		}
		return response
	}
}
