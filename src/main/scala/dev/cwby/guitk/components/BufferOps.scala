package dev.cwby.guitk.components

trait BufferOps[B] {
  def getCursorX(buffer: B): Int
  def getCursorY(buffer: B): Int
  def getLinesCount(buffer: B): Int
  def getLine(buffer: B, lineIndex: Int): StringBuilder
}
