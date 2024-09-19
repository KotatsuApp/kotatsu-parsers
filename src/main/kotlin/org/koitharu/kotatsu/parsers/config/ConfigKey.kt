package org.koitharu.kotatsu.parsers.config

public sealed class ConfigKey<T>(
	@JvmField public val key: String,
) {

	public abstract val defaultValue: T

	public class Domain(
		@JvmField @JvmSuppressWildcards public vararg val presetValues: String,
	) : ConfigKey<String>("domain") {

		init {
			require(presetValues.isNotEmpty()) { "You must provide at least one domain" }
		}

		override val defaultValue: String
			get() = presetValues.first()
	}

	public class ShowSuspiciousContent(
		override val defaultValue: Boolean,
	) : ConfigKey<Boolean>("show_suspicious")

	public class UserAgent(
		override val defaultValue: String,
	) : ConfigKey<String>("user_agent")

	public class SplitByTranslations(
		override val defaultValue: Boolean,
	) : ConfigKey<Boolean>("split_translations")

	public class PreferredImageServer(
		public val presetValues: Map<String?, String?>,
		override val defaultValue: String?,
	) : ConfigKey<String?>("img_server")
}
