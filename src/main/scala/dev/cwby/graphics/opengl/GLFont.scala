package dev.cwby.graphics.opengl

import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@extern
object CFreeTypeHarfBuzz {
  type FTHBFont = Ptr[Byte]

  type GlyphMetrics = CStruct8[Float, Float, Float, Float, Float, Float, Float, CUnsignedInt]
  type GlyphBitmap  = CStruct5[Ptr[Byte], Int, Int, Int, Int]
  type ShapedGlyph  = CStruct6[CUnsignedInt, CUnsignedInt, Float, Float, Float, Float]

  def fthb_create_font(font_path: Ptr[CChar], pixel_size: Float): FTHBFont                             = extern
  def fthb_destroy_font(font: FTHBFont): Unit                                                          = extern
  def fthb_has_glyph(font: FTHBFont, codepoint: CUnsignedInt): Int                                     = extern
  def fthb_get_glyph_metrics(font: FTHBFont, codepoint: CUnsignedInt, metrics: Ptr[GlyphMetrics]): Int = extern
  def fthb_render_glyph(
      font: FTHBFont,
      codepoint: CUnsignedInt,
      out_bitmap: Ptr[GlyphBitmap],
      out_metrics: Ptr[GlyphMetrics]
  ): Int                                                     = extern
  def fthb_free_glyph_bitmap(bitmap: Ptr[GlyphBitmap]): Unit = extern
  def fthb_get_font_metrics(font: FTHBFont, ascent: Ptr[Float], descent: Ptr[Float], line_gap: Ptr[Float]): Unit =
    extern
  def fthb_shape_text(
      font: FTHBFont,
      text: Ptr[CChar],
      text_length: Int,
      language: Ptr[CChar],
      out_glyphs: Ptr[ShapedGlyph],
      max_glyphs: Int
  ): Int = extern
  def fthb_render_glyph_by_index(
      font: FTHBFont,
      glyph_index: CUnsignedInt,
      out_bitmap: Ptr[GlyphBitmap],
      out_metrics: Ptr[GlyphMetrics]
  ): Int = extern
}

/* CharInfo mirrors the Java structure */
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

object GLFont {
  import scala.scalanative.libc.stdio._
  import scala.scalanative.libc.stdlib._
  import scala.scalanative.unsafe._

  def findSystemFallbackFonts(): Seq[String] = {
    val candidates = Seq(
      // Color emoji fonts - HIGHEST PRIORITY
      "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf",
      "/usr/share/fonts/noto/NotoColorEmoji.ttf",
      "/usr/share/fonts/google-noto-emoji/NotoColorEmoji.ttf",
      "/usr/local/share/fonts/NotoColorEmoji.ttf",
      "/usr/share/fonts/truetype/color-emoji/NotoColorEmoji.ttf",
      "/System/Library/Fonts/Apple Color Emoji.ttc",
      "C:\\Windows\\Fonts\\seguiemj.ttf",
      // Monochrome emoji fonts (fallback)
      "/usr/share/fonts/truetype/ancient-scripts/Symbola.ttf",
      "/usr/share/fonts/truetype/noto/NotoEmoji-Regular.ttf",
      "/usr/share/fonts/noto/NotoEmoji-Regular.ttf",
      "/usr/local/share/fonts/NotoEmoji-Regular.ttf",
      // Standard fallback fonts with decent Unicode coverage
      "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
      "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
      // DejaVu last - has poor emoji placeholders
      "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
      "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
      "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
      "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
      // CJK support
      "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
      // macOS fonts
      "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
      "/System/Library/Fonts/Supplemental/Symbol.ttf",
      // Windows fonts
      "C:\\Windows\\Fonts\\seguisym.ttf",
      "C:\\Windows\\Fonts\\arial.ttf",
      "C:\\Windows\\Fonts\\arialuni.ttf"
    )

    candidates.filter(fontExists)
  }

  private def fontExists(path: String): Boolean = {
    val pathCStr = malloc(path.length + 1).asInstanceOf[Ptr[Byte]]
    val modeCStr = malloc(3).asInstanceOf[Ptr[Byte]]

    var i = 0
    while (i < path.length) {
      pathCStr(i) = path.charAt(i).toByte
      i += 1
    }
    pathCStr(path.length) = 0.toByte

    modeCStr(0) = 'r'.toByte
    modeCStr(1) = 'b'.toByte
    modeCStr(2) = 0.toByte

    val file = fopen(pathCStr, modeCStr)
    free(pathCStr)
    free(modeCStr)

    if (file == null) {
      false
    } else {
      fclose(file)
      true
    }
  }
}

class GLFont(fontPath: String, val fontSize: Float, fallbackPaths: Seq[String] = Seq.empty) {
  import CFreeTypeHarfBuzz._

  private val bitmapWidth: Int =
    if (fontSize <= 25) 1024 else if (fontSize <= 50) 2048 else if (fontSize <= 100) 4096 else 8192
  private val bitmapHeight: Int = bitmapWidth
  // Color emojis are 136x128px each - 2048x2048 fits ~240 emojis (16MB RAM + 16MB VRAM)
  private val colorBitmapWidth: Int  = if (fontSize <= 25) 2048 else if (fontSize <= 50) 4096 else 8192
  private val colorBitmapHeight: Int = colorBitmapWidth

  private val primaryFont: FTHBFont = createFontHandle(fontPath)

  private val fallbackFonts: Seq[(FTHBFont, String)] = fallbackPaths.flatMap { path =>
    try {
      val font = createFontHandle(path)
      if (font != null) {
        Some((font, path))
      } else {
        println(s"Warning: Failed to initialize fallback font: $path")
        None
      }
    } catch {
      case e: Exception =>
        println(s"Warning: Could not load fallback font $path: ${e.getMessage}")
        None
    }
  }

  private val charInfoCache       = scala.collection.mutable.Map[Int, CharInfo]()
  private val glyphToFontMap      = scala.collection.mutable.Map[Int, Int]()
  private val colorEmojiLRU       = scala.collection.mutable.LinkedHashMap[Int, Long]()
  private var accessCounter: Long = 0L

  // Pending texture updates to avoid mid-frame updates that cause blinks
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
  private var ascent: Float          = 0f
  private var descent: Float         = 0f
  private var lineGap: Float         = 0f
  private var scale: Float           = 0f

  private var atlasX: Int                 = 0
  private var atlasY: Int                 = 0
  private var atlasRowHeight: Int         = 0
  private var colorAtlasX: Int            = 0
  private var colorAtlasY: Int            = 0
  private var colorAtlasRowHeight: Int    = 0
  private var isInitialized: Boolean      = false
  private var fallbackGlyphInfo: CharInfo = _

  init()

  private def createFontHandle(path: String): FTHBFont = {
    val pathCStr = toCString(path)
    val font     = fthb_create_font(pathCStr, fontSize)
    free(pathCStr.asInstanceOf[Ptr[Byte]])
    if (font == null) {
      throw new RuntimeException(s"Failed to create font: $path")
    }
    font
  }

  private def toCString(str: String): Ptr[CChar] = {
    val ptr = malloc(str.length + 1).asInstanceOf[Ptr[CChar]]
    var i   = 0
    while (i < str.length) {
      ptr(i) = str.charAt(i).toByte
      i += 1
    }
    ptr(str.length) = 0.toByte
    ptr
  }

  private def init(): Unit = {
    Zone {
      val pa = alloc[Float]()
      val pd = alloc[Float]()
      val pl = alloc[Float]()
      fthb_get_font_metrics(primaryFont, pa, pd, pl)
      ascent = !pa
      descent = !pd
      lineGap = !pl
      scale = 1.0f
    }

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
    // Load the .notdef glyph (tofu) from the primary font - usually glyph index 0
    Zone {
      val glyphBitmap  = alloc[GlyphBitmap]()
      val glyphMetrics = alloc[GlyphMetrics]()

      // Render glyph index 0 (.notdef)
      if (fthb_render_glyph_by_index(primaryFont, 0.toUInt, glyphBitmap, glyphMetrics) == 0) {
        // If .notdef fails, create empty fallback
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
      glyphBitmap._5 != 0

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

      // Copy glyph bitmap to atlas
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
      atlasY = atlasY
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
    // Pre-pack ASCII 32-126
    var cp = 32
    while (cp < 127) {
      getCharInfo(cp)
      cp += 1
    }

    // Pre-pack extended Latin (accented characters): À-ÿ (U+00C0-U+00FF)
    cp = 0x00c0
    while (cp <= 0x00ff) {
      getCharInfo(cp)
      cp += 1
    }

    // Pre-pack Latin Extended-A: Ā-ſ (U+0100-U+017F)
    cp = 0x0100
    while (cp <= 0x017f) {
      getCharInfo(cp)
      cp += 1
    }

    // Pre-pack common punctuation and symbols
    val commonSymbols = Seq(
      0x2013, 0x2014,                 // en dash, em dash
      0x2018, 0x2019, 0x201c, 0x201d, // smart quotes
      0x2022, 0x2026,                 // bullet, ellipsis
      0x00a0, 0x00ad                  // non-breaking space, soft hyphen
    )
    commonSymbols.foreach(getCharInfo)
  }

  private def findFontForGlyph(codepoint: Int): (FTHBFont, Int, String) = {
    if (fthb_has_glyph(primaryFont, codepoint.toUInt) != 0) {
      return (primaryFont, -1, "primary")
    }

    var i = 0
    while (i < fallbackFonts.length) {
      val (font, path) = fallbackFonts(i)
      if (fthb_has_glyph(font, codepoint.toUInt) != 0) {
        return (font, i, path)
      }
      i += 1
    }

    (null, -2, "none")
  }

  private def packGlyph(codepoint: Int): Option[CharInfo] = {
    val (font, fontIdx, _) = findFontForGlyph(codepoint)
    if (font == null) {
      println(
        s"Warning: No font has glyph for codepoint U+${codepoint.toHexString} (${Character.toChars(codepoint).mkString})"
      )
      return None
    }

    glyphToFontMap(codepoint) = fontIdx

    Zone {
      val glyphBitmap  = alloc[GlyphBitmap]()
      val glyphMetrics = alloc[GlyphMetrics]()

      if (fthb_render_glyph(font, codepoint.toUInt, glyphBitmap, glyphMetrics) == 0) {
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

      // Scale down color emoji metrics to match text font size
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
        // Atlas is full - if this is a color emoji, try LRU eviction
        if (isColor && colorEmojiLRU.nonEmpty) {
          evictLeastRecentlyUsedEmoji()
          fthb_free_glyph_bitmap(glyphBitmap)
          // Retry packing after eviction
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
        // Copy bitmap data to buffer to avoid it being freed before texture update
        val dataSize = glyphHeight * glyphPitch
        val dataCopy = malloc(dataSize.toUSize).asInstanceOf[Ptr[Byte]]
        var i        = 0
        while (i < dataSize) {
          dataCopy(i) = bitmapPtr(i)
          i += 1
        }
        // Queue update for later (after frame completes)
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

      // Track color emojis in LRU cache
      if (isColor) {
        colorEmojiLRU(codepoint) = accessCounter
      }

      fthb_free_glyph_bitmap(glyphBitmap)
      Some(charInfo)
    }
  }

  private def evictLeastRecentlyUsedEmoji(): Unit = {
    if (colorEmojiLRU.isEmpty) return

    // Find the least recently used emoji (oldest access time)
    val lruCodepoint = colorEmojiLRU.minBy(_._2)._1

    // Remove from cache and LRU tracker
    charInfoCache.remove(lruCodepoint)
    colorEmojiLRU.remove(lruCodepoint)
    glyphToFontMap.remove(lruCodepoint)

    // If we've evicted enough emojis (>10), reset the color atlas to repack efficiently
    if (colorEmojiLRU.size < 230) {
      // Clear the entire color atlas and repack remaining emojis
      val remainingEmojis = colorEmojiLRU.keys.toSeq.sortBy(colorEmojiLRU(_))

      // Clear color atlas
      memset(colorBitmap, 0, (colorBitmapWidth * colorBitmapHeight * 4).toUSize)
      colorAtlasX = 0
      colorAtlasY = 0
      colorAtlasRowHeight = 0

      // Remove all color emoji from cache to force repack
      remainingEmojis.foreach { cp =>
        charInfoCache.remove(cp)
      }
      colorEmojiLRU.clear()

      // Update texture with cleared atlas
      if (isInitialized) {
        colorTexture.cleanup()
        colorTexture = new Texture(colorBitmapWidth, colorBitmapHeight, colorBitmap, true)
      }
    }
  }

  def getCharInfo(codepoint: Int): CharInfo = {
    val charInfo = charInfoCache.getOrElseUpdate(
      codepoint, {
        // Try to pack the glyph dynamically
        packGlyph(codepoint).getOrElse {
          // Return the fallback square glyph for missing characters
          fallbackGlyphInfo
        }
      }
    )

    // Update LRU access time for color emojis
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
      // Free the copied data
      free(update.data)
    }
    pendingUpdates.clear()
  }

  def measureText(text: String): Float = {
    var width = 0f
    var i     = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val ci = getCharInfo(cp)
      width += ci.advance
      i += Character.charCount(cp)
    }
    width
  }

  def getTexture(): Texture      = texture
  def getColorTexture(): Texture = colorTexture
  def getLineHeight(): Float     = ascent - descent + lineGap
  def getAscent(): Float         = ascent
  def getDescent(): Float        = descent
  def getFontSize(): Float       = fontSize

  def cleanup(): Unit = {
    if (texture != null) texture.cleanup()
    if (colorTexture != null) colorTexture.cleanup()
    if (bitmap != null) free(bitmap)
    if (colorBitmap != null) free(colorBitmap)
    if (primaryFont != null) fthb_destroy_font(primaryFont)
    fallbackFonts.foreach { case (font, _) =>
      if (font != null) fthb_destroy_font(font)
    }
  }
}
