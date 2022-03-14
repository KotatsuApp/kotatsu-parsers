package org.koitharu.kotatsu.parsers

import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.*

fun MangaSource.newParser(context: MangaLoaderContext): MangaParser = when (this) {
	MangaSource.READMANGA_RU -> ReadmangaParser(context)
	MangaSource.MINTMANGA -> MintMangaParser(context)
	MangaSource.SELFMANGA -> SelfMangaParser(context)
	MangaSource.MANGACHAN -> MangaChanParser(context)
	MangaSource.DESUME -> DesuMeParser(context)
	MangaSource.HENCHAN -> HenChanParser(context)
	MangaSource.YAOICHAN -> YaoiChanParser(context)
	MangaSource.MANGATOWN -> MangaTownParser(context)
	MangaSource.MANGALIB -> MangaLibParser(context)
	MangaSource.NUDEMOON -> NudeMoonParser(context)
	MangaSource.MANGAREAD -> MangareadParser(context)
	MangaSource.REMANGA -> RemangaParser(context)
	MangaSource.HENTAILIB -> HentaiLibParser(context)
	MangaSource.ANIBEL -> AnibelParser(context)
	MangaSource.NINEMANGA_EN -> NineMangaParser.English(context)
	MangaSource.NINEMANGA_ES -> NineMangaParser.Spanish(context)
	MangaSource.NINEMANGA_RU -> NineMangaParser.Russian(context)
	MangaSource.NINEMANGA_DE -> NineMangaParser.Deutsch(context)
	MangaSource.NINEMANGA_IT -> NineMangaParser.Italiano(context)
	MangaSource.NINEMANGA_BR -> NineMangaParser.Brazil(context)
	MangaSource.NINEMANGA_FR -> NineMangaParser.Francais(context)
	MangaSource.EXHENTAI -> ExHentaiParser(context)
	MangaSource.MANGAOWL -> MangaOwlParser(context)
	MangaSource.MANGADEX -> MangaDexParser(context)
	MangaSource.BATOTO -> BatoToParser(context)
	MangaSource.COMICK_FUN -> ComickFunParser(context)
	MangaSource.LOCAL -> throw NotImplementedError("Local manga parser is not supported")
}.also {
	require(it.source == this) {
		"Cannot instantiate manga parser: $name mapped to ${it.source}"
	}
}