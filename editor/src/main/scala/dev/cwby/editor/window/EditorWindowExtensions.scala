package dev.cwby.editor.window

import dev.cwby.editor.core.TextBuffer
import dev.cwby.guitk.components.Window
import dev.cwby.guitk.text.FontManager

object EditorWindowExtensions {

  extension (window: Window) {

    def ensureTextBufferCursorVisible(buffer: TextBuffer): Unit = {
      if buffer == null then return

      val visibleLines = window.getVisibleLines
      if visibleLines <= 0 then return

      val cursorY = buffer.cursorY

      if cursorY < window.offsetY then window.offsetY = cursorY
      else if cursorY >= window.offsetY + visibleLines then window.offsetY = cursorY - visibleLines + 1

      val maxOffsetY = Math.max(0, buffer.lines.length - visibleLines)
      window.offsetY = Math.min(Math.max(0, window.offsetY), maxOffsetY)
    }

    def ensureTextBufferCursorVisibleHorizontal(buffer: TextBuffer): Unit = {
      if buffer == null then return

      val font        = FontManager.getDefaultFont()
      val lineHeight  = FontManager.getLineHeight()
      val usableWidth = (window.width - FontManager.getAvgWidth()).toInt

      if usableWidth <= 0 || lineHeight <= 0 then return

      val cursorY = buffer.cursorY
      if cursorY < 0 || cursorY >= buffer.lines.length then return

      val line    = buffer.lines(cursorY)
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
      val viewLeftPx    = window.offsetX
      val viewRightPx   = window.offsetX + usableWidth

      if cursorLeftPx < viewLeftPx then window.offsetX = cursorLeftPx
      else if cursorRightPx > viewRightPx then window.offsetX = cursorRightPx - usableWidth

      window.offsetX = Math.max(0, window.offsetX)
    }
  }
}
