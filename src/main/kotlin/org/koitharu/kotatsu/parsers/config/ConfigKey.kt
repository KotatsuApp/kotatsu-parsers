package org.koitharu.kotatsu.parsers.config

sealed class ConfigKey<T>(
	val key: String,
) {

	abstract val defaultValue: T

	class Domain(
		override val defaultValue: String,
		val presetValues: Array<String>?,
	) : ConfigKey<String>("domain")

	class ShowSuspiciousContent(
		override val defaultValue: Boolean,
	) : ConfigKey<Boolean>("show_suspicious")
}