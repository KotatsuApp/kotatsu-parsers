package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PRISMA_SCANS", "Prisma Scans", "pt")
internal class PrismaScansParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PRISMA_SCANS, "prismacomics.com", 10) {
	override val datePattern = "MMM dd, yyyy"
}
