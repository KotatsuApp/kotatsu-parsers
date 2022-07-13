package org.koitharu.kotatsu.parsers.util

import okhttp3.Headers
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.Favicon
import org.koitharu.kotatsu.parsers.model.Favicons
import org.koitharu.kotatsu.parsers.util.json.mapJSON

class FaviconParser(
	private val context: MangaLoaderContext,
	private val domain: String,
	private val headers: Headers?,
) {

	suspend fun parseFavicons(): Favicons {
		val url = "https://$domain"
		val doc = context.httpGet(url, headers).parseHtml()
		val result = HashSet<Favicon>()
		val manifestLink = doc.getElementsByAttributeValue("rel", "manifest").firstOrNull()
			?.attrAsAbsoluteUrlOrNull("href")
		if (manifestLink != null) {
			result += parseManifest(manifestLink)
		}
		val links = doc.getElementsByAttributeValueContaining("rel", "icon")
		links.mapNotNullTo(result) { link ->
			parseLink(link)
		}
		if (result.isEmpty()) {
			result.add(createFallback())
		}
		return Favicons(result, url)
	}

	private fun parseLink(link: Element): Favicon? {
		val href = link.attrAsAbsoluteUrlOrNull("href")
		if (href == null || href.endsWith('/')) {
			return null
		}
		val sizes = link.attr("sizes")
		return Favicon(
			url = href,
			size = parseSize(sizes),
			rel = link.attrOrNull("rel"),
		)
	}

	private fun parseSize(sizes: String): Int {
		if (sizes.isEmpty() || sizes == "any") {
			return 0
		}
		return sizes.substringBefore(' ')
			.split('x', 'X', '*')
			.firstNotNullOfOrNull { it.toIntOrNull() }
			?: 0
	}

	private suspend fun parseManifest(url: String): List<Favicon> {
		val json = context.httpGet(url, headers).parseJson()
		val icons = json.getJSONArray("icons")
		return icons.mapJSON { jo ->
			Favicon(
				url = jo.getString("src").resolveLink(),
				size = parseSize(jo.getString("sizes")),
				rel = null,
			)
		}
	}

	private fun createFallback(): Favicon {
		val href = "https://$domain/favicon.ico"
		return Favicon(
			url = href,
			size = 0,
			rel = null,
		)
	}

	private fun String.resolveLink(): String {
		return when {
			startsWith("http:") || startsWith("https:") -> {
				this
			}

			startsWith('/') -> {
				"https://$domain$this"
			}

			else -> {
				"https://$domain/$this"
			}
		}
	}
}