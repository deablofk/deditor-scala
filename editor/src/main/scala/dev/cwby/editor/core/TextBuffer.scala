package dev.cwby.editor.core

import dev.cwby.getFileExtension
import dev.cwby.lsp.Range

import java.io.File
import scala.compiletime.uninitialized

class TextBuffer(loader: FileChunkLoader) {
  var state: BufferState               = uninitialized
  var fileChunkLoader: FileChunkLoader = uninitialized
  var file: File                       = uninitialized

  if (loader != null) {
    fileChunkLoader = loader
    val lines = fileChunkLoader.loadChunkByByteSize()
    this.file = loader.getFile()
    val path  = file.getAbsolutePath
    val fType = getFileExtension(path)
    state = BufferState.fromLines(lines.toArray).copy(filePath = path, fileType = fType)
  } else {
    state = BufferState.empty
  }

  def this() = {
    this(null)
  }

  def lines: Array[StringBuilder] = state.lines
  def cursorX: Int                = state.cursorX
  def cursorY: Int                = state.cursorY
  def filepath: String            = state.filePath
  def fileType: String            = state.fileType

  def getCurrentLine(): StringBuilder             = BufferCore.getCurrentLine(state)
  def getLines(): Array[StringBuilder]            = state.lines
  def setLines(lines: Array[StringBuilder]): Unit = { state = state.copy(lines = lines) }
  def getSourceCode(): String                     = BufferCore.getSourceCode(state)
  def getFilepath(): String                       = state.filePath
  def getFileType(): String                       = state.fileType
  def setFilepath(path: String): Unit             = { state = state.copy(filePath = path) }
  def setFileType(ft: String): Unit               = { state = state.copy(fileType = ft) }

  def pushUndoState(): Unit = { state = UndoOps.push(state) }
  def undo(): Unit          = { state = UndoOps.undo(state) }
  def redo(): Unit          = { state = UndoOps.redo(state) }

  def beginSearch(): Unit                       = { state = SearchOps.begin(state) }
  def acceptSearch(): Unit                      = { state = SearchOps.accept(state) }
  def cancelSearch(): Unit                      = { state = SearchOps.cancel(state) }
  def isSearchActive(): Boolean                 = state.searchActive
  def getSearchQuery(): String                  = state.searchQuery
  def getLastSearchQuery(): String              = state.lastSearchQuery
  def getSearchQueryLength(): Int               = state.searchQuery.length
  def getSearchMatches(): Array[(Int, Int)]     = state.searchMatches
  def getSearchMatchCount(): Int                = state.searchMatches.length
  def getSearchStatusText(): String             = SearchOps.getStatusText(state)
  def getSearchCurrentDisplayIndex(): Int       = SearchOps.getCurrentDisplayIndex(state)
  def beginSearchWithQuery(query: String): Unit = { state = SearchOps.beginWithQuery(state, query) }
  def beginSearchForWordUnderCursor(): Boolean  = {
    val word = CursorOps.getWordUnderCursor(state)
    if (word == null || word.isEmpty) false
    else { state = SearchOps.beginWithQuery(state, word); true }
  }
  def getSearchMatchStartsForLine(y: Int): Array[Int] = SearchOps.getMatchStartsForLine(state, y)
  def getCurrentSearchMatch(): Option[(Int, Int)]     = SearchOps.getCurrentMatch(state)
  def appendSearchChar(c: Char): Unit                 = { state = SearchOps.appendChar(state, c) }
  def backspaceSearchChar(): Unit                     = { state = SearchOps.backspaceChar(state) }
  def searchNext(): Boolean = { state = SearchOps.searchNext(state); state.searchMatches.nonEmpty }
  def searchPrev(): Boolean = { state = SearchOps.searchPrev(state); state.searchMatches.nonEmpty }

  def appendChar(c: Char): Unit                            = { state = BufferCore.appendChar(state, c) }
  def insertTextAtCursor(text: String): Unit               = { state = BufferCore.insertText(state, text) }
  def pasteText(text: String): Unit                        = { state = BufferCore.pasteText(state, text) }
  def replaceTextInRange(range: Range, text: String): Unit = {
    state = BufferCore.replaceTextInRange(state, range, text)
  }
  def removeChar(): Unit                                 = { state = BufferCore.removeChar(state) }
  def removeCharAfterCursor(): Unit                      = { state = BufferCore.removeCharAfter(state) }
  def newLineUp(): Unit                                  = { state = BufferCore.newLineUp(state) }
  def newLineDown(): Unit                                = { state = BufferCore.newLineDown(state) }
  def smartNewLine(): Unit                               = { state = BufferCore.smartNewLine(state) }
  def deleteCurrentLine(): Unit                          = { state = BufferCore.deleteCurrentLine(state) }
  def calculateTabCount(line: String): Int               = BufferCore.calculateTabCount(line)
  def calculateIndentation(previousLine: String): String = BufferCore.calculateIndentation(previousLine)

  def moveCursor(x: Int, y: Int): Unit     = { state = CursorOps.clampCursor(state, x, y) }
  def moveCursorLeft(): Unit               = { state = CursorOps.moveLeft(state) }
  def moveCursorRight(): Unit              = { state = CursorOps.moveRight(state) }
  def moveCursorUp(): Unit                 = { state = CursorOps.moveUp(state) }
  def moveCursorDown(): Unit               = { state = CursorOps.moveDown(state) }
  def moveCursorHalfUp(): Unit             = { state = CursorOps.moveUp(state) }
  def moveCursorHalfDown(): Unit           = { state = CursorOps.moveDown(state) }
  def moveToFirstNonWhitespaceChar(): Unit = { state = CursorOps.moveToFirstNonWhitespace(state) }
  def moveToLastChar(): Unit               = { state = CursorOps.moveToLineEnd(state) }
  def moveNextWord(): Unit                 = { state = CursorOps.moveNextWord(state) }
  def movePreviousWord(): Unit             = { state = CursorOps.movePreviousWord(state) }
  def removeNextWord(): Unit               = { state = CursorOps.removeNextWord(state) }
  def removePreviousWord(): Unit           = { state = CursorOps.removePreviousWord(state) }
  def gotoPosition(x: Int, y: Int): Unit   = { state = CursorOps.gotoPosition(state, x, y) }

  def nextGraphemeIndex(index: Int): Int           = UnicodeOps.nextGraphemeIndex(getCurrentLine(), index)
  def prevGraphemeIndex(index: Int): Int           = UnicodeOps.prevGraphemeIndex(getCurrentLine(), index)
  def isCombiningMark(codePoint: Int): Boolean     = UnicodeOps.isCombiningMark(codePoint)
  def isRegionalIndicator(codePoint: Int): Boolean = UnicodeOps.isRegionalIndicator(codePoint)
  def isEmojiModifier(codePoint: Int): Boolean     = UnicodeOps.isEmojiModifier(codePoint)

  def getRegion(startChar: Int, startLine: Int, endChar: Int, endLine: Int): String =
    BufferCore.getRegion(state, startChar, startLine, endChar, endLine)

  def deleteRegion(startChar: Int, startLine: Int, endChar: Int, endLine: Int): Unit =
    state = BufferCore.deleteRegion(state, startChar, startLine, endChar, endLine)

  def searchWordUnderCursor(): Array[(Int, Int)] = {
    val word = CursorOps.getWordUnderCursor(state)
    if (word == null || word.isEmpty) Array.empty
    else BufferCore.searchWord(state, word)
  }

  def searchWord(word: String): Array[(Int, Int)] = BufferCore.searchWord(state, word)

  def synchronized[T](f: => T): T = f
}
