package org.koitharu.kotatsu.parsers.site.fuzzydoodle.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.site.fuzzydoodle.FuzzyDoodleParser
import java.util.EnumSet

@MangaSourceParser("LELSCANVF", "LelScanFr", "fr")
internal class LelScanVf(context: MangaLoaderContext) :
	FuzzyDoodleParser(context, MangaParserSource.LELSCANVF, "lelscanfr.com") {
	override val availableStates: Set<MangaState> = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)
	override val ongoingValue = "en-cours"
	override val finishedValue = "termin"
}
