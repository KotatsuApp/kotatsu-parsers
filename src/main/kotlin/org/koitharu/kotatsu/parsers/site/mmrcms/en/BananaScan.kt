package org.koitharu.kotatsu.parsers.site.mmrcms.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser

@MangaSourceParser("BANANASCAN_COM", "BananaScan.Com", "en")
internal class BananaScan(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.BANANASCAN_COM, "bananascans.com")
