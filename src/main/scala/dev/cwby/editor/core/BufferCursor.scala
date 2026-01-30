package dev.cwby.editor.core

import dev.cwby.editor.core.BufferCore

object UnicodeOps:
  @inline def isCombiningMark(codePoint: Int): Boolean =
    val t = Character.getType(codePoint)
    t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK || t == Character.ENCLOSING_MARK

  @inline def isRegionalIndicator(codePoint: Int): Boolean =
    codePoint >= 0x1f1e6 && codePoint <= 0x1f1ff

  @inline def isEmojiModifier(codePoint: Int): Boolean =
    codePoint >= 0x1f3fb && codePoint <= 0x1f3ff

  def nextGraphemeIndex(line: StringBuilder, index: Int): Int =
    val len = line.length
    if index >= len then return len

    var next    = index
    val firstCp = Character.codePointAt(line, next)
    next += Character.charCount(firstCp)

    if isRegionalIndicator(firstCp) then
      if next < len then
        val secondCp = Character.codePointAt(line, next)
        if isRegionalIndicator(secondCp) then next += Character.charCount(secondCp)

    var continue = true
    while continue && next < len do
      val cp = Character.codePointAt(line, next)
      if cp == 0x200d then
        next += 1
        if next < len then
          val nextCp = Character.codePointAt(line, next)
          next += Character.charCount(nextCp)
      else if isCombiningMark(cp) || isEmojiModifier(cp) then next += Character.charCount(cp)
      else continue = false
    next

  def prevGraphemeIndex(line: StringBuilder, index: Int): Int =
    if index <= 0 then return 0
    var prev = index

    var continue = true
    while continue && prev > 0 do
      val cp = Character.codePointBefore(line, prev)
      prev -= Character.charCount(cp)

      if isCombiningMark(cp) || isEmojiModifier(cp) then ()
      else if isRegionalIndicator(cp) then
        if prev > 0 then
          val behindCp = Character.codePointBefore(line, prev)
          if isRegionalIndicator(behindCp) then prev -= Character.charCount(behindCp)
        continue = false
      else if prev > 0 then
        val behindCp = Character.codePointBefore(line, prev)
        if behindCp == 0x200d then prev -= 1
        else continue = false
      else continue = false
    prev

object CursorOps:
  @inline def clampCursor(state: BufferState, x: Int, y: Int): BufferState =
    if state.lines.isEmpty then return state.copy(cursorX = 0, cursorY = 0)
    val safeY = Math.min(Math.max(0, y), state.lines.length - 1)
    val safeX = Math.min(Math.max(0, x), state.lines(safeY).length)
    state.copy(cursorX = safeX, cursorY = safeY)

  def moveLeft(state: BufferState): BufferState =
    val prevX = UnicodeOps.prevGraphemeIndex(BufferCore.getCurrentLine(state), state.cursorX)
    clampCursor(state, prevX, state.cursorY)

  def moveRight(state: BufferState): BufferState =
    val nextX = UnicodeOps.nextGraphemeIndex(BufferCore.getCurrentLine(state), state.cursorX)
    clampCursor(state, nextX, state.cursorY)

  def moveUp(state: BufferState): BufferState =
    clampCursor(state, state.cursorX, state.cursorY - 1)

  def moveDown(state: BufferState): BufferState =
    clampCursor(state, state.cursorX, state.cursorY + 1)

  def gotoPosition(state: BufferState, x: Int, y: Int): BufferState =
    clampCursor(state, x, y)

  def moveToFirstNonWhitespace(state: BufferState): BufferState =
    val line = BufferCore.getCurrentLine(state).toString.trim
    if line.isEmpty then return state
    val c     = line.charAt(0)
    val index = BufferCore.getCurrentLine(state).indexOf(c.toString)
    if index != -1 then gotoPosition(state, index, state.cursorY) else state

  def moveToLineEnd(state: BufferState): BufferState =
    gotoPosition(state, BufferCore.getCurrentLine(state).length, state.cursorY)

  private def getWordBounds(state: BufferState, forward: Boolean): (Int, Int) =
    val currentLine = BufferCore.getCurrentLine(state)
    val len         = currentLine.length
    var start       = state.cursorX
    var end         = state.cursorX

    if forward then
      while end < len && Character.isLetterOrDigit(currentLine.charAt(end)) do end += 1
      while end < len && !Character.isLetterOrDigit(currentLine.charAt(end)) do end += 1
    else
      while start > 0 && !Character.isLetterOrDigit(currentLine.charAt(start - 1)) do start -= 1
      while start > 0 && Character.isLetterOrDigit(currentLine.charAt(start - 1)) do start -= 1

    (start, end)

  def moveNextWord(state: BufferState): BufferState =
    val (_, end) = getWordBounds(state, forward = true)
    state.copy(cursorX = end)

  def movePreviousWord(state: BufferState): BufferState =
    val (start, _) = getWordBounds(state, forward = false)
    state.copy(cursorX = start)

  def removeNextWord(state: BufferState): BufferState =
    val (start, end) = getWordBounds(state, forward = true)
    BufferCore.getCurrentLine(state).delete(start, end)
    state

  def removePreviousWord(state: BufferState): BufferState =
    val (start, end) = getWordBounds(state, forward = false)
    BufferCore.getCurrentLine(state).delete(start, end)
    state.copy(cursorX = start)

  def getWordUnderCursor(state: BufferState): String =
    if state.lines.isEmpty then return ""
    val line = BufferCore.getCurrentLine(state)
    if line.isEmpty then return ""
    if state.cursorX < 0 || state.cursorX >= line.length then return ""

    var start = state.cursorX
    while start > 0 && Character.isLetterOrDigit(line.charAt(start - 1)) do start -= 1

    var end = state.cursorX
    while end < line.length && Character.isLetterOrDigit(line.charAt(end)) do end += 1

    if start < end then line.substring(start, end) else ""
