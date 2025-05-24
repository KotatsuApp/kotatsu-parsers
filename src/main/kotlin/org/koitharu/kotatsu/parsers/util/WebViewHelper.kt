package org.koitharu.kotatsu.parsers.util

import org.koitharu.kotatsu.parsers.MangaLoaderContext

public class WebViewHelper(
	private val context: MangaLoaderContext,
	private val domain: String,
) {

	public suspend fun getLocalStorageValue(key: String): String? {
		return context.evaluateJs("window.localStorage.getItem(\"$key\")")
	}
}
