package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("MASTERKOMIK", "Tenshi ( MasterKomik )", "id")
internal class MasterKomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MASTERKOMIK, "tenshi.id", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"
	override val listUrl = "/komik"
}
