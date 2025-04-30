package org.koitharu.kotatsu.parsers.network

import io.ktor.client.plugins.cookies.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.bytestring.decodeToString
import org.jsoup.Jsoup
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAVAILABLE

public object CloudFlareHelper {

	public const val PROTECTION_NOT_DETECTED: Int = 0
	public const val PROTECTION_CAPTCHA: Int = 1
	public const val PROTECTION_BLOCKED: Int = 2

	private const val CF_CLEARANCE = "cf_clearance"

	public suspend fun checkResponseForProtection(response: HttpResponse): Int {
		val statusCode = response.status.value
		if (statusCode != HTTP_FORBIDDEN && statusCode != HTTP_UNAVAILABLE) {
			return PROTECTION_NOT_DETECTED
		}
		val content = response.bodyAsChannel().peek(Int.MAX_VALUE)?.let {
			Jsoup.parse(it.decodeToString(), response.request.url.toString())
		} ?: return PROTECTION_NOT_DETECTED
		return when {
			content.selectFirst("h2[data-translate=\"blocked_why_headline\"]") != null -> PROTECTION_BLOCKED
			content.getElementById("challenge-error-title") != null || content.getElementById("challenge-error-text") != null -> PROTECTION_CAPTCHA

			else -> PROTECTION_NOT_DETECTED
		}
	}

	public suspend fun getClearanceCookie(cookiesStorage: CookiesStorage, url: String): String? {
		return cookiesStorage.get(Url(url)).find { it.name == CF_CLEARANCE }?.value
	}

	public fun isCloudFlareCookie(name: String): Boolean {
		return name.startsWith("cf_")
			|| name.startsWith("_cf")
			|| name.startsWith("__cf")
			|| name == "csrftoken"
	}
}
