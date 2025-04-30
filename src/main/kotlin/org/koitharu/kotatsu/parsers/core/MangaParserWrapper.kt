package org.koitharu.kotatsu.parsers.core

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery

internal class MangaParserWrapper(
	private val delegate: MangaParser,
) : MangaParser by delegate {

	override val authorizationProvider: MangaParserAuthProvider?
		get() = delegate as? MangaParserAuthProvider

	override suspend fun getList(query: MangaSearchQuery): List<Manga> = withContext(Dispatchers.Default) {
		if (!query.skipValidation) {
			searchQueryCapabilities.validate(query)
		}
		delegate.getList(query)
	}

	override suspend fun getDetails(manga: Manga): Manga = withContext(Dispatchers.Default) {
		delegate.getDetails(manga)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.Default) {
		delegate.getPages(chapter)
	}

	override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.Default) {
		delegate.getPageUrl(page)
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions = withContext(Dispatchers.Default) {
		delegate.getFilterOptions()
	}

	override suspend fun getFavicons(): Favicons = withContext(Dispatchers.Default) {
		delegate.getFavicons()
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> = withContext(Dispatchers.Default) {
		delegate.getRelatedManga(seed)
	}

	override suspend fun intercept(sender: Sender, request: HttpRequestBuilder): HttpClientCall {
		delegate.getRequestHeaders().forEach { name, values ->
			request.headers.appendMissing(name, values)
		}
		return sender.execute(request)
	}
}
