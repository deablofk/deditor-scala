package dev.cwby.guitk.text

import dev.cwby.guitk.renderer.Texture

import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

final class CharInfo {
  var advance: Float   = 0f
  var width: Float     = 0f
  var height: Float    = 0f
  var xoffset: Float   = 0f
  var yoffset: Float   = 0f
  var x0: Float        = 0f; var y0: Float = 0f; var x1: Float = 0f; var y1: Float = 0f
  var s0: Float        = 0f; var t0: Float = 0f; var s1: Float = 0f; var t1: Float = 0f
  var isColor: Boolean = false
  var lastAccess: Long = 0L
}

final class GlyphCache(fontSize: Float, primaryFont: Font, fallbackFonts: Seq[Font]) {
  import CFreeTypeHarfBuzz._

  private val bitmapWidth: Int =
    if (fontSize <= 25) 1024 else if (fontSize <= 50) 2048 else if (fontSize <= 100) 4096 else 8192
  private val bitmapHeight: Int      = bitmapWidth
  private val colorBitmapWidth: Int  = if (fontSize <= 25) 2048 else if (fontSize <= 50) 4096 else 8192
  private val colorBitmapHeight: Int = colorBitmapWidth

  private val charInfoCache       = scala.collection.mutable.Map[Int, CharInfo]()
  private val glyphToFontMap      = scala.collection.mutable.Map[Int, Int]()
  private val colorEmojiLRU       = scala.collection.mutable.LinkedHashMap[Int, Long]()
  private var accessCounter: Long = 0L

  private case class PendingUpdate(
      glyphX: Int,
      glyphY: Int,
      width: Int,
      height: Int,
      data: Ptr[Byte],
      pitch: Int,
      isColor: Boolean
  )
  private val pendingUpdates = scala.collection.mutable.ArrayBuffer[PendingUpdate]()

  private var texture: Texture       = _
  private var colorTexture: Texture  = _
  private var bitmap: Ptr[Byte]      = _
  private var colorBitmap: Ptr[Byte] = _

  private var atlasX: Int                 = 0
  private var atlasY: Int                 = 0
  private var atlasRowHeight: Int         = 0
  private var colorAtlasX: Int            = 0
  private var colorAtlasY: Int            = 0
  private var colorAtlasRowHeight: Int    = 0
  private var isInitialized: Boolean      = false
  private var fallbackGlyphInfo: CharInfo = _

  init()

  private def init(): Unit = {
    val bitmapBytes = bitmapWidth * bitmapHeight
    bitmap = malloc(bitmapBytes).asInstanceOf[Ptr[Byte]]
    memset(bitmap, 0, bitmapBytes.toUSize)

    val colorBitmapBytes = colorBitmapWidth * colorBitmapHeight * 4
    colorBitmap = malloc(colorBitmapBytes).asInstanceOf[Ptr[Byte]]
    memset(colorBitmap, 0, colorBitmapBytes.toUSize)

    atlasX = 0
    atlasY = 0
    atlasRowHeight = 0
    colorAtlasX = 0
    colorAtlasY = 0
    colorAtlasRowHeight = 0

    packAsciiRange()
    packFallbackGlyph()

    texture = new Texture(bitmapWidth, bitmapHeight, bitmap, false)
    colorTexture = new Texture(colorBitmapWidth, colorBitmapHeight, colorBitmap, true)
    isInitialized = true
  }

  private def packFallbackGlyph(): Unit = {
    Zone {
      val glyphBitmap  = alloc[GlyphBitmap]()
      val glyphMetrics = alloc[GlyphMetrics]()

      if (!primaryFont.renderGlyphByIndex(0, glyphBitmap, glyphMetrics)) {
        fallbackGlyphInfo = new CharInfo
        fallbackGlyphInfo.advance = fontSize * 0.5f
        fallbackGlyphInfo.width = 0f
        fallbackGlyphInfo.height = 0f
        fallbackGlyphInfo.isColor = false
        return
      }

      val bitmapPtr   = glyphBitmap._1
      val glyphWidth  = glyphBitmap._2
      val glyphHeight = glyphBitmap._3
      val glyphPitch  = glyphBitmap._4

      val advance = glyphMetrics._1
      val width   = glyphMetrics._2
      val height  = glyphMetrics._3
      val xoffset = glyphMetrics._4
      val yoffset = glyphMetrics._5

      if (glyphWidth <= 0 || glyphHeight <= 0) {
        fallbackGlyphInfo = new CharInfo
        fallbackGlyphInfo.advance = fontSize * 0.5f
        fallbackGlyphInfo.width = 0f
        fallbackGlyphInfo.height = 0f
        fallbackGlyphInfo.isColor = false
        fthb_free_glyph_bitmap(glyphBitmap)
        return
      }

      if (atlasX + glyphWidth + 2 > bitmapWidth) {
        atlasX = 0
        atlasY += atlasRowHeight + 2
        atlasRowHeight = 0
      }

      val glyphX = atlasX + 1
      val glyphY = atlasY + 1

      var row = 0
      while (row < glyphHeight) {
        val srcOffset = row * glyphPitch
        val dstOffset = (glyphY + row) * bitmapWidth + glyphX
        var col       = 0
        while (col < glyphWidth) {
          bitmap(dstOffset + col) = bitmapPtr(srcOffset + col)
          col += 1
        }
        row += 1
      }

      atlasX += glyphWidth + 2
      if (glyphHeight > atlasRowHeight) {
        atlasRowHeight = glyphHeight
      }

      fallbackGlyphInfo = new CharInfo
      fallbackGlyphInfo.advance = advance
      fallbackGlyphInfo.xoffset = xoffset
      fallbackGlyphInfo.yoffset = yoffset
      fallbackGlyphInfo.width = width
      fallbackGlyphInfo.height = height
      fallbackGlyphInfo.isColor = false

      fallbackGlyphInfo.s0 = glyphX.toFloat / bitmapWidth
      fallbackGlyphInfo.t0 = glyphY.toFloat / bitmapHeight
      fallbackGlyphInfo.s1 = (glyphX + glyphWidth).toFloat / bitmapWidth
      fallbackGlyphInfo.t1 = (glyphY + glyphHeight).toFloat / bitmapHeight

      fallbackGlyphInfo.x0 = xoffset
      fallbackGlyphInfo.y0 = yoffset
      fallbackGlyphInfo.x1 = xoffset + width
      fallbackGlyphInfo.y1 = yoffset + height

      fthb_free_glyph_bitmap(glyphBitmap)
    }
  }

  private def packAsciiRange(): Unit = {
    var cp = 32
    while (cp < 127) {
      getCharInfo(cp)
      cp += 1
    }

    cp = 0x00c0
    while (cp <= 0x00ff) {
      getCharInfo(cp)
      cp += 1
    }

    cp = 0x0100
    while (cp <= 0x017f) {
      getCharInfo(cp)
      cp += 1
    }

    val commonSymbols = Seq(
      0x2013, 0x2014, 0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2026, 0x00a0, 0x00ad
    )
    commonSymbols.foreach(getCharInfo)
  }

  private def findFontForGlyph(codepoint: Int): Option[(Font, Int)] = {
    if (primaryFont.hasGlyph(codepoint)) {
      return Some((primaryFont, -1))
    }

    var i = 0
    while (i < fallbackFonts.length) {
      val font = fallbackFonts(i)
      if (font.hasGlyph(codepoint)) {
        return Some((font, i))
      }
      i += 1
    }

    None
  }

  private def packGlyph(codepoint: Int): Option[CharInfo] = {
    findFontForGlyph(codepoint) match {
      case None =>
        println(
          s"Warning: No font has glyph for codepoint U+${codepoint.toHexString} (${Character.toChars(codepoint).mkString})"
        )
        return None
      case Some((font, fontIdx)) =>
        glyphToFontMap(codepoint) = fontIdx

        Zone {
          val glyphBitmap  = alloc[GlyphBitmap]()
          val glyphMetrics = alloc[GlyphMetrics]()

          if (!font.renderGlyph(codepoint, glyphBitmap, glyphMetrics)) {
            println(s"Warning: Failed to render glyph U+${codepoint.toHexString}")
            return None
          }

          val bitmapPtr   = glyphBitmap._1
          val glyphWidth  = glyphBitmap._2
          val glyphHeight = glyphBitmap._3
          val glyphPitch  = glyphBitmap._4
          val isColor     = glyphBitmap._5 != 0

          var advance = glyphMetrics._1
          var width   = glyphMetrics._2
          var height  = glyphMetrics._3
          var xoffset = glyphMetrics._4
          var yoffset = glyphMetrics._5

          if (isColor && glyphWidth > 0 && glyphHeight > 0) {
            val emojiScale = fontSize / glyphHeight.toFloat
            width = glyphWidth * emojiScale
            height = glyphHeight * emojiScale
            xoffset = xoffset * emojiScale
            yoffset = yoffset * emojiScale
            advance = glyphWidth * emojiScale
          }

          if (glyphWidth <= 0 || glyphHeight <= 0) {
            val charInfo = new CharInfo
            charInfo.advance = advance
            charInfo.width = 0f
            charInfo.height = 0f
            charInfo.isColor = false
            fthb_free_glyph_bitmap(glyphBitmap)
            return Some(charInfo)
          }

          val (targetBitmap, targetAtlasX, targetAtlasY, targetRowHeight, _, targetWidth, targetHeight) =
            if (isColor) {
              (
                colorBitmap,
                colorAtlasX,
                colorAtlasY,
                colorAtlasRowHeight,
                colorTexture,
                colorBitmapWidth,
                colorBitmapHeight
              )
            } else {
              (bitmap, atlasX, atlasY, atlasRowHeight, texture, bitmapWidth, bitmapHeight)
            }

          var currentX         = targetAtlasX
          var currentY         = targetAtlasY
          var currentRowHeight = targetRowHeight

          if (
            currentY + glyphHeight + 2 > targetHeight ||
            (currentX + glyphWidth + 2 > targetWidth && currentY + currentRowHeight + glyphHeight + 4 > targetHeight)
          ) {
            if (isColor && colorEmojiLRU.nonEmpty) {
              evictLeastRecentlyUsedEmoji()
              fthb_free_glyph_bitmap(glyphBitmap)
              return packGlyph(codepoint)
            } else {
              println(s"Warning: Font atlas full, cannot pack glyph U+${codepoint.toHexString}")
              fthb_free_glyph_bitmap(glyphBitmap)
              return None
            }
          }

          if (currentX + glyphWidth + 2 > targetWidth) {
            currentX = 0
            currentY += currentRowHeight + 2
            currentRowHeight = 0
          }

          val glyphX = currentX + 1
          val glyphY = currentY + 1

          val bytesPerPixel = if (isColor) 4 else 1
          var row           = 0
          while (row < glyphHeight) {
            val srcOffset = row * glyphPitch
            val dstOffset = ((glyphY + row) * targetWidth + glyphX) * bytesPerPixel
            var col       = 0
            while (col < glyphWidth * bytesPerPixel) {
              targetBitmap(dstOffset + col) = bitmapPtr(srcOffset + col)
              col += 1
            }
            row += 1
          }

          if (isInitialized) {
            val dataSize = glyphHeight * glyphPitch
            val dataCopy = malloc(dataSize.toUSize).asInstanceOf[Ptr[Byte]]
            var i        = 0
            while (i < dataSize) {
              dataCopy(i) = bitmapPtr(i)
              i += 1
            }
            pendingUpdates += PendingUpdate(glyphX, glyphY, glyphWidth, glyphHeight, dataCopy, glyphPitch, isColor)
          }

          if (isColor) {
            colorAtlasX = currentX + glyphWidth + 2
            colorAtlasY = currentY
            if (glyphHeight > colorAtlasRowHeight) {
              colorAtlasRowHeight = glyphHeight
            }
          } else {
            atlasX = currentX + glyphWidth + 2
            atlasY = currentY
            if (glyphHeight > atlasRowHeight) {
              atlasRowHeight = glyphHeight
            }
          }

          val charInfo = new CharInfo
          charInfo.advance = advance
          charInfo.xoffset = xoffset
          charInfo.yoffset = yoffset
          charInfo.width = width
          charInfo.height = height
          charInfo.isColor = isColor
          charInfo.lastAccess = accessCounter
          accessCounter += 1

          charInfo.s0 = glyphX.toFloat / targetWidth
          charInfo.t0 = glyphY.toFloat / targetHeight
          charInfo.s1 = (glyphX + glyphWidth).toFloat / targetWidth
          charInfo.t1 = (glyphY + glyphHeight).toFloat / targetHeight

          charInfo.x0 = xoffset
          charInfo.y0 = yoffset
          charInfo.x1 = xoffset + width
          charInfo.y1 = yoffset + height

          if (isColor) {
            colorEmojiLRU(codepoint) = accessCounter
          }

          fthb_free_glyph_bitmap(glyphBitmap)
          Some(charInfo)
        }
    }
  }

  private def evictLeastRecentlyUsedEmoji(): Unit = {
    if (colorEmojiLRU.isEmpty) return

    val lruCodepoint = colorEmojiLRU.minBy(_._2)._1

    charInfoCache.remove(lruCodepoint)
    colorEmojiLRU.remove(lruCodepoint)
    glyphToFontMap.remove(lruCodepoint)

    if (colorEmojiLRU.size < 230) {
      val remainingEmojis = colorEmojiLRU.keys.toSeq.sortBy(colorEmojiLRU(_))

      memset(colorBitmap, 0, (colorBitmapWidth * colorBitmapHeight * 4).toUSize)
      colorAtlasX = 0
      colorAtlasY = 0
      colorAtlasRowHeight = 0

      remainingEmojis.foreach { cp =>
        charInfoCache.remove(cp)
      }
      colorEmojiLRU.clear()

      if (isInitialized) {
        colorTexture.cleanup()
        colorTexture = new Texture(colorBitmapWidth, colorBitmapHeight, colorBitmap, true)
      }
    }
  }

  def getCharInfo(codepoint: Int): CharInfo = {
    val charInfo = charInfoCache.getOrElseUpdate(
      codepoint, {
        packGlyph(codepoint).getOrElse {
          fallbackGlyphInfo
        }
      }
    )

    if (charInfo.isColor && colorEmojiLRU.contains(codepoint)) {
      accessCounter += 1
      charInfo.lastAccess = accessCounter
      colorEmojiLRU(codepoint) = accessCounter
    }

    charInfo
  }

  def flushPendingUpdates(): Unit = {
    if (pendingUpdates.isEmpty) return

    pendingUpdates.foreach { update =>
      val targetTexture = if (update.isColor) colorTexture else texture
      targetTexture.updateRegion(
        update.glyphX,
        update.glyphY,
        update.width,
        update.height,
        update.data,
        update.pitch,
        update.isColor
      )
      free(update.data)
    }
    pendingUpdates.clear()
  }

  def getTexture(): Texture      = texture
  def getColorTexture(): Texture = colorTexture

  def cleanup(): Unit = {
    if (texture != null) texture.cleanup()
    if (colorTexture != null) colorTexture.cleanup()
    if (bitmap != null) free(bitmap)
    if (colorBitmap != null) free(colorBitmap)
  }
}
