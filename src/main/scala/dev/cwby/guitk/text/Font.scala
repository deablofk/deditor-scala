package dev.cwby.guitk.text

import scala.scalanative.libc.stdio._
import scala.scalanative.libc.stdlib._
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

object Font {
  def findSystemFallbackFonts(): Seq[String] = {
    val candidates = Seq(
      "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf",
      "/usr/share/fonts/noto/NotoColorEmoji.ttf",
      "/usr/share/fonts/google-noto-emoji/NotoColorEmoji.ttf",
      "/usr/local/share/fonts/NotoColorEmoji.ttf",
      "/usr/share/fonts/truetype/color-emoji/NotoColorEmoji.ttf",
      "/System/Library/Fonts/Apple Color Emoji.ttc",
      "C:\\Windows\\Fonts\\seguiemj.ttf",
      "/usr/share/fonts/truetype/ancient-scripts/Symbola.ttf",
      "/usr/share/fonts/truetype/noto/NotoEmoji-Regular.ttf",
      "/usr/share/fonts/noto/NotoEmoji-Regular.ttf",
      "/usr/local/share/fonts/NotoEmoji-Regular.ttf",
      "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
      "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
      "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
      "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
      "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
      "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
      "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
      "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
      "/System/Library/Fonts/Supplemental/Symbol.ttf",
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

  def toCString(str: String): Ptr[CChar] = {
    val ptr = malloc(str.length + 1).asInstanceOf[Ptr[CChar]]
    var i   = 0
    while (i < str.length) {
      ptr(i) = str.charAt(i).toByte
      i += 1
    }
    ptr(str.length) = 0.toByte
    ptr
  }
}

final class Font(path: String, val size: Float) {
  import CFreeTypeHarfBuzz._

  private val handle: FTHBFont = createHandle(path)

  val ascent: Float  = extractAscent()
  val descent: Float = extractDescent()
  val lineGap: Float = extractLineGap()

  private def createHandle(fontPath: String): FTHBFont = {
    val pathCStr = Font.toCString(fontPath)
    val font     = fthb_create_font(pathCStr, size)
    free(pathCStr.asInstanceOf[Ptr[Byte]])
    if (font == null) {
      throw new RuntimeException(s"Failed to create font: $fontPath")
    }
    font
  }

  private def extractAscent(): Float = {
    Zone {
      val pa = alloc[Float]()
      val pd = alloc[Float]()
      val pl = alloc[Float]()
      fthb_get_font_metrics(handle, pa, pd, pl)
      !pa
    }
  }

  private def extractDescent(): Float = {
    Zone {
      val pa = alloc[Float]()
      val pd = alloc[Float]()
      val pl = alloc[Float]()
      fthb_get_font_metrics(handle, pa, pd, pl)
      !pd
    }
  }

  private def extractLineGap(): Float = {
    Zone {
      val pa = alloc[Float]()
      val pd = alloc[Float]()
      val pl = alloc[Float]()
      fthb_get_font_metrics(handle, pa, pd, pl)
      !pl
    }
  }

  def hasGlyph(codepoint: Int): Boolean = {
    fthb_has_glyph(handle, codepoint.toUInt) != 0
  }

  def renderGlyph(codepoint: Int, bitmap: Ptr[GlyphBitmap], metrics: Ptr[GlyphMetrics]): Boolean = {
    fthb_render_glyph(handle, codepoint.toUInt, bitmap, metrics) != 0
  }

  def renderGlyphByIndex(index: Int, bitmap: Ptr[GlyphBitmap], metrics: Ptr[GlyphMetrics]): Boolean = {
    fthb_render_glyph_by_index(handle, index.toUInt, bitmap, metrics) != 0
  }

  def getLineHeight(): Float = ascent - descent + lineGap

  def cleanup(): Unit = {
    if (handle != null) fthb_destroy_font(handle)
  }
}
