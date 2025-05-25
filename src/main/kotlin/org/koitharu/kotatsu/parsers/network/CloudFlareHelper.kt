package org.koitharu.kotatsu.parsers.network

import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAVAILABLE

public object CloudFlareHelper {

	public const val PROTECTION_NOT_DETECTED: Int = 0
	public const val PROTECTION_CAPTCHA: Int = 1
	public const val PROTECTION_BLOCKED: Int = 2

	private const val CF_CLEARANCE = "cf_clearance"

	public fun checkResponseForProtection(response: Response): Int {
		if (response.code != HTTP_FORBIDDEN && response.code != HTTP_UNAVAILABLE) {
			return PROTECTION_NOT_DETECTED
		}
		val content = if (response.body != null) {
			response.peekBody(Long.MAX_VALUE).use {
				Jsoup.parse(it.byteStream(), Charsets.UTF_8.name(), response.request.url.toString())
			}
		} else {
			return PROTECTION_NOT_DETECTED
		}
		return when {
			content.selectFirst("h2[data-translate=\"blocked_why_headline\"]") != null -> PROTECTION_BLOCKED
			content.getElementById("challenge-error-title") != null || content.getElementById("challenge-error-text") != null -> PROTECTION_CAPTCHA

			else -> PROTECTION_NOT_DETECTED
		}
	}

	public fun getClearanceCookie(cookieJar: CookieJar, url: String): String? {
		return cookieJar.loadForRequest(url.toHttpUrl()).find { it.name == CF_CLEARANCE }?.value
	}

	public fun isCloudFlareCookie(name: String): Boolean {
		return name.startsWith("cf_")
			|| name.startsWith("_cf")
			|| name.startsWith("__cf")
			|| name == "csrftoken"
	}
}
