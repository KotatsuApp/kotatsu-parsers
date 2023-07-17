package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PETROTECHSOCIETY", "PetrotechSociety", "en")
internal class PetrotechSociety(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PETROTECHSOCIETY, "www.petrotechsociety.org", pageSize = 10) {

	override val postreq = true
	override val isNsfwSource = true
}
