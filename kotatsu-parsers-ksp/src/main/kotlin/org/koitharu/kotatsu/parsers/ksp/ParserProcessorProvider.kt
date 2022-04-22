package org.koitharu.kotatsu.parsers.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class ParserProcessorProvider : SymbolProcessorProvider {

	override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
		return ParserProcessor(
			codeGenerator = environment.codeGenerator,
			logger = environment.logger,
			options = environment.options
		)
	}
}