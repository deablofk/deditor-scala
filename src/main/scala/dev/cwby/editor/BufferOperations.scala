package dev.cwby.editor

import dev.cwby.lsp.Range
import dev.cwby.editor.UnicodeOps

final case class Snapshot(
    lines: Array[String],
    cursorX: Int,
    cursorY: Int
)

object BufferCore:
  @inline def getLine(state: BufferState, y: Int): StringBuilder =
    val safeY = Math.max(0, Math.min(y, state.lines.length - 1))
    state.lines(safeY)

  @inline def getCurrentLine(state: BufferState): StringBuilder =
    getLine(state, state.cursorY)

  def getSourceCode(state: BufferState): String =
    val sb = new StringBuilder()
    var i  = 0
    while i < state.lines.length do
      sb.append(state.lines(i)).append('\n')
      i += 1
    sb.toString

  def getLinesAsStrings(state: BufferState): Array[String] =
    val result = new Array[String](state.lines.length)
    var i      = 0
    while i < state.lines.length do
      result(i) = state.lines(i).toString
      i += 1
    result

  def setLinesFromStrings(state: BufferState, lines: Array[String]): BufferState =
    val builders = new Array[StringBuilder](lines.length)
    var i        = 0
    while i < lines.length do
      builders(i) = new StringBuilder(lines(i))
      i += 1
    val validLines = if builders.isEmpty then Array(new StringBuilder()) else builders
    state.copy(lines = validLines)

  def insertText(state: BufferState, text: String): BufferState =
    val line  = getCurrentLine(state)
    val safeX = Math.max(0, Math.min(state.cursorX, line.length))
    line.insert(safeX, text)
    state.copy(cursorX = safeX + text.length)

  def appendChar(state: BufferState, c: Char): BufferState =
    val line  = getCurrentLine(state)
    val safeX = Math.max(0, Math.min(state.cursorX, line.length))
    line.insert(safeX, c)
    state.copy(cursorX = safeX + 1)

  def pasteText(state: BufferState, text: String): BufferState =
    if text == null then return state

    val line  = getCurrentLine(state)
    val parts = text.split("\n", -1)
    line.insert(state.cursorX, parts(0))
    var newX     = state.cursorX + parts(0).length
    var newY     = state.cursorY
    var newLines = state.lines

    if parts.length > 1 then
      val remaining = line.substring(newX)
      line.setLength(newX)

      var i = 1
      while i < parts.length do
        val newLine = new StringBuilder(parts(i))
        if i == 1 then newLine.insert(0, remaining)

        newY += 1
        val temp = new Array[StringBuilder](newLines.length + 1)
        System.arraycopy(newLines, 0, temp, 0, newY)
        temp(newY) = newLine
        System.arraycopy(newLines, newY, temp, newY + 1, newLines.length - newY)
        newLines = temp
        newX = newLine.length
        i += 1

    state.copy(lines = newLines, cursorX = newX, cursorY = newY)

  def replaceTextInRange(state: BufferState, range: Range, text: String): BufferState =
    val startLine = Math.min(range.getStart().getLine(), state.lines.length - 1)
    val endLine   = Math.min(range.getEnd().getLine(), state.lines.length - 1)
    if startLine < 0 || endLine < 0 then return state

    val startChar = Math.min(range.getStart().getCharacter(), state.lines(startLine).length)
    val endChar   = Math.min(range.getEnd().getCharacter(), state.lines(endLine).length)

    if startLine == endLine then
      val line = state.lines(startLine)
      line.replace(startChar, endChar, text)
      state.copy(cursorX = startChar + text.length)
    else
      val firstLine = state.lines(startLine)
      firstLine.replace(startChar, firstLine.length, text)

      var lineX = startLine + 1
      while lineX < endLine do
        state.lines(lineX) = new StringBuilder()
        lineX += 1

      val lastLine = state.lines(endLine)
      lastLine.replace(0, endChar, "")
      lastLine.insert(0, text)
      state

  def removeChar(state: BufferState): BufferState =
    if state.cursorX > 0 then
      val prevX = UnicodeOps.prevGraphemeIndex(getCurrentLine(state), state.cursorX)
      getCurrentLine(state).delete(prevX, state.cursorX)
      state.copy(cursorX = prevX)
    else if state.cursorY > 0 then
      val currentLine = state.lines(state.cursorY)
      val newLines    = new Array[StringBuilder](state.lines.length - 1)
      System.arraycopy(state.lines, 0, newLines, 0, state.cursorY)
      System.arraycopy(state.lines, state.cursorY + 1, newLines, state.cursorY, state.lines.length - state.cursorY - 1)
      val newY = state.cursorY - 1
      val newX = newLines(newY).length
      newLines(newY).append(currentLine)
      state.copy(lines = newLines, cursorX = newX, cursorY = newY)
    else state

  def removeCharAfter(state: BufferState): BufferState =
    val line = getCurrentLine(state)
    if state.cursorX < line.length then
      val nextX = UnicodeOps.nextGraphemeIndex(line, state.cursorX)
      line.delete(state.cursorX, nextX)
      state
    else if state.cursorY < state.lines.length - 1 then
      val nextLine = state.lines(state.cursorY + 1)
      getCurrentLine(state).append(nextLine)
      val newLines = new Array[StringBuilder](state.lines.length - 1)
      System.arraycopy(state.lines, 0, newLines, 0, state.cursorY + 1)
      System.arraycopy(
        state.lines,
        state.cursorY + 2,
        newLines,
        state.cursorY + 1,
        state.lines.length - state.cursorY - 2
      )
      state.copy(lines = newLines)
    else state

  def calculateTabCount(line: String): Int =
    var spaceCount = 0
    var tabCount   = 0
    var i          = 0
    while i < line.length do
      val c = line.charAt(i)
      if c == ' ' then spaceCount += 1
      else if c == '\t' then tabCount += 1
      else return tabCount + spaceCount / 4
      i += 1
    tabCount + spaceCount / 4

  def calculateIndentation(previousLine: String): String =
    var tabCount = calculateTabCount(previousLine)
    if previousLine.trim.endsWith("{") then tabCount += 1
    "\t" * Math.max(tabCount, 0)

  def leadingWhitespace(s: String): String =
    var i = 0
    while i < s.length && (s.charAt(i) == ' ' || s.charAt(i) == '\t') do i += 1
    s.substring(0, i)

  def newLineUp(state: BufferState): BufferState =
    val safeY    = Math.max(0, state.cursorY)
    val newLine  = new StringBuilder(calculateIndentation(getCurrentLine(state).toString))
    val newX     = newLine.length
    val newLines = new Array[StringBuilder](state.lines.length + 1)
    System.arraycopy(state.lines, 0, newLines, 0, safeY)
    newLines(safeY) = newLine
    System.arraycopy(state.lines, safeY, newLines, safeY + 1, state.lines.length - safeY)
    state.copy(lines = newLines, cursorX = newX)

  def newLineDown(state: BufferState): BufferState =
    val newLine  = new StringBuilder(calculateIndentation(getCurrentLine(state).toString))
    val newX     = newLine.length
    val newY     = state.cursorY + 1
    val newLines = new Array[StringBuilder](state.lines.length + 1)
    System.arraycopy(state.lines, 0, newLines, 0, newY)
    newLines(newY) = newLine
    System.arraycopy(state.lines, newY, newLines, newY + 1, state.lines.length - newY)
    state.copy(lines = newLines, cursorX = newX, cursorY = newY)

  def smartNewLine(state: BufferState): BufferState =
    val currentLine    = state.lines(state.cursorY)
    val lineLength     = currentLine.length
    val x              = Math.max(0, Math.min(state.cursorX, lineLength))
    val prevChar: Char = if x > 0 && x - 1 < lineLength then currentLine.charAt(x - 1) else 0.toChar
    val nextChar: Char = if x < lineLength then currentLine.charAt(x) else 0.toChar

    val betweenPairs =
      (prevChar == '{' && nextChar == '}') ||
        (prevChar == '[' && nextChar == ']') ||
        (prevChar == '(' && nextChar == ')')

    if betweenPairs then
      val left        = currentLine.substring(0, x)
      val right       = currentLine.substring(x)
      val baseIndent  = leadingWhitespace(currentLine.toString)
      val innerIndent = calculateIndentation(left)

      state.lines(state.cursorY) = new StringBuilder(left)
      val middleLine  = new StringBuilder(innerIndent)
      val closingLine = new StringBuilder(baseIndent).append(right)

      val newLines = new Array[StringBuilder](state.lines.length + 2)
      System.arraycopy(state.lines, 0, newLines, 0, state.cursorY + 1)
      newLines(state.cursorY + 1) = middleLine
      newLines(state.cursorY + 2) = closingLine
      System.arraycopy(
        state.lines,
        state.cursorY + 1,
        newLines,
        state.cursorY + 3,
        state.lines.length - state.cursorY - 1
      )

      state.copy(lines = newLines, cursorX = innerIndent.length, cursorY = state.cursorY + 1)
    else
      val left        = currentLine.substring(0, x)
      val right       = currentLine.substring(x)
      val indentation = calculateIndentation(left)
      val newLine     = new StringBuilder(indentation)

      if x >= lineLength then
        val newY     = state.cursorY + 1
        val newLines = new Array[StringBuilder](state.lines.length + 1)
        System.arraycopy(state.lines, 0, newLines, 0, newY)
        newLines(newY) = newLine
        System.arraycopy(state.lines, newY, newLines, newY + 1, state.lines.length - newY)
        state.copy(lines = newLines, cursorX = newLine.length, cursorY = newY)
      else
        state.lines(state.cursorY) = new StringBuilder(left)
        newLine.append(right)
        val newY     = state.cursorY + 1
        val newLines = new Array[StringBuilder](state.lines.length + 1)
        System.arraycopy(state.lines, 0, newLines, 0, newY)
        newLines(newY) = newLine
        System.arraycopy(state.lines, newY, newLines, newY + 1, state.lines.length - newY)
        state.copy(lines = newLines, cursorX = indentation.length, cursorY = newY)

  def deleteCurrentLine(state: BufferState): BufferState =
    if state.lines.length == 1 then
      getCurrentLine(state).setLength(0)
      state.copy(cursorX = 0)
    else if state.lines.length - 1 == state.cursorY then
      val newLines = new Array[StringBuilder](state.lines.length - 1)
      System.arraycopy(state.lines, 0, newLines, 0, state.cursorY)
      val newY = if state.cursorY > 0 then state.cursorY - 1 else 0
      state.copy(lines = newLines, cursorY = newY)
    else
      val newLines = new Array[StringBuilder](state.lines.length - 1)
      System.arraycopy(state.lines, 0, newLines, 0, state.cursorY)
      System.arraycopy(state.lines, state.cursorY + 1, newLines, state.cursorY, state.lines.length - state.cursorY - 1)
      state.copy(lines = newLines)

  def getRegion(state: BufferState, startChar: Int, startLine: Int, endChar: Int, endLine: Int): String =
    var sChar = startChar
    var sLine = startLine
    var eChar = endChar
    var eLine = endLine

    if startLine > endLine || (startLine == endLine && startChar > endChar) then
      val tempChar = sChar
      val tempLine = sLine
      sChar = eChar
      sLine = eLine
      eChar = tempChar
      eLine = tempLine

    if state.lines.isEmpty then return ""

    sLine = Math.min(Math.max(0, sLine), state.lines.length - 1)
    eLine = Math.min(Math.max(0, eLine), state.lines.length - 1)

    if sLine > eLine then
      val tempLine = sLine
      sLine = eLine
      eLine = tempLine
      val tempChar = sChar
      sChar = eChar
      eChar = tempChar

    val region = new StringBuilder()
    var line   = sLine
    while line <= eLine do
      val content = state.lines(line).toString
      val lineLen = content.length

      if line == sLine && line == eLine then
        val s            = Math.min(Math.max(0, sChar), lineLen)
        val endExclusive = Math.min(Math.max(s, eChar + 1), lineLen)
        region.append(content)
      else if line == sLine then
        val s = Math.min(Math.max(0, sChar), lineLen)
        region.append(content.substring(s)).append("\n")
      else if line == eLine then
        val endExclusive = Math.min(Math.max(0, eChar + 1), lineLen)
        region.append(content)
      else region.append(content).append("\n")
      line += 1

    region.toString

  def deleteRegion(state: BufferState, startChar: Int, startLine: Int, endChar: Int, endLine: Int): BufferState =
    var sChar = startChar
    var sLine = startLine
    var eChar = endChar
    var eLine = endLine

    if startLine > endLine || (startLine == endLine && startChar > endChar) then
      val tempChar = sChar
      val tempLine = sLine
      sChar = eChar
      sLine = eLine
      eChar = tempChar
      eLine = tempLine

    sLine = Math.min(Math.max(0, sLine), state.lines.length - 1)
    eLine = Math.min(Math.max(0, eLine), state.lines.length - 1)

    if sLine > eLine then
      val tempLine = sLine
      sLine = eLine
      eLine = tempLine
      val tempChar = sChar
      sChar = eChar
      eChar = tempChar

    val startLineBuilder = state.lines(sLine)
    val startLineLen     = startLineBuilder.length
    val startS           = Math.min(Math.max(0, sChar), startLineLen)

    if sLine == eLine then
      val endExclusive = Math.min(Math.max(startS, eChar + 1), startLineLen)
      startLineBuilder.delete(startS, endExclusive)
      return state.copy(cursorX = startS, cursorY = sLine)

    startLineBuilder.delete(startS, startLineLen)

    val endLineBuilder = state.lines(eLine)
    val endLineLen     = endLineBuilder.length
    val endExclusive   = Math.min(Math.max(0, eChar + 1), endLineLen)
    val endLineSuffix  = endLineBuilder.substring(endExclusive, endLineLen)

    startLineBuilder.append(endLineSuffix)

    val finalLines = new Array[StringBuilder](state.lines.length - (eLine - sLine))
    System.arraycopy(state.lines, 0, finalLines, 0, sLine + 1)
    if eLine + 1 < state.lines.length then
      System.arraycopy(state.lines, eLine + 1, finalLines, sLine + 1, state.lines.length - eLine - 1)

    state.copy(lines = finalLines, cursorX = startS, cursorY = sLine)

  def searchWord(state: BufferState, word: String): Array[(Int, Int)] =
    val buffer = new scala.collection.mutable.ArrayBuffer[(Int, Int)]()
    var y      = 0
    while y < state.lines.length do
      val line = state.lines(y).toString
      var x    = line.indexOf(word)
      while x != -1 do
        buffer += ((y, x))
        x = line.indexOf(word, x + 1)
      y += 1
    buffer.toArray

object UndoOps:
  private def snapshot(state: BufferState): Snapshot =
    val linesCopy = new Array[String](state.lines.length)
    var i         = 0
    while i < state.lines.length do
      linesCopy(i) = state.lines(i).toString
      i += 1
    Snapshot(linesCopy, state.cursorX, state.cursorY)

  private def restore(state: BufferState, snap: Snapshot): BufferState =
    val lines = new Array[StringBuilder](snap.lines.length)
    var i     = 0
    while i < snap.lines.length do
      lines(i) = new StringBuilder(snap.lines(i))
      i += 1
    val validLines = if lines.isEmpty then Array(new StringBuilder()) else lines
    val safeY      = Math.min(Math.max(0, snap.cursorY), validLines.length - 1)
    val safeX      = Math.min(Math.max(0, snap.cursorX), validLines(safeY).length)
    state.copy(lines = validLines, cursorX = safeX, cursorY = safeY)

  def push(state: BufferState): BufferState =
    val snap    = snapshot(state)
    var newUndo = state.undoStack :+ snap
    if newUndo.length > BufferState.maxUndo then newUndo = newUndo.drop(1)
    state.copy(undoStack = newUndo, redoStack = Array.empty)

  def undo(state: BufferState): BufferState =
    if state.undoStack.isEmpty then return state
    val currentSnap = snapshot(state)
    val snap        = state.undoStack.last
    val newUndo     = state.undoStack.dropRight(1)
    val newRedo     = state.redoStack :+ currentSnap
    restore(state.copy(undoStack = newUndo, redoStack = newRedo), snap)

  def redo(state: BufferState): BufferState =
    if state.redoStack.isEmpty then return state
    val currentSnap = snapshot(state)
    val snap        = state.redoStack.last
    val newRedo     = state.redoStack.dropRight(1)
    val newUndo     = state.undoStack :+ currentSnap
    restore(state.copy(undoStack = newUndo, redoStack = newRedo), snap)
