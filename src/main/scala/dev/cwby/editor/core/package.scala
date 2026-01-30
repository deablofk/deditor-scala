package dev.cwby.editor

import dev.cwby.guitk.components.BufferOps

package object core {
  given BufferOps[TextBuffer] with {
    def getCursorX(buffer: TextBuffer): Int = buffer.cursorX
    def getCursorY(buffer: TextBuffer): Int = buffer.cursorY
    def getLinesCount(buffer: TextBuffer): Int = buffer.lines.length
    def getLine(buffer: TextBuffer, lineIndex: Int): StringBuilder = buffer.lines(lineIndex)
  }
}
