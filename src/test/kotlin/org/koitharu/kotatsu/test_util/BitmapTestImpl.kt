package org.koitharu.kotatsu.test_util

import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class BitmapTestImpl(
	val image: BufferedImage,
) : Bitmap {

	override val width: Int
		get() = image.width

	override val height: Int
		get() = image.height

	override fun drawBitmap(
		sourceBitmap: Bitmap,
		src: Rect,
		dst: Rect,
	) {
		val graphics = image.createGraphics()
		val subImage = (sourceBitmap as BitmapTestImpl).image.getSubimage(
			src.left, src.top, src.width, src.height,
		)
		graphics.drawImage(subImage, dst.left, dst.top, dst.width, dst.height, null)
		graphics.dispose()
	}

	fun compress(format: String): ByteArray = ByteArrayOutputStream().also { stream ->
		ImageIO.write(image, format, stream)
	}.toByteArray()
}
