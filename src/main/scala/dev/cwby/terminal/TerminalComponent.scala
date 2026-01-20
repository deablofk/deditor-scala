package dev.cwby.terminal

import dev.cwby.graphics.FontManager
import dev.cwby.graphics.layout.component.IComponent
import dev.cwby.graphics.opengl.Renderer2D

import scala.scalanative.unsafe._

final class TerminalComponent(private val session: TerminalSession) extends IComponent:

  private val cellBufSize      = 8
  private val lineBufSize      = 64 * 1024
  private val borderColor: Int = 0xff000000

  private def toColor(rgb: Int, default: Int): Int =
    if rgb == 0 then default else 0xff000000 | rgb

  private def applyDimIfNeeded(color: Int, attrs: Int, bg: Int): Int =
    val dim = (attrs & (1 << 6)) != 0
    if !dim then color
    else
      val fgR = (color >> 16) & 0xff
      val fgG = (color >> 8) & 0xff
      val fgB = color & 0xff
      val bgR = (bg >> 16) & 0xff
      val bgG = (bg >> 8) & 0xff
      val bgB = bg & 0xff
      val r   = ((fgR * 6 + bgR * 4) / 10) & 0xff
      val g   = ((fgG * 6 + bgG * 4) / 10) & 0xff
      val b   = ((fgB * 6 + bgB * 4) / 10) & 0xff
      0xff000000 | (r << 16) | (g << 8) | b

  def onTextInput(text: String): Unit =
    session.writeUtf8(text)

  def onSpecialSequence(seq: String): Unit =
    session.writeUtf8(seq)

  override def render(renderer: Renderer2D, x: Float, y: Float, width: Float, height: Float): Unit =
    val lineHeight = FontManager.getLineHeight()
    val avgWidth   = FontManager.getAvgWidth()

    val rows = Math.max(1, (height / lineHeight).toInt)
    val cols = Math.max(1, (width / avgWidth).toInt)

    session.resize(rows, cols, width.toInt, height.toInt)
    session.poll()

    val font = FontManager.getDefaultFont()

    val defaultBg = 0xff000000
    val defaultFg = 0xffffffff

    val cursor = session.getCursor()

    renderer.pushClip(x, y, width, height)

    renderer.drawRect(x, y, width, height, defaultBg)

    Zone {
      val utf8  = stackalloc[CChar](cellBufSize)
      val fg    = stackalloc[CInt](1)
      val bg    = stackalloc[CInt](1)
      val attrs = stackalloc[CInt](1)
      val w     = stackalloc[CInt](1)

      var r = 0
      while r < rows do
        var c = 0
        while c < cols do
          val ok = session.fillCell(r, c, utf8, cellBufSize, fg, bg, attrs, w)
          if ok then
            val widthCells = (!w).toInt
            if widthCells != 0 then
              val rawBg   = (!bg).toInt
              val bgColor = toColor(rawBg, defaultBg)
              if bgColor != defaultBg then
                val drawX = x + c * avgWidth
                val drawY = y + r * lineHeight
                renderer.drawRect(drawX, drawY, avgWidth * Math.max(1, widthCells), lineHeight, bgColor)
          c += 1
        r += 1

      renderer.beginTextBatch(font)

      var drewAnyGlyph = false
      r = 0
      while r < rows do
        var c2 = 0
        while c2 < cols do
          val ok2 = session.fillCell(r, c2, utf8, cellBufSize, fg, bg, attrs, w)
          if ok2 then
            val widthCells = (!w).toInt
            if widthCells != 0 then
              val s = fromCString(utf8)
              if s != null && s.nonEmpty && s != " " then
                drewAnyGlyph = true
                val rawFg = (!fg).toInt
                val rawBg = (!bg).toInt
                val a     = (!attrs).toInt

                val bgColor = toColor(rawBg, defaultBg)
                val fgColor = applyDimIfNeeded(toColor(rawFg, defaultFg), a, bgColor)

                val drawX = x + c2 * avgWidth
                val drawY = y + r * lineHeight

                val isCursor  = cursor != null && cursor.visible && cursor.row == r && cursor.col == c2
                val textColor = if isCursor then 0xff000000 else fgColor
                renderer.drawText(s, drawX, drawY + font.getAscent(), font, textColor)
          c2 += 1
        r += 1

      renderer.endTextBatch(font)

      if !drewAnyGlyph then
        val lineBuf = stackalloc[CChar](lineBufSize)
        renderer.beginTextBatch(font)
        var rr = 0
        while rr < rows do
          val n = session.getLineText(rr, lineBuf, lineBufSize)
          if n > 0 then
            val safeN = Math.min(n, lineBufSize - 1)
            lineBuf(safeN) = 0.toByte
            val line = fromCString(lineBuf)
            if line != null && line.nonEmpty then
              renderer.drawText(line, x, y + rr * lineHeight + font.getAscent(), font, defaultFg)
          rr += 1
        renderer.endTextBatch(font)
    }

    if cursor != null && cursor.visible then
      val cx = x + cursor.col * avgWidth
      val cy = y + cursor.row * lineHeight
      renderer.drawRect(cx, cy, avgWidth, lineHeight, 0xffffffff)
    renderer.popClip()

    renderer.drawRect(x, y, width, height, borderColor, true)
