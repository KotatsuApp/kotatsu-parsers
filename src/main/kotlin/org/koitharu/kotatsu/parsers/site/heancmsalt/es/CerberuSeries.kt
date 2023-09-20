package org.koitharu.kotatsu.parsers.site.heancmsalt.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.heancmsalt.HeanCmsAlt

@MangaSourceParser("LEGIONSCANS", "CerberuSeries", "es")
internal class CerberuSeries(context: MangaLoaderContext) :
	HeanCmsAlt(context, MangaSource.LEGIONSCANS, "cerberuseries.xyz")
