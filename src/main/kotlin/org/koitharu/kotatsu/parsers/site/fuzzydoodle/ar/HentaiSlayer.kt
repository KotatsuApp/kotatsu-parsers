package org.koitharu.kotatsu.parsers.site.fuzzydoodle.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.site.fuzzydoodle.FuzzyDoodleParser
import java.util.*

@MangaSourceParser("HENTAISLAYER", "HentaiSlayer", "ar", ContentType.HENTAI)
internal class HentaiSlayer(context: MangaLoaderContext) :
	FuzzyDoodleParser(context, MangaParserSource.HENTAISLAYER, "hentaislayer.net") {

	override val ongoingValue = "مستمر"
	override val finishedValue = "مكتمل"
	override val abandonedValue = "متوقف"

	override val mangaValue = "مانجا"
	override val manhuaValue = "مانهوا"
	override val comicsValue = "كوميكس"

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHUA,
			ContentType.COMICS,
		),
	)
}
