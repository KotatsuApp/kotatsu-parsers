package org.koitharu.kotatsu.parsers.ksp

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import java.io.File
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
		val symbols = resolver.getSymbolsWithAnnotation("org.koitharu.kotatsu.parsers.MangaSourceParser")
		val ret = symbols.filterNot { it.validate() }.toList()
		if (!symbols.iterator().hasNext()) {
			return ret
		}
		val dependencies = Dependencies.ALL_FILES
		val factoryFile =
			try {
				codeGenerator.createNewFile(
					dependencies = dependencies,
					packageName = "org.koitharu.kotatsu.parsers",
					fileName = "MangaParserFactory",
				)
			} catch (e: FileAlreadyExistsException) {
				logger.warn(e.toString(), null)
				null
			}
		val sourcesFile =
			try {
				codeGenerator.createNewFile(
					dependencies = dependencies,
					packageName = "org.koitharu.kotatsu.parsers.model",
					fileName = "MangaSource",
				)
			} catch (e: FileAlreadyExistsException) {
				logger.warn(e.toString(), null)
				null
			}
		val totalCount = sourcesFile?.writer().use { sourcesWriter ->
			factoryFile?.writer().use { factoryWriter ->
				writeContent(sourcesWriter, factoryWriter, symbols)
			}
		}
		writeSummary(totalCount)
		return ret
	}

	private fun writeContent(
		sourcesWriter: Writer?,
		factoryWriter: Writer?,
		symbols: Sequence<KSAnnotated>,
	): Int {
		if (sourcesWriter == null && factoryWriter == null) {
			return 0
		}
		factoryWriter?.write(
			"""
			package org.koitharu.kotatsu.parsers

			import org.koitharu.kotatsu.parsers.model.MangaParserSource

			internal fun MangaParserSource.newParser(context: MangaLoaderContext): MangaParser = when (this) {
			
			""".trimIndent(),
		)
		sourcesWriter?.write(
			"""
			package org.koitharu.kotatsu.parsers.model
			
			public enum class MangaParserSource(
				public val title: String,
				public val locale: String,
				public val contentType: ContentType,
				public val isBroken: Boolean,
			): MangaSource {
			
			""".trimIndent(),
		)

		val visitor = ParserVisitor(sourcesWriter, factoryWriter)
		val totalCount = symbols
			.filter { it is KSClassDeclaration && it.validate() }
			.onEach { it.accept(visitor, Unit) }
			.count()

		factoryWriter?.write(
			"""
				MangaParserSource.DUMMY -> throw NotImplementedError("Manga parser ${'$'}name cannot be instantiated")
			}.also {
				require(it.source == this) {
					"Cannot instantiate manga parser: ${'$'}name mapped to ${'$'}{it.source}"
				}
			}
			""".trimIndent(),
		)
		sourcesWriter?.write(
			"""
				DUMMY("Dummy", "", ContentType.OTHER, false),
				;
			}
			""".trimIndent(),
		)
		return totalCount
	}

	private fun writeSummary(totalCount: Int) {
		val file = File(options["summaryOutputDir"] ?: return, "summary.yaml")
		file.writeText("total: $totalCount")
	}

	private inner class ParserVisitor(
		private val sourcesWriter: Writer?,
		private val factoryWriter: Writer?,
	) : KSVisitorVoid() {
		private val titles = HashMap<String, String>()

		override fun visitClassDeclaration(
			classDeclaration: KSClassDeclaration,
			data: Unit,
		) {
			if (classDeclaration.classKind != ClassKind.CLASS || classDeclaration.isAbstract()) {
				logger.error("Only non-abstract can be annotated with @MangaSourceParser", classDeclaration)
			}
			val annotation = classDeclaration.annotations.single { it.shortName.asString() == "MangaSourceParser" }
			val deprecation = classDeclaration.annotations.singleOrNull { it.shortName.asString() == "Deprecated" }
			val isBroken = classDeclaration.annotations.any { it.shortName.asString() == "Broken" }
			val name = annotation.arguments.single { it.name?.asString() == "name" }.value as String
			val title = annotation.arguments.single { it.name?.asString() == "title" }.value as String
			val locale = annotation.arguments.single { it.name?.asString() == "locale" }.value as String
			val type = annotation.arguments.single { it.name?.asString() == "type" }.value
			val localeString = "\"$locale\""
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
			val className = checkNotNull(classDeclaration.qualifiedName?.asString()) { "Class name is null" }

			val prevTitleClass = titles.put(title, className)
			if (prevTitleClass != null) {
				logger.warn("Source title duplication: \"$title\" is assigned to both $prevTitleClass and $className")
			}

			factoryWriter?.write("\tMangaParserSource.$name -> $className(context)\n")
			val deprecationString =
				if (deprecation != null) {
					val reason =
						deprecation.arguments
							.find { it.name?.asString() == "message" }
							?.value
							?.toString() ?: "Unknown reason"
					"@Deprecated(\"$reason\") "
				} else {
					""
				}
			val localeComment = localeTitle?.toTitleCase(localeObj)?.let { " /* $it */" }.orEmpty()
			sourcesWriter?.write(
				"\t$deprecationString$name(\"$title\", $localeString$localeComment, ContentType.$type, $isBroken),\n",
			)
		}
	}
}
