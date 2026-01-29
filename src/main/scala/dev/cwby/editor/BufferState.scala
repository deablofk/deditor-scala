package dev.cwby.editor

import dev.cwby.editor.{Snapshot, CursorOps}

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
