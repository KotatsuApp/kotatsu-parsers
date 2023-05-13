package org.koitharu.kotatsu.parsers

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable

class AuthCheckExtension : BeforeAllCallback {

    private val loaderContext: MangaLoaderContext = MangaLoaderContextMock

    override fun beforeAll(context: ExtensionContext) {
        for (source in MangaSource.values()) {
            if (source == MangaSource.LOCAL || source == MangaSource.DUMMY) {
                continue
            }
            val parser = source.newParser(loaderContext)
            if (parser is MangaParserAuthProvider) {
                checkAuthorization(source, parser)
            }
        }
    }

    private fun checkAuthorization(source: MangaSource, parser: MangaParserAuthProvider) = runTest {
        runCatchingCancellable {
            parser.getUsername()
        }.onSuccess { username ->
            println("Signed in to ${source.name} as $username")
        }.onFailure { error ->
            System.err.println("Auth failed for ${source.name}: ${error.javaClass.name}(${error.message})")
        }
    }
}
