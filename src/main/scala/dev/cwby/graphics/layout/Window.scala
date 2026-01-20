package dev.cwby.graphics.layout

import dev.cwby.editor.TextBuffer
import dev.cwby.graphics.FontManager
import dev.cwby.graphics.layout.component.IComponent

class Window(
    var title: String,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var component: IComponent = null,
    var visible: Boolean = true,
    var offsetX: Int = 0,
    var offsetY: Int = 0,
    private var visibleLinesCache: Int = 0
):

  // behavior (composition)
  def open(): Unit = {}

  def close(): Unit = onClose()

  def onTrigger(): Unit = {}

  def onClose(): Unit = {}

  def hide(): Unit = this.visible = false

  def isVisible: Boolean = visible

  def getComponent: IComponent = component

  def setComponent(component: IComponent): Unit = this.component = component

  def getVisibleLines: Int = {
    val calculated = (height / FontManager.getLineHeight()).toInt

    if calculated > 0 then calculated
    else visibleLinesCache
  }

  def setVisibleLines(visibleLines: Int): Unit = {
    this.visibleLinesCache = visibleLines
  }

  def resetScroll(): Unit = {
    offsetX = 0
    offsetY = 0
  }

  def ensureCursorVisible(buffer: TextBuffer): Unit = {
    if buffer == null then return

    val visibleLines = getVisibleLines
    if visibleLines <= 0 then return

    val cursorY = buffer.cursorY

    if cursorY < offsetY then offsetY = cursorY
    else if cursorY >= offsetY + visibleLines then offsetY = cursorY - visibleLines + 1

    val maxOffsetY = Math.max(0, buffer.lines.length - visibleLines)
    offsetY = Math.min(Math.max(0, offsetY), maxOffsetY)
  }

  def ensureCursorVisibleHorizontal(buffer: TextBuffer): Unit = {
    if buffer == null then return

    val font        = FontManager.getDefaultFont()
    val lineHeight  = FontManager.getLineHeight()
    val usableWidth = (width - FontManager.getAvgWidth()).toInt

    if usableWidth <= 0 || lineHeight <= 0 then return

    if buffer.cursorY < 0 || buffer.cursorY >= buffer.lines.length then return

    val line    = buffer.lines(buffer.cursorY)
    val cursorX = Math.min(Math.max(0, buffer.cursorX), line.length())

    val tabSize    = 4
    val spaceWidth = font.measureText(" ")

    var xPx = 0.0f
    var i   = 0
    while i < cursorX && i < line.length() do
      val codePoint = line.toString.codePointAt(i)

      if codePoint == '\t' then
        val tabWidth = spaceWidth * tabSize
        xPx = ((xPx + tabWidth) / tabWidth).toInt * tabWidth
      else
        val glyph = new String(Character.toChars(codePoint))
        xPx += font.measureText(glyph)

      i += Character.charCount(codePoint)

    val cursorLeftPx  = xPx.toInt
    val cursorRightPx = (xPx + FontManager.getAvgWidth()).toInt
    val viewLeftPx    = offsetX
    val viewRightPx   = offsetX + usableWidth

    if cursorLeftPx < viewLeftPx then offsetX = cursorLeftPx
    else if cursorRightPx > viewRightPx then offsetX = cursorRightPx - usableWidth

    offsetX = Math.max(0, offsetX)
  }
