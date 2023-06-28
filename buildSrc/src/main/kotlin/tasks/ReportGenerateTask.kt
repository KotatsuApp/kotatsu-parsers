package tasks

import korlibs.template.Template
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.simpleframework.xml.Serializer
import org.simpleframework.xml.core.Persister
import tasks.model.TestCase
import tasks.model.TestSuite
import java.io.File
import kotlin.math.roundToInt

open class ReportGenerateTask : DefaultTask() {

	@TaskAction
	fun execute() {
		val reportsRoot = File(project.rootDir, "build/test-results/test")
		val outputRoot = File(project.rootDir, "build/test-results-html")
		outputRoot.deleteRecursively()
		outputRoot.mkdir()
		for (file in checkNotNull(reportsRoot.listFiles()) {
			"No files found in $reportsRoot"
		}) {
			if (file.isDirectory) {
				continue
			}
			val report = makeReport(file)
			val output = File(outputRoot, file.nameWithoutExtension + ".htm")
			output.writeText(report)
			println("Report generated: ${output.absolutePath}")
		}
	}

	private fun makeReport(file: File): String {
		val serializer: Serializer = Persister()
		val testSuite = serializer.read(TestSuite::class.java, file)
		val templateText = javaClass.classLoader.getResourceAsStream("report.html")?.use {
			it.bufferedReader().readText()
		}

		val results = LinkedHashMap<String, LinkedHashMap<String, TestCase>>()
		val tests = LinkedHashSet<String>()
		for (case in testSuite.testCases) {
			if (!case.isValid()) {
				continue
			}
			tests.add(case.testName)
			val map = results.getOrPut(case.source) { LinkedHashMap() }
			val oldValue = map.put(case.testName, case)
			check(oldValue == null) { "Check failed: $oldValue" }
		}

		val failPercent = (testSuite.failures.toDouble() / testSuite.tests * 100.0).roundToInt()
		val errorPercent = (testSuite.errors.toDouble() / testSuite.tests * 100.0).roundToInt()
		return runBlocking {
			val template = Template(requireNotNull(templateText))
			template(
				mapOf(
					"testSuite" to testSuite,
					"tests" to tests,
					"results" to results,
					"success_percent" to 100 - (failPercent + errorPercent),
					"error_percent" to errorPercent,
					"fail_percent" to failPercent,
					"success" to testSuite.tests - (testSuite.failures + testSuite.errors),
				),
			)
		}
	}
}
