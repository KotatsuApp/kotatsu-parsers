package org.koitharu.kotatsu.parsers.bitmap

import kotlinx.io.Sink

abstract class Bitmap {
	abstract val width: Int
	abstract val height: Int

	enum class CompressFormat {
		JPEG,
		PNG,
		WEBP_LOSSY,
		WEBP_LOSSLESS,
	}

	enum class Config {
		ALPHA_8,
		RGB_565,
		ARGB_8888,
		RGBA_F16,
		HARDWARE,
		RGBA_1010102,
	}

	abstract fun compress(
		format: CompressFormat,
		quality: Int,
		sink: Sink
	): Boolean

	abstract fun getPixels(
		/*@ColorInt*/
		pixels: IntArray,
		offset: Int,
		stride: Int,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
	)

	abstract fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect)

	companion object {
		fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
			throw NotImplementedError("createBitmap(width, height, config) is not implemented")
		}

		fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
			throw NotImplementedError("createBitmap(source, x, y, width, height) is not implemented")
		}
	}
}
