package org.koitharu.kotatsu.parsers.exception

import okio.IOException
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.util.json.mapJSON

class GraphQLException(private val errors: JSONArray) : IOException() {

	val messages = errors.mapJSON {
		it.getString("message")
	}

	override val message: String
		get() = messages.joinToString("\n")
}