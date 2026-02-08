package dev.cwby.guitk.text

import dev.cwby.guitk.renderer.Texture

final class TextShaper(fontPath: String, val fontSize: Float, fallbackPaths: Seq[String] = Seq.empty) {
  private val primaryFont: Font = new Font(fontPath, fontSize)

  private val fallbackFonts: Seq[Font] = fallbackPaths.flatMap { path =>
    try {
      val font = new Font(path, fontSize)
      Some(font)
    } catch {
      case e: Exception =>
        println(s"Warning: Could not load fallback font $path: ${e.getMessage}")
        None
    }
  }

  private val glyphCache: GlyphCache = new GlyphCache(fontSize, primaryFont, fallbackFonts)

  def getCharInfo(codepoint: Int): CharInfo = glyphCache.getCharInfo(codepoint)

  def flushPendingUpdates(): Unit = glyphCache.flushPendingUpdates()

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

  def getTexture(): Texture      = glyphCache.getTexture()
  def getColorTexture(): Texture = glyphCache.getColorTexture()
  def getLineHeight(): Float     = primaryFont.getLineHeight()
  def getAscent(): Float         = primaryFont.ascent
  def getDescent(): Float        = primaryFont.descent
  def getFontSize(): Float       = fontSize

  def cleanup(): Unit = {
    glyphCache.cleanup()
    primaryFont.cleanup()
    fallbackFonts.foreach(_.cleanup())
  }
}
