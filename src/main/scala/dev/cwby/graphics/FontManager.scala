package dev.cwby.graphics

import dev.cwby.config.FontConfig
import dev.cwby.getConfig
import dev.cwby.graphics.opengl.GLFont

import java.io.File
import java.io.IOException

object FontManager {
  private val fontCfg: FontConfig = getConfig.font
  private var defaultFont: GLFont = _
  private var lineHeight: Float   = _
  private var avgWidth: Float     = _

  try {
    initializeFont()
  } catch {
    case e: IOException =>
      throw new RuntimeException("Failed to initialize font", e)
  }

  @throws[IOException]
  private def initializeFont(): Unit = {
    // Try to load the configured font, fallback to system fonts
    val fontPath      = findFontPath(fontCfg.family)
    val fallbackFonts = GLFont.findSystemFallbackFonts()
    defaultFont = new GLFont(fontPath, fontCfg.size, fallbackFonts)
    lineHeight = defaultFont.getLineHeight()
    avgWidth = defaultFont.measureText("M") // Use 'M' as average width reference
  }

  private def findFontPath(fontFamily: String): String = {
    val possiblePaths = Array(
      "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
      "/usr/share/fonts/TTF/DejaVuSansMono.ttf",
      "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
      "/usr/share/fonts/liberation/LiberationMono-Regular.ttf",
      "/usr/share/fonts/truetype/ubuntu/UbuntuMono-R.ttf",
      "/usr/share/fonts/ubuntu/UbuntuMono-R.ttf",
      "/usr/share/fonts/truetype/noto/NotoMono-Regular.ttf",
      "/usr/share/fonts/noto/NotoMono-Regular.ttf",
      "/System/Library/Fonts/Menlo.ttc",
      "C:\\Windows\\Fonts\\consola.ttf"
    )

    possiblePaths
      .find(path => new File(path).exists())
      .getOrElse("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf")
  }

  def getLineHeight(): Float = {
    lineHeight
  }

  def getAvgWidth(): Float = {
    avgWidth
  }

  def getDefaultFont(): GLFont = {
    defaultFont
  }

  def increaseFontSize(sizeIncrease: Int): Unit = {
    fontCfg.size += sizeIncrease
    try {
      // Clean up old font
      if (defaultFont != null) {
        defaultFont.cleanup()
      }
      // Reinitialize with new size
      initializeFont()
    } catch {
      case e: IOException =>
        println("Failed to update font size: " + e.getMessage())
    }
  }

  def measureText(text: String): Float = {
    defaultFont.measureText(text)
  }
}
