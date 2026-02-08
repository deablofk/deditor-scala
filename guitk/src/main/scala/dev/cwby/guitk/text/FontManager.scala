package dev.cwby.guitk.text

import java.io.File
import java.io.IOException

object FontManager {
  private var defaultFont: TextShaper = _
  private var lineHeight: Float       = _
  private var avgWidth: Float         = _
  private var currentFontFamily: String = _
  private var currentFontSize: Int      = 0

  def initialize(fontFamily: String, fontSize: Int): Unit = {
    currentFontFamily = fontFamily
    currentFontSize = fontSize
    try {
      initializeFont()
    } catch {
      case e: IOException =>
        throw new RuntimeException("Failed to initialize font", e)
    }
  }

  @throws[IOException]
  private def initializeFont(): Unit = {
    val fontPath      = findFontPath(currentFontFamily)
    val fallbackFonts = Font.findSystemFallbackFonts()
    defaultFont = new TextShaper(fontPath, currentFontSize, fallbackFonts)
    lineHeight = defaultFont.getLineHeight()
    avgWidth = defaultFont.measureText("M")
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

  def getDefaultFont(): TextShaper = {
    defaultFont
  }

  def increaseFontSize(sizeIncrease: Int): Unit = {
    currentFontSize += sizeIncrease
    try {
      if (defaultFont != null) {
        defaultFont.cleanup()
      }
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
