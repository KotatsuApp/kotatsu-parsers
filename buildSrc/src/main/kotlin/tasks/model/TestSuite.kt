package tasks.model

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "testsuite", strict = false)
class TestSuite {
	@JvmField
	@field:Attribute(name = "name")
	var name: String = ""

	@JvmField
	@field:Attribute(name = "tests")
	var tests: Int = 0

	@JvmField
	@field:Attribute(name = "failures")
	var failures: Int = 0

	@JvmField
	@field:Attribute(name = "errors")
	var errors: Int = 0

	@field:ElementList(entry = "testcase", inline = true)
	lateinit var testCases: List<TestCase>
}