package org.koitharu.kotatsu.parsers.exception

import okio.IOException
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.util.json.mapJSON

public class GraphQLException @InternalParsersApi constructor(private val errors: JSONArray) : IOException() {

	public val messages = errors.mapJSON {
		it.getString("message")
	}

	override val message: String
		get() = messages.joinToString("\n")
}
