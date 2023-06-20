package org.koitharu.kotatsu.parsers.ksp

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import java.io.Writer
import java.util.*

class ParserProcessor(
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {

	private val availableLocales = Locale.getAvailableLocales().toSet()
	private val sourceNamePattern = Regex("[A-Z_][A-Z0-9_]{3,}")

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val symbols = resolver
			.getSymbolsWithAnnotation("org.koitharu.kotatsu.parsers.MangaSourceParser")
		val ret = symbols.filterNot { it.validate() }.toList()
		if (!symbols.iterator().hasNext()) {
			return ret
		}
		val dependencies = Dependencies.ALL_FILES
		val factoryFile = try {
			codeGenerator.createNewFile(
				dependencies = dependencies,
				packageName = "org.koitharu.kotatsu.parsers",
				fileName = "MangaParserFactory",
			)
		} catch (e: FileAlreadyExistsException) {
			logger.warn(e.toString(), null)
			null
		}
		val sourcesFile = try {
			codeGenerator.createNewFile(
				dependencies = dependencies,
				packageName = "org.koitharu.kotatsu.parsers.model",
				fileName = "MangaSource",
			)
		} catch (e: FileAlreadyExistsException) {
			logger.warn(e.toString(), null)
			null
		}
		sourcesFile?.writer().use { sourcesWriter ->
			factoryFile?.writer().use { factoryWriter ->
				writeContent(sourcesWriter, factoryWriter, symbols)
			}
		}
		return ret
	}

	private fun writeContent(
		sourcesWriter: Writer?,
		factoryWriter: Writer?,
		symbols: Sequence<KSAnnotated>,
	) {
		if (sourcesWriter == null && factoryWriter == null) {
			return
		}
		factoryWriter?.write(
			"""
				package org.koitharu.kotatsu.parsers

				import org.koitharu.kotatsu.parsers.model.MangaSource
				
				@Suppress("DEPRECATION")
				@Deprecated("", replaceWith = ReplaceWith("context.newParserInstance(this)"))
				fun MangaSource.newParser(context: MangaLoaderContext): MangaParser = when (this) {
				
			""".trimIndent(),
		)
		sourcesWriter?.write(
			"""
				package org.koitharu.kotatsu.parsers.model
				
				enum class MangaSource(
					val title: String,
					val locale: String?,
				) {
					LOCAL("Local", null),
				
			""".trimIndent(),
		)

		val visitor = ParserVisitor(sourcesWriter, factoryWriter)
		symbols
			.filter { it is KSClassDeclaration && it.validate() }
			.forEach { it.accept(visitor, Unit) }

		factoryWriter?.write(
			"""
				MangaSource.LOCAL -> throw NotImplementedError("Local manga parser is not supported")
				MangaSource.DUMMY -> throw NotImplementedError("Dummy manga parser cannot be instantiated")
			}.also {
				require(it.source == this) {
					"Cannot instantiate manga parser: ${'$'}name mapped to ${'$'}{it.source}"
				}
			}
			""".trimIndent(),
		)
		sourcesWriter?.write(
			"""
				DUMMY("Dummy", null),
				;
			}
			""".trimIndent(),
		)
	}

	private inner class ParserVisitor(
		private val sourcesWriter: Writer?,
		private val factoryWriter: Writer?,
	) : KSVisitorVoid() {

		override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
			if (classDeclaration.classKind != ClassKind.CLASS || classDeclaration.isAbstract()) {
				logger.error("Only non-abstract can be annotated with @MangaSourceParser", classDeclaration)
			}
			val annotation = classDeclaration.annotations.single { it.shortName.asString() == "MangaSourceParser" }
			val deprecation = classDeclaration.annotations.singleOrNull { it.shortName.asString() == "Deprecated" }
			val name = annotation.arguments.single { it.name?.asString() == "name" }.value as String
			val title = annotation.arguments.single { it.name?.asString() == "title" }.value as String
			val locale = annotation.arguments.single { it.name?.asString() == "locale" }.value as String
			val localeString = if (locale.isEmpty()) "null" else "\"$locale\""
			val localeObj = if (locale.isEmpty()) null else Locale(locale)
			val localeTitle = localeObj?.getDisplayLanguage(localeObj)
			if (localeObj != null && localeObj !in availableLocales) {
				logger.error("Manga source $name has wrong locale: $localeTitle")
			}

			if (!sourceNamePattern.matches(name)) {
				logger.error("Manga source name must be uppercase: $name")
			}

			val constructor = classDeclaration.primaryConstructor
			if (constructor == null || constructor.parameters.count { !it.hasDefault } != 1) {
				logger.error(
					"Class with @MangaSourceParser must have a primary constructor with one parameter",
					classDeclaration,
				)
			}
			val className = classDeclaration.qualifiedName?.asString()
			factoryWriter?.write("\tMangaSource.$name -> $className(context)\n")
			val deprecationString = if (deprecation != null) {
				val reason = deprecation.arguments
					.find { it.name?.asString() == "message" }?.value?.toString() ?: "Unknown reason"
				"@Deprecated(\"$reason\") "
			} else ""
			val localeComment = localeTitle?.toTitleCase(localeObj)?.let { " /* $it */" }.orEmpty()
			sourcesWriter?.write("\t$deprecationString$name(\"$title\", $localeString$localeComment),\n")
		}
	}
}
