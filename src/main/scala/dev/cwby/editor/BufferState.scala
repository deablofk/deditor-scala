package dev.cwby.editor

import dev.cwby.lsp.Range

final case class Snapshot(
    lines: Array[String],
    cursorX: Int,
    cursorY: Int
)

final case class BufferState(
    lines: Array[StringBuilder],
    cursorX: Int,
    cursorY: Int,
    undoStack: Array[Snapshot],
    redoStack: Array[Snapshot],
    searchActive: Boolean,
    searchQuery: String,
    searchMatches: Array[(Int, Int)],
    searchMatchesByLine: Array[(Int, Array[Int])],
    searchCurrentIndex: Int,
    searchAnchorX: Int,
    searchAnchorY: Int,
    lastSearchQuery: String,
    filePath: String,
    fileType: String
)

object BufferState:
  final val maxUndo: Int = 1000

  def empty: BufferState =
    BufferState(
      lines = Array(new StringBuilder()),
      cursorX = 0,
      cursorY = 0,
      undoStack = Array.empty,
      redoStack = Array.empty,
      searchActive = false,
      searchQuery = "",
      searchMatches = Array.empty,
      searchMatchesByLine = Array.empty,
      searchCurrentIndex = -1,
      searchAnchorX = 0,
      searchAnchorY = 0,
      lastSearchQuery = "",
      filePath = null,
      fileType = null
    )

  def fromLines(lines: Array[StringBuilder]): BufferState =
    val validLines = if lines.isEmpty then Array(new StringBuilder()) else lines
    empty.copy(lines = validLines)

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

object SearchOps:
  def begin(state: BufferState): BufferState =
    val updated = state.copy(
      searchActive = true,
      searchQuery = "",
      searchAnchorX = state.cursorX,
      searchAnchorY = state.cursorY
    )
    recomputeMatches(updated)

  def accept(state: BufferState): BufferState =
    state.copy(searchActive = false, lastSearchQuery = state.searchQuery)

  def cancel(state: BufferState): BufferState =
    state.copy(
      searchActive = false,
      searchQuery = "",
      lastSearchQuery = "",
      searchMatches = Array.empty,
      searchMatchesByLine = Array.empty,
      searchCurrentIndex = -1
    )

  def appendChar(state: BufferState, c: Char): BufferState =
    val updated     = state.copy(searchQuery = state.searchQuery + c)
    val withMatches = recomputeMatches(updated)
    gotoCurrentMatch(withMatches)

  def backspaceChar(state: BufferState): BufferState =
    if state.searchQuery.isEmpty then return state
    val updated     = state.copy(searchQuery = state.searchQuery.dropRight(1))
    val withMatches = recomputeMatches(updated)
    gotoCurrentMatch(withMatches)

  def beginWithQuery(state: BufferState, query: String): BufferState =
    if query == null || query.isEmpty then return state
    val updated = state.copy(
      searchActive = false,
      searchQuery = query,
      lastSearchQuery = query,
      searchAnchorX = state.cursorX,
      searchAnchorY = state.cursorY
    )
    val withMatches = recomputeMatches(updated)
    gotoCurrentMatch(withMatches)

  def beginForWordUnderCursor(state: BufferState): BufferState =
    val word = CursorOps.getWordUnderCursor(state)
    if word == null || word.isEmpty then state else beginWithQuery(state, word)

  def getMatchStartsForLine(state: BufferState, y: Int): Array[Int] =
    var i = 0
    while i < state.searchMatchesByLine.length do
      val (lineY, starts) = state.searchMatchesByLine(i)
      if lineY == y then return starts
      i += 1
    Array.empty

  def getCurrentMatch(state: BufferState): Option[(Int, Int)] =
    if state.searchCurrentIndex >= 0 && state.searchCurrentIndex < state.searchMatches.length then
      Some(state.searchMatches(state.searchCurrentIndex))
    else None

  def getStatusText(state: BufferState): String =
    if state.searchQuery.isEmpty then return ""
    val total   = state.searchMatches.length
    val current = getCurrentDisplayIndex(state)
    s"[$current/$total]"

  def getCurrentDisplayIndex(state: BufferState): Int =
    if state.searchQuery.isEmpty then return 0
    if state.searchMatches.isEmpty then return 0

    val matchAtCursor = findMatchIndexContainingCursor(state, state.searchQuery.length)
    if matchAtCursor >= 0 then return matchAtCursor + 1

    val next = selectNextMatchIndexFromCursor(state)
    if next >= 0 then next + 1 else 0

  def searchNext(state: BufferState): BufferState =
    if state.searchMatches.isEmpty then return state
    val nextIdx = selectNextMatchIndexFromCursor(state)
    val updated = state.copy(searchCurrentIndex = nextIdx)
    gotoCurrentMatch(updated)

  def searchPrev(state: BufferState): BufferState =
    if state.searchMatches.isEmpty then return state
    val prevIdx = selectPrevMatchIndexFromCursor(state)
    val updated = state.copy(searchCurrentIndex = prevIdx)
    gotoCurrentMatch(updated)

  private def selectNextMatchIndexFromCursor(state: BufferState): Int =
    if state.searchMatches.isEmpty then return -1
    val cy = state.cursorY
    val cx = state.cursorX
    var i  = 0
    while i < state.searchMatches.length do
      val (y, x) = state.searchMatches(i)
      if y > cy || (y == cy && x > cx) then return i
      i += 1
    0

  private def selectPrevMatchIndexFromCursor(state: BufferState): Int =
    if state.searchMatches.isEmpty then return -1
    val cy = state.cursorY
    val cx = state.cursorX
    var i  = state.searchMatches.length - 1
    while i >= 0 do
      val (y, x) = state.searchMatches(i)
      if y < cy || (y == cy && x < cx) then return i
      i -= 1
    state.searchMatches.length - 1

  private def findMatchIndexContainingCursor(state: BufferState, queryLength: Int): Int =
    if state.searchMatches.isEmpty then return -1
    if queryLength <= 0 then return -1

    val cy = state.cursorY
    val cx = state.cursorX

    var i = 0
    while i < state.searchMatches.length do
      val (y, x) = state.searchMatches(i)
      if y == cy && cx >= x && cx < x + queryLength then return i
      i += 1
    -1

  private def gotoCurrentMatch(state: BufferState): BufferState =
    getCurrentMatch(state) match
      case Some((y, x)) => CursorOps.gotoPosition(state, x, y)
      case None         => state

  private def recomputeMatches(state: BufferState): BufferState =
    val q = state.searchQuery
    if q.isEmpty then
      return state.copy(
        searchMatches = Array.empty,
        searchMatchesByLine = Array.empty,
        searchCurrentIndex = -1
      )

    val matchesBuffer = new scala.collection.mutable.ArrayBuffer[(Int, Int)]()
    val byLineMap     = new scala.collection.mutable.HashMap[Int, scala.collection.mutable.ArrayBuffer[Int]]()

    var y = 0
    while y < state.lines.length do
      val line = state.lines(y).toString
      var x    = line.indexOf(q)
      while x != -1 do
        matchesBuffer += ((y, x))
        val list = byLineMap.getOrElseUpdate(y, new scala.collection.mutable.ArrayBuffer[Int]())
        list += x
        x = line.indexOf(q, x + 1)
      y += 1

    val matches = matchesBuffer.toArray
    val byLine  = byLineMap.map { case (y, xs) => (y, xs.toArray) }.toArray
    val bestIdx = selectBestMatchIndex(state.copy(searchMatches = matches), state.searchAnchorY, state.searchAnchorX)

    state.copy(
      searchMatches = matches,
      searchMatchesByLine = byLine,
      searchCurrentIndex = bestIdx
    )

  private def selectBestMatchIndex(state: BufferState, ay: Int, ax: Int): Int =
    if state.searchMatches.isEmpty then return -1

    var i = 0
    while i < state.searchMatches.length do
      val (y, x) = state.searchMatches(i)
      if y > ay || (y == ay && x >= ax) then return i
      i += 1
    0

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
