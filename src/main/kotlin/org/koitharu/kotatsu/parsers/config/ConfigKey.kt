package org.koitharu.kotatsu.parsers.config

sealed class ConfigKey<T>(
	@JvmField val key: String,
) {

	abstract val defaultValue: T

	class Domain(
		override val defaultValue: String,
		@JvmField val presetValues: Array<String>?,
	) : ConfigKey<String>("domain")

	class ShowSuspiciousContent(
		override val defaultValue: Boolean,
	) : ConfigKey<Boolean>("show_suspicious")

	class UserAgent(
		override val defaultValue: String,
	) : ConfigKey<String>("user_agent")
}
