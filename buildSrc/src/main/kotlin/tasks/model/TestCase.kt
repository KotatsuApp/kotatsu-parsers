package tasks.model

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

@Root(name = "testcase", strict = false)
class TestCase {
	@JvmField
	@field:Attribute(name = "name")
	var name: String = ""

	@JvmField
	@field:Attribute(name = "time")
	var time: Float = 0f

	@JvmField
	@field:Element(name = "failure", required = false)
	var failure: Failure? = null

	val index by lazy {
		name.split('|').getOrNull(0)?.toIntOrNull() ?: 0
	}

	val testName by lazy {
		name.split('|').getOrNull(1).orEmpty()
	}

	val source by lazy {
		name.split('|').getOrNull(2).orEmpty()
	}

	fun isValid() = name.count { it == '|' } == 2
}
