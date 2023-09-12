package org.koitharu.kotatsu.parsers

import org.junit.jupiter.params.provider.EnumSource
import org.koitharu.kotatsu.parsers.model.MangaSource

@EnumSource(MangaSource::class, names = ["HENTAIUKR"], mode = EnumSource.Mode.INCLUDE)
internal annotation class MangaSources
