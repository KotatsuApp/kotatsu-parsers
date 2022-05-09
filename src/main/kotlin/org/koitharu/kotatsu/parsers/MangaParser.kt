package org.koitharu.kotatsu.parsers

import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl

abstract class MangaParser(val source: MangaSource) {

	protected abstract val context: MangaLoaderContext

	abstract val sortOrders: Set<SortOrder>

	val config by lazy { context.getConfig(source) }

	protected abstract val configKeyDomain: ConfigKey.Domain

	abstract suspend fun getList(
		offset: Int,
		query: String? = null,
		tags: Set<MangaTag>? = null,
		sortOrder: SortOrder? = null,
	): List<Manga>

	abstract suspend fun getDetails(manga: Manga): Manga

	abstract suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	open suspend fun getPageUrl(page: MangaPage): String = page.url.withDomain()

	abstract suspend fun getTags(): Set<MangaTag>

	open fun getFaviconUrl() = "https://${getDomain()}/favicon.ico"

	open fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	/* Utils */

	fun getDomain(): String {
		return config[configKeyDomain]
	}

	protected fun generateUid(url: String): Long {
		var h = 1125899906842597L
		source.name.forEach { c ->
			h = 31 * h + c.code
		}
		url.forEach { c ->
			h = 31 * h + c.code
		}
		return h
	}

	protected fun generateUid(id: Long): Long {
		var h = 1125899906842597L
		source.name.forEach { c ->
			h = 31 * h + c.code
		}
		h = 31 * h + id
		return h
	}

	protected fun String.withDomain(subdomain: String? = null): String {
		var domain = getDomain()
		if (subdomain != null) {
			domain = subdomain + "." + domain.removePrefix("www.")
		}
		return toAbsoluteUrl(domain)
	}

	protected fun parseFailed(message: String? = null): Nothing {
		throw ParseException(message)
	}
}