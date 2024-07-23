package org.koitharu.kotatsu.parsers

import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@EnumSource(MangaParserSource::class, names = ["DUMMY"], mode = EXCLUDE)
internal annotation class MangaSources
