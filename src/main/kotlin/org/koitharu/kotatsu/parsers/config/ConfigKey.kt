package org.koitharu.kotatsu.parsers.config

sealed class ConfigKey<T>(
	@JvmField val key: String,
) {

	abstract val defaultValue: T

	class Domain(
		@JvmField vararg val presetValues: String,
	) : ConfigKey<String>("domain") {

		init {
			require(presetValues.isNotEmpty()) { "You must provide at least one domain" }
		}

		override val defaultValue: String
			get() = presetValues.first()
	}

	class ShowSuspiciousContent(
		override val defaultValue: Boolean,
	) : ConfigKey<Boolean>("show_suspicious")

	class UserAgent(
		override val defaultValue: String,
	) : ConfigKey<String>("user_agent")
}
