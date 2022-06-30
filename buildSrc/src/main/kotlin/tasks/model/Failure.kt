package tasks.model

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Root
import org.simpleframework.xml.Text

@Root(name = "failure", strict = false)
class Failure {
	@JvmField
	@field:Attribute(name = "message")
	var message: String = ""

	@JvmField
	@field:Attribute(name = "type")
	var type: String = ""

	@JvmField
	@field:Text
	var text: String = ""

	fun textHtml(): String {
		return text.replace("\n", "<br>")
	}
}