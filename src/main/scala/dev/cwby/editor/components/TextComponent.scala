package dev.cwby.editor.components

import dev.cwby.WindowManager
import dev.cwby.config.hexToInt
import dev.cwby.editor.core.TextBuffer
import dev.cwby.editor.core.TextInteractionMode
import dev.cwby.getBufferMode
import dev.cwby.getConfig
import dev.cwby.guitk.text.FontManager
import dev.cwby.guitk.components.{Window, IComponent}
import dev.cwby.guitk.renderer.Renderer2D
import dev.cwby.editor.input.GlobalKeyHandler
import dev.cwby.treesitter.SyntaxHighlighter

import scala.collection.mutable
import scala.compiletime.uninitialized


class TextComponent extends IComponent {

  private val textColor: Int               = getConfig.treesitter.getOrElse("default", 0xffffffff)
  private val cursorColorInt: Int          = hexToInt(getConfig.cursor.color)
  private val selectColorInt: Int          = hexToInt(getConfig.cursor.select)
  private val lineBackgroundColor: Int     = 0x66666666
  private val searchMatchColor: Int        = 0x66ffd54f
  private val searchCurrentMatchColor: Int = 0xaaffb300
  

  private var buffer: TextBuffer                                       = uninitialized
  private var renderWindow: Window[TextBuffer]                         = uninitialized
  private var cursorVisible: Boolean                                   = true
  private var lastBlinkTime: Long                                      = 0
  private var cachedStyles: mutable.Map[String, mutable.Map[Int, Int]] = mutable.Map.empty
  private val textBuilder                                              = StringBuilder(256)

  def setBuffer(buffer: TextBuffer): TextComponent = {
    this.buffer = buffer
    this
  }

  def getBuffer: TextBuffer = {
    buffer
  }

  def setRenderWindow(window: Window[TextBuffer]): TextComponent = {
    this.renderWindow = window
    this
  }

  private def drawHighlightedText(
      renderer: Renderer2D,
      text: String,
      x: Float,
      y: Float,
      width: Float,
      height: Float,
      offsetX: Int,
      styles: mutable.Map[Int, Int]
  ): Unit = {
    var drawX = x - offsetX
    textBuilder.setLength(0)
    var currentColor   = textColor
    val font           = FontManager.getDefaultFont()
    val baselineOffset = font.getAscent()
    val textY          = y + baselineOffset
    val tabSize        = 4
    val spaceWidth     = font.measureText(" ")

    var i = 0
    while (i < text.length) {
      val codePoint  = text.codePointAt(i)
      val color: Int = styles.getOrElse(i, textColor)

      if (codePoint == '\t') {
        if (textBuilder.nonEmpty) {
          renderer.drawText(textBuilder.toString(), drawX, textY, font, currentColor)
          drawX += font.measureText(textBuilder.toString())
          textBuilder.setLength(0)
        }

        val tabWidth = spaceWidth * tabSize
        drawX = ((drawX + tabWidth) / tabWidth).toInt * tabWidth
      } else {
        if (currentColor != color) {
          if (textBuilder.nonEmpty) {
            renderer.drawText(textBuilder.toString(), drawX, textY, font, currentColor)
            drawX += font.measureText(textBuilder.toString())
            textBuilder.setLength(0)
          }
          currentColor = color
        }
        textBuilder.append(new String(Character.toChars(codePoint)))
      }

      i += Character.charCount(codePoint)
    }

    if (textBuilder.nonEmpty) {
      renderer.drawText(textBuilder.toString(), drawX, textY, font, currentColor)
    }
  }

  private def drawCurrentLineBackground(renderer: Renderer2D, x: Float, y: Float, width: Float, lineHeight: Float): Unit = {
    if (renderWindow == null) {
      return
    }
    val currentLineY = (buffer.cursorY - renderWindow.offsetY) * lineHeight
    renderer.drawRect(x, y + currentLineY, width, lineHeight, lineBackgroundColor)
  }

  private def renderSearchHighlights(
      renderer: Renderer2D,
      bufferX: Float,
      bufferY: Float,
      width: Float,
      height: Float
  ): Unit = {
    if buffer == null || renderWindow == null then return

    val qLen = buffer.getSearchQueryLength()
    if qLen <= 0 then return

    val lineHeight   = FontManager.getLineHeight()
    val font         = FontManager.getDefaultFont()
    val visibleLines = Math.ceil(height / lineHeight).toInt
    val startY       = Math.max(0, renderWindow.offsetY)
    val endY         = Math.min(startY + visibleLines, buffer.lines.length)

    val current = buffer.getCurrentSearchMatch()

    var y = startY
    while y < endY do
      val starts = buffer.getSearchMatchStartsForLine(y)
      if starts.nonEmpty then
        val line  = buffer.lines(y)
        val baseY = bufferY + (y - renderWindow.offsetY) * lineHeight

        for startX <- starts do
          val clampedStart = Math.max(0, Math.min(startX, line.length()))
          val clampedEnd   = Math.max(clampedStart, Math.min(clampedStart + qLen, line.length()))

          var xOffset = -renderWindow.offsetX.toFloat
          var i       = 0
          while i < clampedStart && i < line.length() do
            val codePoint = line.toString.codePointAt(i)
            if codePoint == '\t' then
              val tabSize    = 4
              val spaceWidth = font.measureText(" ")
              val tabWidth   = spaceWidth * tabSize
              xOffset = ((xOffset + tabWidth) / tabWidth).toInt * tabWidth
            else
              val glyph = new String(Character.toChars(codePoint))
              xOffset += font.measureText(glyph)
            i += Character.charCount(codePoint)

          var matchWidth = 0.0f
          var j          = clampedStart
          while j < clampedEnd && j < line.length() do
            val codePoint = line.toString.codePointAt(j)
            if codePoint == '\t' then
              val tabSize    = 4
              val spaceWidth = font.measureText(" ")
              matchWidth += spaceWidth * tabSize
            else
              val glyph = new String(Character.toChars(codePoint))
              matchWidth += font.measureText(glyph)
            j += Character.charCount(codePoint)

          val isCurrent = current.exists(c => c._1 == y && c._2 == startX)
          val color     = if isCurrent then searchCurrentMatchColor else searchMatchColor
          if matchWidth > 0 then renderer.drawRect(bufferX + xOffset, baseY, matchWidth, lineHeight, color)
      y += 1
  }

  private def renderText(renderer: Renderer2D, x: Float, y: Float, width: Float, height: Float, offsetY: Int): Unit = {
    val lineHeight   = FontManager.getLineHeight()
    val font         = FontManager.getDefaultFont()
    val visibleLines = Math.ceil(height / lineHeight).toInt
    val endY         = Math.min(offsetY + visibleLines, buffer.lines.length)

    renderer.beginTextBatch(font)

    var i     = offsetY
    var count = 0
    while (i < endY) {
      val line    = buffer.lines(i)
      val lineStr = line.toString()

      val cacheKey = buffer.getFileType() + "\n" + lineStr

      val styles = cachedStyles.getOrElse(
        cacheKey, {
          try {
            val stylesMutable = SyntaxHighlighter.highlight(lineStr, buffer.getFileType())
            cachedStyles(cacheKey) = stylesMutable
            stylesMutable
          } catch {
            case _: Exception =>
              val simpleStyles = SyntaxHighlighter.getSimpleHighlighting(lineStr)
              cachedStyles(cacheKey) = simpleStyles
              simpleStyles
          }
        }
      )

      if (renderWindow == null) {
        return
      }
      drawHighlightedText(renderer, lineStr, x, y + count * lineHeight, width, height, renderWindow.offsetX, styles)
      i += 1
      count += 1
    }

    renderer.endTextBatch(font)
  }

  def invalidateCache(): Unit = {
    cachedStyles = mutable.Map.empty
  }

  private def renderCursor(renderer: Renderer2D, bufferX: Float, bufferY: Float): Unit = {
    val now = System.currentTimeMillis()

    if (now - GlobalKeyHandler.lastKeyPressTime >= 5000) {
      if (now - lastBlinkTime >= getConfig.cursor.blink) {
        cursorVisible = !cursorVisible
        lastBlinkTime = now
      }
    } else {
      cursorVisible = true
    }

    if (renderWindow == null || buffer == null) {
      return
    }

    buffer.synchronized {
      val cursorX = buffer.cursorX
      val cursorY = buffer.cursorY

      if (cursorVisible && cursorY >= 0 && cursorY < buffer.lines.length) {
        var x          = -renderWindow.offsetX.toFloat
        val line       = buffer.lines(cursorY)
        val tabSize    = 4
        val spaceWidth = FontManager.getDefaultFont().measureText(" ")
        var i          = 0
        while (i < cursorX && i < line.length()) {
          val codePoint = line.toString.codePointAt(i)
          if (codePoint == '\t') {
            val tabWidth = spaceWidth * tabSize
            x = ((x + tabWidth) / tabWidth).toInt * tabWidth
          } else {
            val font  = FontManager.getDefaultFont()
            val glyph = new String(Character.toChars(codePoint))
            x += font.measureText(glyph)
          }
          i += Character.charCount(codePoint)
        }

        val y = (cursorY - renderWindow.offsetY) * FontManager.getLineHeight()
        if (getBufferMode == TextInteractionMode.NAVIGATION || getBufferMode == TextInteractionMode.SELECT) {
          renderer.drawRect(
            bufferX + x,
            bufferY + y,
            FontManager.getAvgWidth(),
            FontManager.getLineHeight(),
            cursorColorInt
          )
        } else if (getBufferMode == TextInteractionMode.INSERT) {
          renderer.drawRect(bufferX + x, bufferY + y, 2, FontManager.getLineHeight(), cursorColorInt)
        }
      }
    }
  }

  private def renderSelection(renderer: Renderer2D, bufferX: Float, bufferY: Float): Unit = {
    buffer.synchronized {
      val cursorX = buffer.cursorX
      val cursorY = buffer.cursorY
      val selectX = GlobalKeyHandler.startVisualX
      val selectY = GlobalKeyHandler.startVisualY
      if (getBufferMode == TextInteractionMode.SELECT) {
        if (renderWindow == null) {
          return
        }
        val startLine = Math.min(cursorY, selectY)
        val endLine   = Math.max(cursorY, selectY)
        val startChar = if cursorY < selectY then cursorX else selectX
        val endChar   = if cursorY > selectY then cursorX else selectX

        val safeStartLine = Math.max(0, startLine)
        val safeEndLine   = Math.min(endLine, buffer.lines.length - 1)
        if (safeEndLine < safeStartLine) {
          return
        }

        for (line <- safeStartLine to safeEndLine) {
          val lineY       = (line - renderWindow.offsetY) * FontManager.getLineHeight()
          val lineContent = buffer.lines(line)

          val lineStart    = if line == safeStartLine then startChar else 0
          val lineEnd      = if line == safeEndLine then endChar else lineContent.length()
          var startXOffset = 0.0f
          var endXOffset   = 0.0f

          var i = 0
          while (i < lineContent.length()) {
            val codePoint = lineContent.toString.codePointAt(i)
            val font      = FontManager.getDefaultFont()
            val glyph     = new String(Character.toChars(codePoint))
            val charWidth = font.measureText(glyph)

            if (i < lineStart) {
              startXOffset += charWidth
            }
            if (i < lineEnd) {
              endXOffset += charWidth
            }

            i += Character.charCount(codePoint)
          }

          renderer.drawRect(
            bufferX + startXOffset,
            bufferY + lineY,
            endXOffset - startXOffset,
            FontManager.getLineHeight(),
            selectColorInt
          )
        }
      }
    }
  }

  override def render(renderer: Renderer2D, x: Float, y: Float, width: Float, height: Float): Unit = {
    renderer.pushClip(x, y, width, height)
    renderer.drawRect(x, y, width, height, hexToInt(getConfig.theme.background))
    drawCurrentLineBackground(renderer, x, y, width, FontManager.getLineHeight())
    if (buffer != null) {
      if (renderWindow == null) {
        renderer.popClip()
        return
      }
      renderSearchHighlights(renderer, x, y, width, height)
      renderText(renderer, x, y, width, height, renderWindow.offsetY)
      if (WindowManager.getCurrentWindow == renderWindow) {
        renderCursor(renderer, x, y)
        renderSelection(renderer, x, y)
      }
    }
    renderer.popClip()

    // Draw border after clipping is removed so all edges are visible
    // renderer.drawRect(x, y, width, height, borderColor, true)
  }
}
