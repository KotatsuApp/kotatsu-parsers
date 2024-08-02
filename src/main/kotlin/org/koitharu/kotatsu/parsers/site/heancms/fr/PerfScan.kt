package org.koitharu.kotatsu.parsers.site.heancms.fr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms

@MangaSourceParser("PERF_SCAN", "PerfScan", "fr")
internal class PerfScan(context: MangaLoaderContext) :
	HeanCms(context, MangaParserSource.PERF_SCAN, "perf-scan.fr")
