package dev.cwby.input

import dev.cwby.WindowManager
import dev.cwby.appendCommandBuffer
import dev.cwby.clearCommandBuffer
import dev.cwby.clipboard.ClipboardType
import dev.cwby.clipboard.getClipboardContent
import dev.cwby.clipboard.setClipboardContent
import dev.cwby.commandHandlerState
import dev.cwby.editor.TextBuffer
import dev.cwby.editor.TextInteractionMode
import dev.cwby.editor.TextInteractionMode.*
import dev.cwby.executeCommand
import dev.cwby.getBufferMode
import dev.cwby.getCommandBuffer
import dev.cwby.guitk.platform.Engine
import dev.cwby.guitk.text.FontManager
import dev.cwby.guitk.renderer.OpenGLRenderer
import dev.cwby.guitk.components.TiledWindow
import dev.cwby.graphics.layout.component.TelescopeComponent
import dev.cwby.graphics.layout.component.TelescopeWindow
import dev.cwby.graphics.layout.component.TextComponent
import dev.cwby.guitk.bindings.sdl.SDLConstants.*
import dev.cwby.guitk.bindings.sdl.{SDLEventHelpers, SDLKeyboard, SDL_Event}
import dev.cwby.lsp.CompletionItemKind
import dev.cwby.lsp.LSPManager
import dev.cwby.setBufferMode
import dev.cwby.terminal.TerminalWindow

import scala.scalanative.unsafe.*

object GlobalKeyHandler:
  var lastKeyPressTime: Long = 0
  var startVisualX: Int = 0
  var startVisualY: Int = 0
  private var modeNode: TrieNode = KeybindingTrie.getRoot(getBufferMode)
  private var anyNode: TrieNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
  private var lastMode: TextInteractionMode = getBufferMode

  // TODO: refactor this code to reduce complexity, calculating TABs, must be in other place
  private def caretXPxAt(buffer: TextBuffer, cursorX: Int): Int =
    if buffer == null || buffer.lines == null || buffer.lines.isEmpty then return 0
    if buffer.cursorY < 0 || buffer.cursorY >= buffer.lines.length then return 0

    val font = FontManager.getDefaultFont()
    val line = buffer.lines(buffer.cursorY)
    val safeCursorX = Math.min(Math.max(0, cursorX), line.length())

    val tabSize = 4
    val spaceWidth = font.measureText(" ")

    var xPx = 0.0f
    var i = 0
    while i < safeCursorX && i < line.length() do
      val codePoint = line.toString.codePointAt(i)
      if codePoint == '\t' then
        val tabWidth = spaceWidth * tabSize
        xPx = ((xPx + tabWidth) / tabWidth).toInt * tabWidth
      else
        val glyph = new String(Character.toChars(codePoint))
        xPx += font.measureText(glyph)
      i += Character.charCount(codePoint)

    xPx.toInt

  private def getCurrentWordPrefix(buffer: TextBuffer): String =
    if buffer == null || buffer.lines == null || buffer.lines.isEmpty then return ""
    if buffer.cursorY < 0 || buffer.cursorY >= buffer.lines.length then return ""

    val line = buffer.lines(buffer.cursorY)
    if line.isEmpty then return ""

    val cursorX = Math.min(buffer.cursorX, line.length())
    var start = cursorX
    while start > 0 && Character.isLetterOrDigit(line.charAt(start - 1)) do start -= 1

    if start < cursorX then line.substring(start, cursorX) else ""


  private case class BufferPos(y: Int, x: Int)

  private def comparePos(a: BufferPos, b: BufferPos): Int = {
    if a.y != b.y then Integer.compare(a.y, b.y)
    else Integer.compare(a.x, b.x)
  }

  private def isWithinInclusive(open: BufferPos, cursor: BufferPos, close: BufferPos): Boolean = {
    comparePos(open, cursor) <= 0 && comparePos(cursor, close) <= 0
  }

  private def isModifierScancode(scancode: Int): Boolean = {
    // TODO: wtf is this (224...231)? Replace for NON Magic numbers
    scancode match
      case 224 | 225 | 226 | 227 | 228 | 229 | 230 | 231 => true
      case _ => false
  }


  private def findEnclosingPairInBuffer(b: TextBuffer, open: Char, close: Char): Option[(BufferPos, BufferPos)] =
    if b == null then return None
    if b.lines == null || b.lines.isEmpty then return None

    val cursor = BufferPos(b.cursorY, b.cursorX)

    var stack: List[BufferPos] = Nil
    var best: Option[(BufferPos, BufferPos)] = None

    var y = 0
    while y < b.lines.length do
      val line = b.lines(y)
      var x = 0
      while x < line.length() do
        val ch = line.charAt(x)
        if ch == open then stack = BufferPos(y, x) :: stack
        else if ch == close && stack.nonEmpty then
          val openPos = stack.head
          stack = stack.tail
          val closePos = BufferPos(y, x)
          if isWithinInclusive(openPos, cursor, closePos) then
            best match
              case Some((bestOpen, _)) =>
                if comparePos(bestOpen, openPos) < 0 then best = Some((openPos, closePos))
              case None =>
                best = Some((openPos, closePos))
        x += 1
      y += 1

    best

  private def deleteInsidePairAndYank(b: TextBuffer, openPos: BufferPos, closePos: BufferPos): Unit =
    if b == null then return

    val openY = Math.max(0, Math.min(openPos.y, b.lines.length - 1))
    val closeY = Math.max(0, Math.min(closePos.y, b.lines.length - 1))

    if openY > closeY then return

    val openLine = b.lines(openY)
    val closeLine = b.lines(closeY)

    val openX = Math.max(0, Math.min(openPos.x, Math.max(0, openLine.length() - 1)))
    val closeX = Math.max(0, Math.min(closePos.x, Math.max(0, closeLine.length() - 1)))

    val closeIndent = leadingWhitespace(closeLine)
    val closeSuffix = closeLine.substring(closeX, closeLine.length())

    if openY == closeY then
      val start = Math.min(openX + 1, openLine.length())
      val endExclusive = Math.max(start, closeX)
      val inside = openLine.substring(start, endExclusive)
      if inside.forall(_.isWhitespace) then b.gotoPosition(start, openY)
      else
        b.pushUndoState()
        yankToClipboard(inside)
        openLine.delete(start, endExclusive)
        b.gotoPosition(start, openY)
      return

    val yankBuilder = new StringBuilder()
    val startX = Math.min(openX + 1, openLine.length())
    yankBuilder.append(openLine.substring(startX, openLine.length())).append("\n")
    var y = openY + 1
    while y < closeY do
      yankBuilder.append(b.lines(y).toString).append("\n")
      y += 1
    yankBuilder.append(closeLine.substring(0, closeX))
    val yankText = yankBuilder.toString
    if yankText.forall(_.isWhitespace) then
      b.gotoPosition(startX, openY)
      return

    b.pushUndoState()
    yankToClipboard(yankText)

    openLine.delete(startX, openLine.length())

    val innerIndent = b.calculateIndentation(openLine.toString)
    val newCloseLine = new StringBuilder(closeIndent).append(closeSuffix)
    val newInnerLine = new StringBuilder(innerIndent)

    val newLines = scala.collection.mutable.ArrayBuffer[StringBuilder]()
    newLines ++= b.lines.slice(0, openY + 1)
    newLines += newInnerLine
    newLines += newCloseLine
    newLines ++= b.lines.slice(closeY + 1, b.lines.length)

    b.setLines(newLines.toArray)
    b.gotoPosition(innerIndent.length(), openY + 1)

  private def isTagNameChar(c: Char): Boolean =
    Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':'

  private case class ParsedTag(name: String, isClosing: Boolean, isSelfClosing: Boolean, endX: Int)

  private def parseTagInLine(line: StringBuilder, startX: Int): Option[ParsedTag] =
    if line == null then return None
    if startX < 0 || startX >= line.length() then return None
    if line.charAt(startX) != '<' then return None

    val len = line.length()
    if startX + 1 >= len then return None

    val next = line.charAt(startX + 1)
    if next == '!' || next == '?' then return None

    var i = startX + 1
    var isClosing = false
    if i < len && line.charAt(i) == '/' then
      isClosing = true
      i += 1

    while i < len && Character.isWhitespace(line.charAt(i)) do i += 1

    val nameStart = i
    while i < len && isTagNameChar(line.charAt(i)) do i += 1
    if i == nameStart then return None

    val name = line.substring(nameStart, i)

    var end = i
    while end < len && line.charAt(end) != '>' do end += 1
    if end >= len then return None

    val isSelfClosing =
      !isClosing && end - 1 >= 0 && line.charAt(end - 1) == '/'

    Some(ParsedTag(name = name, isClosing = isClosing, isSelfClosing = isSelfClosing, endX = end))

  private case class TagMatch(
                               openY: Int,
                               openStartX: Int,
                               openEndX: Int,
                               closeY: Int,
                               closeStartX: Int,
                               closeEndX: Int,
                               name: String
                             )

  private def findEnclosingTagInBuffer(b: TextBuffer): Option[TagMatch] =
    if b == null then return None
    if b.lines == null || b.lines.isEmpty then return None

    val cursor = BufferPos(b.cursorY, b.cursorX)
    var best: Option[TagMatch] = None

    var y = b.cursorY
    while y >= 0 do
      val line = b.lines(y)
      var x = Math.min(if y == b.cursorY then b.cursorX else line.length() - 1, line.length() - 1)
      while x >= 0 do
        if line.charAt(x) == '<' then
          parseTagInLine(line, x) match
            case Some(tag) if !tag.isClosing && !tag.isSelfClosing =>
              val openStart = BufferPos(y, x)
              BufferPos(y, tag.endX)

              var depth = 1
              var yy = y
              val xx = tag.endX + 1
              var foundClose: Option[(BufferPos, BufferPos)] = None

              while yy < b.lines.length && foundClose.isEmpty do
                val l = b.lines(yy)
                var scanX = if yy == y then xx else 0
                while scanX < l.length() && foundClose.isEmpty do
                  if l.charAt(scanX) == '<' then
                    parseTagInLine(l, scanX) match
                      case Some(t2) if t2.name == tag.name && !t2.isSelfClosing =>
                        if t2.isClosing then
                          depth -= 1
                          if depth == 0 then foundClose = Some((BufferPos(yy, scanX), BufferPos(yy, t2.endX)))
                        else depth += 1
                      case _ =>
                  scanX += 1
                yy += 1

              foundClose match
                case Some((closeStart, closeEnd)) =>
                  if isWithinInclusive(openStart, cursor, closeEnd) then
                    val candidate = TagMatch(y, x, tag.endX, closeStart.y, closeStart.x, closeEnd.x, tag.name)
                    best match
                      case Some(existing) =>
                        val existingOpen = BufferPos(existing.openY, existing.openStartX)
                        if comparePos(BufferPos(candidate.openY, candidate.openStartX), existingOpen) > 0 then
                          best = Some(candidate)
                      case None =>
                        best = Some(candidate)
                case None =>
            case _ =>
        x -= 1
      y -= 1

    best

  private def changeInsideTag(b: TextBuffer): Unit =
    if b == null then return

    findEnclosingTagInBuffer(b) match
      case None =>
        ()
      case Some(m) =>
        val openLine = b.lines(m.openY)
        val closeLine = b.lines(m.closeY)

        val startY = m.openY
        val startX = Math.min(m.openEndX + 1, openLine.length())
        val endY = m.closeY
        val endX = Math.max(0, Math.min(m.closeStartX, closeLine.length()))

        val yankBuilder = new StringBuilder()
        if startY == endY then yankBuilder.append(openLine.substring(startX, endX))
        else
          yankBuilder.append(openLine.substring(startX, openLine.length())).append("\n")
          var yy = startY + 1
          while yy < endY do
            yankBuilder.append(b.lines(yy).toString).append("\n")
            yy += 1
          yankBuilder.append(closeLine.substring(0, endX))

        val yankText = yankBuilder.toString
        if yankText.forall(_.isWhitespace) then
          b.gotoPosition(startX, startY)
          return

        val closeIndent = leadingWhitespace(closeLine)
        val closeSuffix = closeLine.substring(m.closeStartX, closeLine.length())

        b.pushUndoState()
        yankToClipboard(yankText)

        if startY == endY then
          openLine.delete(startX, endX)
          b.gotoPosition(startX, startY)
        else
          openLine.delete(startX, openLine.length())

          val innerIndent = leadingWhitespace(openLine)
          val newCloseLine = new StringBuilder(closeIndent).append(closeSuffix)
          val newInnerLine = new StringBuilder(innerIndent)

          val newLines = scala.collection.mutable.ArrayBuffer[StringBuilder]()
          newLines ++= b.lines.slice(0, startY + 1)
          newLines += newInnerLine
          newLines += newCloseLine
          newLines ++= b.lines.slice(endY + 1, b.lines.length)

          b.setLines(newLines.toArray)
          b.gotoPosition(innerIndent.length(), startY + 1)

  private def findEnclosingQuoteInBuffer(b: TextBuffer, quote: Char): Option[(BufferPos, BufferPos)] =
    if b == null then return None
    if b.lines == null || b.lines.isEmpty then return None

    val cursor = BufferPos(b.cursorY, b.cursorX)

    var stack: List[BufferPos] = Nil
    var best: Option[(BufferPos, BufferPos)] = None

    var y = 0
    while y < b.lines.length do
      val line = b.lines(y)
      var x = 0
      while x < line.length() do
        val ch = line.charAt(x)
        if ch == quote && !isEscapedAt(line, x) then
          if stack.nonEmpty then
            val openPos = stack.head
            stack = stack.tail
            val closePos = BufferPos(y, x)
            if isWithinInclusive(openPos, cursor, closePos) then
              best match
                case Some((bestOpen, _)) =>
                  if comparePos(bestOpen, openPos) < 0 then best = Some((openPos, closePos))
                case None =>
                  best = Some((openPos, closePos))
          else stack = BufferPos(y, x) :: stack
        x += 1
      y += 1

    best

  private def deleteInsideQuoteAndYank(b: TextBuffer, openPos: BufferPos, closePos: BufferPos): Unit =
    if b == null then return

    val openY = Math.max(0, Math.min(openPos.y, b.lines.length - 1))
    val closeY = Math.max(0, Math.min(closePos.y, b.lines.length - 1))

    if openY > closeY then return

    val openLine = b.lines(openY)
    val closeLine = b.lines(closeY)
    val openX = Math.max(0, Math.min(openPos.x, Math.max(0, openLine.length() - 1)))
    val closeX = Math.max(0, Math.min(closePos.x, Math.max(0, closeLine.length() - 1)))

    val closeIndent = leadingWhitespace(closeLine)
    val closeSuffix = closeLine.substring(closeX, closeLine.length())

    if openY == closeY then
      val start = Math.min(openX + 1, openLine.length())
      val endExclusive = Math.max(start, closeX)
      val inside = openLine.substring(start, endExclusive)
      if inside.forall(_.isWhitespace) then b.gotoPosition(start, openY)
      else
        b.pushUndoState()
        yankToClipboard(inside)
        openLine.delete(start, endExclusive)
        b.gotoPosition(start, openY)
      return

    val yankBuilder = new StringBuilder()
    val startX = Math.min(openX + 1, openLine.length())
    yankBuilder.append(openLine.substring(startX, openLine.length())).append("\n")
    var y = openY + 1
    while y < closeY do
      yankBuilder.append(b.lines(y).toString).append("\n")
      y += 1
    yankBuilder.append(closeLine.substring(0, closeX))

    val yankText = yankBuilder.toString
    if yankText.forall(_.isWhitespace) then
      b.gotoPosition(startX, openY)
      return

    b.pushUndoState()
    yankToClipboard(yankText)

    openLine.delete(startX, openLine.length())

    val innerIndent = leadingWhitespace(openLine)
    val newCloseLine = new StringBuilder(closeIndent).append(closeSuffix)
    val newInnerLine = new StringBuilder(innerIndent)

    val newLines = scala.collection.mutable.ArrayBuffer[StringBuilder]()
    newLines ++= b.lines.slice(0, openY + 1)
    newLines += newInnerLine
    newLines += newCloseLine
    newLines ++= b.lines.slice(closeY + 1, b.lines.length)

    b.setLines(newLines.toArray)
    b.gotoPosition(innerIndent.length(), openY + 1)

  private def changeInsideDelimiter(b: TextBuffer, open: Char, close: Char): Unit =
    println("Typed " + open)
    if b == null then return

    if open == close then
      findEnclosingQuoteInBuffer(b, open) match
        case Some((openPos, closePos)) =>
          deleteInsideQuoteAndYank(b, openPos, closePos)
          switchMode(INSERT)
        case None =>
          ()
    else
      findEnclosingPairInBuffer(b, open, close) match
        case Some((openPos, closePos)) =>
          deleteInsidePairAndYank(b, openPos, closePos)
          switchMode(INSERT)
        case None =>
          ()

  private def leadingWhitespace(line: StringBuilder): String =
    val s = line.toString
    var i = 0
    while i < s.length && (s.charAt(i) == ' ' || s.charAt(i) == '\t') do i += 1
    s.substring(0, i)

  private def firstNonWhitespaceIndex(line: StringBuilder): Int =
    val s = line.toString
    var i = 0
    while i < s.length && (s.charAt(i) == ' ' || s.charAt(i) == '\t') do i += 1
    i

  private def wordBoundsForward(b: TextBuffer): (Int, Int) =
    val currentLine = b.getCurrentLine()
    val len = currentLine.length()
    val start = Math.max(0, Math.min(b.cursorX, len))
    var end = start
    while end < len && Character.isLetterOrDigit(currentLine.charAt(end)) do end += 1
    while end < len && !Character.isLetterOrDigit(currentLine.charAt(end)) do end += 1
    (start, end)

  private def wordBoundsBackward(b: TextBuffer): (Int, Int) =
    val currentLine = b.getCurrentLine()
    val len = currentLine.length()
    val end = Math.max(0, Math.min(b.cursorX, len))
    var start = end
    while start > 0 && !Character.isLetterOrDigit(currentLine.charAt(start - 1)) do start -= 1
    while start > 0 && Character.isLetterOrDigit(currentLine.charAt(start - 1)) do start -= 1
    (start, end)

  private def yankToClipboard(text: String): Unit =
    setClipboardContent(ClipboardType.INTERNAL, text)

  private def stripTrailingPositionLine(text: String): String =
    if text == null || text.isEmpty then return text
    val lastNewline = text.lastIndexOf('\n')
    if lastNewline < 0 then return text

    val tail = text.substring(lastNewline + 1).trim
    if !(tail.startsWith("(") && tail.endsWith(")")) then return text

    val inner = tail.substring(1, tail.length - 1)
    val parts = inner.split(",").map(_.trim)
    if parts.length != 2 then return text

    val isDigits0 = parts(0).nonEmpty && parts(0).forall(_.isDigit)
    val isDigits1 = parts(1).nonEmpty && parts(1).forall(_.isDigit)
    if isDigits0 && isDigits1 then text.substring(0, lastNewline)
    else text

  private def deleteRangeAndYank(b: TextBuffer, start: Int, end: Int): Unit =
    b.pushUndoState()
    val line = b.getCurrentLine()
    val s = Math.max(0, Math.min(start, line.length()))
    val e = Math.max(s, Math.min(end, line.length()))
    yankToClipboard(line.substring(s, e))
    line.delete(s, e)
    b.gotoPosition(s, b.cursorY)

  private def deleteRange(b: TextBuffer, start: Int, end: Int): Unit =
    b.pushUndoState()
    val line = b.getCurrentLine()
    val s = Math.max(0, Math.min(start, line.length()))
    val e = Math.max(s, Math.min(end, line.length()))
    line.delete(s, e)
    b.gotoPosition(s, b.cursorY)

  private def isEscapedAt(line: StringBuilder, index: Int): Boolean =
    var i = index - 1
    var backslashes = 0
    while i >= 0 && line.charAt(i) == '\\' do
      backslashes += 1
      i -= 1
    (backslashes % 2) == 1

  private def countUnescapedQuotesBeforeCursor(line: StringBuilder, cursorX: Int): Int =
    val end = Math.max(0, Math.min(cursorX, line.length()))
    var i = 0
    var count = 0
    while i < end do
      if line.charAt(i) == '"' && !isEscapedAt(line, i) then count += 1
      i += 1
    count

  private def autoPairHandled(b: TextBuffer, c: Char): Boolean =
    if b == null then return false

    val line = b.getCurrentLine()
    val x = Math.max(0, Math.min(b.cursorX, line.length()))
    val nextChar: Char = if x < line.length() then line.charAt(x) else 0.toChar

    def insertPair(open: Char, close: Char): Boolean =
      b.insertTextAtCursor(s"$open$close")
      b.moveCursorLeft()
      true

    def insertSingle(ch: Char): Boolean =
      b.insertTextAtCursor(String.valueOf(ch))
      true

    c match
      case '(' => insertPair('(', ')')
      case '{' => insertPair('{', '}')
      case '[' => insertPair('[', ']')
      case ')' | '}' | ']' =>
        if nextChar == c then
          b.moveCursorRight()
          true
        else insertSingle(c)
      case '"' =>
        if nextChar == '"' then
          b.moveCursorRight()
          true
        else
          val quoteCount = countUnescapedQuotesBeforeCursor(line, x)
          if (quoteCount % 2) == 1 then insertSingle('"')
          else insertPair('"', '"')
      case _ => false

  private def registerCommandMappings(): Unit =
    KeybindingTrie.cmap(
      "ESC",
      (_, _) => {
        clearCommandBuffer()
        switchMode(NAVIGATION)
      }
    )
    KeybindingTrie.cmap(
      "RET",
      (_, _) => {
        switchMode(NAVIGATION)
        executeCommand(getCommandBuffer)
        clearCommandBuffer()
      }
    )
    KeybindingTrie.cmap(
      "BACKSPACE",
      (_, _) => {
        val length = getCommandBuffer.length() - 1
        if length >= 0 then commandHandlerState.buffer.deleteCharAt(length)
      }
    )
    KeybindingTrie.cmap(
      "CTRL-v",
      (_, _) => {
        commandHandlerState.buffer.append(getClipboardContent(ClipboardType.SYSTEM))
      }
    )

  private def registerSearchMappings(): Unit =
    KeybindingTrie.map(
      SEARCH,
      "ESC",
      (_, b) => {
        WindowManager.getCurrentWindow match
          case telescope: TelescopeWindow =>
            telescope.close()
            switchMode(NAVIGATION)
          case _ =>
            if b != null then b.cancelSearch()
            switchMode(NAVIGATION)
      }
    )
    KeybindingTrie.map(
      SEARCH,
      "RET",
      (_, b) => {
        WindowManager.getCurrentWindow match
          case telescope: TelescopeWindow =>
            telescope.onTrigger()
            switchMode(NAVIGATION)
          case _ =>
            if b != null then b.acceptSearch()
            switchMode(NAVIGATION)
      }
    )
    KeybindingTrie.map(
      SEARCH,
      "BACKSPACE",
      (_, b) => {
        WindowManager.getCurrentWindow match
          case telescope: TelescopeWindow =>
            telescope.onQueryBackspace()
          case _ =>
            if b != null then b.backspaceSearchChar()
      }
    )
    KeybindingTrie.map(
      SEARCH,
      "j",
      (w, b) => {
        w match
          case telescope: TelescopeWindow =>
            telescope.moveSelectionDown()
          case _ =>
            if b != null then
              b.searchNext()
              w.ensureCursorVisible(b)
              w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.map(
      SEARCH,
      "k",
      (w, b) => {
        w match
          case telescope: TelescopeWindow =>
            telescope.moveSelectionUp()
          case _ =>
            if b != null then
              b.searchPrev()
              w.ensureCursorVisible(b)
              w.ensureCursorVisibleHorizontal(b)
      }
    )

    KeybindingTrie.map(
      SEARCH,
      "CTRL-n",
      (w, _) => {
        w match
          case telescope: TelescopeWindow =>
            telescope.moveSelectionDown()
          case _ =>
      }
    )

    KeybindingTrie.map(
      SEARCH,
      "CTRL-p",
      (w, _) => {
        w match
          case telescope: TelescopeWindow =>
            telescope.moveSelectionUp()
          case _ =>
      }
    )

    KeybindingTrie.map(
      SEARCH,
      "n",
      (w, b) => {
        if b != null then
          b.searchNext()
          w.ensureCursorVisible(b)
          w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.map(
      SEARCH,
      "N",
      (w, b) => {
        if b != null then
          b.searchPrev()
          w.ensureCursorVisible(b)
          w.ensureCursorVisibleHorizontal(b)
      }
    )

  private def registerNormalMappings(): Unit =
    KeybindingTrie.nmap(
      "ESC",
      (_, b) => {
        KeybindingTrie.resetNumberInput()
        if b != null then b.cancelSearch()
      }
    )
    KeybindingTrie.nmap("i", (_, _) => switchMode(INSERT))
    KeybindingTrie.nmap(
      "I",
      (_, b) => {
        b.moveToFirstNonWhitespaceChar()
        switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "a",
      (_, b) => {
        b.moveCursorRight()
        switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "A",
      (_, b) => {
        b.moveToLastChar()
        switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "v",
      (_, b) => {
        startVisualX = b.cursorX
        startVisualY = b.cursorY
        switchMode(SELECT)
      }
    )
    KeybindingTrie.nmap("V", (_, _) => switchMode(SELECT_LINE))
    KeybindingTrie.nmap(":", (_, _) => switchMode(COMMAND))

    KeybindingTrie.nmap(
      "/",
      (_, b) => {
        if b != null then
          b.beginSearch()
          switchMode(SEARCH)
      }
    )

    KeybindingTrie.nmap(
      "n",
      (w, b) => {
        if b != null then
          b.searchNext()
          w.ensureCursorVisible(b)
          w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "N",
      (w, b) => {
        if b != null then
          b.searchPrev()
          w.ensureCursorVisible(b)
          w.ensureCursorVisibleHorizontal(b)
      }
    )

    KeybindingTrie.nmap(
      "q",
      (w, _) => {
        w match
          case dired: dev.cwby.graphics.layout.component.DiredWindow =>
            dired.close()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "g r",
      (w, _) => {
        w match
          case dired: dev.cwby.graphics.layout.component.DiredWindow =>
            dired.refresh()
          case _ =>
      }
    )
    KeybindingTrie.nmap(
      "o",
      (_, b) => {
        b.newLineDown()
        switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "O",
      (_, b) => {
        b.newLineUp()
        switchMode(INSERT)
      }
    )

    KeybindingTrie.nmap(
      "y y",
      (_, b) => {
        if b != null then yankToClipboard(b.getCurrentLine().toString + "\n")
      }
    )

    KeybindingTrie.nmap(
      "Y",
      (_, b) => {
        if b != null then yankToClipboard(b.getCurrentLine().toString + "\n")
      }
    )

    KeybindingTrie.nmap(
      "y w",
      (_, b) => {
        if b != null then
          val (s, e) = wordBoundsForward(b)
          yankToClipboard(b.getCurrentLine().substring(s, e))
      }
    )
    KeybindingTrie.nmap(
      "y b",
      (_, b) => {
        if b != null then
          val (s, e) = wordBoundsBackward(b)
          yankToClipboard(b.getCurrentLine().substring(s, e))
      }
    )
    KeybindingTrie.nmap(
      "y SHIFT-$",
      (_, b) => {
        if b != null then yankToClipboard(b.getCurrentLine().substring(b.cursorX, b.getCurrentLine().length()))
      }
    )
    KeybindingTrie.nmap(
      "y 0",
      (_, b) => {
        if b != null then
          yankToClipboard(
            b.getCurrentLine().substring(0, Math.max(0, Math.min(b.cursorX, b.getCurrentLine().length())))
          )
      }
    )

    KeybindingTrie.nmap(
      "d d",
      (_, b) => {
        if b != null then
          yankToClipboard(b.getCurrentLine().toString + "\n")
          b.deleteCurrentLine()
          WindowManager.getCurrentWindow match
            case dired: dev.cwby.graphics.layout.component.DiredWindow =>
              dired.applyEditsToFilesystem()
            case _ =>
      }
    )

    KeybindingTrie.nmap(
      "d w",
      (_, b) => {
        if b != null then
          val (s, e) = wordBoundsForward(b)
          deleteRangeAndYank(b, s, e)
      }
    )
    KeybindingTrie.nmap(
      "d b",
      (_, b) => {
        if b != null then
          val (s, e) = wordBoundsBackward(b)
          deleteRangeAndYank(b, s, e)
      }
    )
    KeybindingTrie.nmap(
      "d SHIFT-$",
      (_, b) => {
        if b != null then deleteRangeAndYank(b, b.cursorX, b.getCurrentLine().length())
      }
    )
    KeybindingTrie.nmap(
      "d 0",
      (_, b) => {
        if b != null then deleteRangeAndYank(b, 0, b.cursorX)
      }
    )

    KeybindingTrie.nmap(
      "c w",
      (_, b) => {
        if b != null then
          val (s, e) = wordBoundsForward(b)
          deleteRangeAndYank(b, s, e)
          switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "c b",
      (_, b) => {
        if b != null then
          val (s, e) = wordBoundsBackward(b)
          deleteRangeAndYank(b, s, e)
          switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "c SHIFT-$",
      (_, b) => {
        if b != null then
          deleteRangeAndYank(b, b.cursorX, b.getCurrentLine().length())
          switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "c 0",
      (_, b) => {
        if b != null then
          deleteRangeAndYank(b, 0, b.cursorX)
          switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "c c",
      (_, b) => {
        if b != null then
          yankToClipboard(b.getCurrentLine().toString + "\n")
          val indent = leadingWhitespace(b.getCurrentLine())
          b.getCurrentLine().setLength(0)
          b.getCurrentLine().append(indent)
          b.gotoPosition(indent.length(), b.cursorY)
          switchMode(INSERT)
      }
    )

    KeybindingTrie.nmap("c i (", (_, b) => changeInsideDelimiter(b, '(', ')'))
    KeybindingTrie.nmap("c i {", (_, b) => changeInsideDelimiter(b, '{', '}'))
    KeybindingTrie.nmap("c i [", (_, b) => changeInsideDelimiter(b, '[', ']'))
    KeybindingTrie.nmap("c i <", (_, b) => changeInsideDelimiter(b, '<', '>'))
    KeybindingTrie.nmap("c i t", (_, b) => changeInsideTag(b))
    KeybindingTrie.nmap("c i \"", (_, b) => changeInsideDelimiter(b, '"', '"'))
    KeybindingTrie.nmap("c i '", (_, b) => changeInsideDelimiter(b, '\'', '\''))

    KeybindingTrie.nmap(
      "x",
      (_, b) => {
        if b != null then deleteRangeAndYank(b, b.cursorX, b.nextGraphemeIndex(b.cursorX))
      }
    )
    KeybindingTrie.nmap(
      "X",
      (_, b) => {
        if b != null then deleteRangeAndYank(b, b.prevGraphemeIndex(b.cursorX), b.cursorX)
      }
    )
    KeybindingTrie.nmap(
      "D",
      (_, b) => {
        if b != null then deleteRangeAndYank(b, b.cursorX, b.getCurrentLine().length())
      }
    )
    KeybindingTrie.nmap(
      "C",
      (_, b) => {
        if b != null then
          deleteRangeAndYank(b, b.cursorX, b.getCurrentLine().length())
          switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "s",
      (_, b) => {
        if b != null then
          deleteRangeAndYank(b, b.cursorX, b.nextGraphemeIndex(b.cursorX))
          switchMode(INSERT)
      }
    )
    KeybindingTrie.nmap(
      "S",
      (_, b) => {
        if b != null then
          yankToClipboard(b.getCurrentLine().toString + "\n")
          val indent = leadingWhitespace(b.getCurrentLine())
          b.getCurrentLine().setLength(0)
          b.getCurrentLine().append(indent)
          b.gotoPosition(indent.length(), b.cursorY)
          switchMode(INSERT)
      }
    )

    KeybindingTrie.nmap(
      "g g",
      (w, b) => {
        b.moveCursor(0, 0)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "G",
      (w, b) => {
        b.moveCursor(0, b.lines.length - 1)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "h",
      (w, b) => {
        b.moveCursorLeft()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "j",
      (w, b) => {
        b.moveCursorDown()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "k",
      (w, b) => {
        b.moveCursorUp()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "l",
      (w, b) => {
        b.moveCursorRight()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "w",
      (w, b) => {
        b.moveNextWord()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "b",
      (w, b) => {
        b.movePreviousWord()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "SHIFT-$",
      (w, b) => {
        val x = Math.max(0, b.getCurrentLine().length() - 1)
        b.gotoPosition(x, b.cursorY)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "0",
      (w, b) => {
        b.gotoPosition(0, b.cursorY)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "SHIFT-^",
      (w, b) => {
        val x = firstNonWhitespaceIndex(b.getCurrentLine())
        b.gotoPosition(x, b.cursorY)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    val wordSearchAction: (dev.cwby.guitk.components.Window, TextBuffer) => Unit = (w, b) => {
      if b != null && b.beginSearchForWordUnderCursor() then
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
    }

    KeybindingTrie.nmap("#", wordSearchAction)
    KeybindingTrie.nmap("*", wordSearchAction)
    //    KeybindingTrie.nmap("SHIFT-#", wordSearchAction)
    //    KeybindingTrie.nmap("SHIFT-*", wordSearchAction)

    KeybindingTrie.nmap(
      "CTRL-u",
      (w, b) => {
        val delta = Math.max(1, w.getVisibleLines / 2)
        b.moveCursor(b.cursorX, b.cursorY - delta)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "CTRL-d",
      (w, b) => {
        val delta = Math.max(1, w.getVisibleLines / 2)
        b.moveCursor(b.cursorX, b.cursorY + delta)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "p",
      (_, b) => {
        if b != null then
          val text = getClipboardContent(ClipboardType.INTERNAL)
          if text != null && text.contains("\n") then
            val toPaste = if text.endsWith("\n") then text.dropRight(1) else text
            b.newLineDown()
            b.pasteText(toPaste)
          else
            b.moveCursorRight()
            b.pasteText(text)
      }
    )
    KeybindingTrie.nmap(
      "P",
      (_, b) => {
        if b != null then
          val text = getClipboardContent(ClipboardType.INTERNAL)
          if text != null && text.contains("\n") then
            val toPaste = if text.endsWith("\n") then text.dropRight(1) else text
            b.newLineUp()
            b.pasteText(toPaste)
          else b.pasteText(text)
      }
    )
    KeybindingTrie.nmap(
      "CTRL-p",
      (w, b) => {
        b.moveCursorUp()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "CTRL-n",
      (w, b) => {
        b.moveCursorDown()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.nmap(
      "CTRL-w h",
      (w, _) => {
        w match
          case tiledWindow: TiledWindow =>
            tiledWindow.moveLeft()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w l",
      (w, _) => {
        w match
          case tiledWindow: TiledWindow =>
            tiledWindow.moveRight()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w j",
      (w, _) => {
        w match
          case tiledWindow: TiledWindow =>
            tiledWindow.moveDown()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w k",
      (w, _) => {
        w match
          case tiledWindow: TiledWindow =>
            tiledWindow.moveUp()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w v",
      (w, _) => {
        w match
          case _: TiledWindow =>
            executeCommand("vs")
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w s",
      (w, _) => {
        w match
          case _: TiledWindow =>
            executeCommand("s")
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "u",
      (_, b) => {
        if b != null then b.undo()
      }
    )
    KeybindingTrie.nmap(
      "CTRL-r",
      (_, b) => {
        if b != null then b.redo()
      }
    )

    KeybindingTrie.nmap("RET", (w, _) => w.onTrigger())
    KeybindingTrie.nmap(
      "c e",
      (_, b) => {
        if b != null then
          val line = b.getCurrentLine()
          val len = line.length()
          val start = Math.max(0, Math.min(b.cursorX, len))
          var end = start
          while end < len && Character.isLetterOrDigit(line.charAt(end)) do end += 1
          deleteRangeAndYank(b, start, end)
          switchMode(INSERT)
      }
    )

    KeybindingTrie.nmap("CTRL-=", (_, _) => FontManager.increaseFontSize(1))
    KeybindingTrie.nmap("CTRL--", (_, _) => FontManager.increaseFontSize(-1))
    // lsp stuff, it is probably best to register only if there is a lsp in the buffer, but actually ded cant have specific buffers binding
    KeybindingTrie.nmap(
      "g d",
      (_, b) => {
        val client = LSPManager.getLSPClient(b)
        if client == null then ()

        val definitions = client.requestDefinitions(b)
        if definitions.length == 1 then
          val location = definitions.head
          executeCommand("edit " + location.getUri().replace("file://", ""))
          val x = location.getRange().getStart().getCharacter()
          val y = location.getRange().getStart().getLine()
          OpenGLRenderer.getCurrentTextBuffer().gotoPosition(x, y)
        else
          // show options in the floating window for selecting the denition
          (
          )
      }
    )

    KeybindingTrie.nmap(
      "SPACE s f",
      (_, _) => {
        executeCommand("telescope-files")
      }
    )

    KeybindingTrie.nmap(
      "SPACE s g",
      (_, _) => {
        executeCommand("telescope-grep")
      }
    )

  private def registerSelectMappings(): Unit =
    KeybindingTrie.smap("ESC", (_, _) => switchMode(NAVIGATION))
    KeybindingTrie.map(SELECT_LINE, "ESC", (_, _) => switchMode(NAVIGATION))
    KeybindingTrie.smap("v", (_, _) => switchMode(NAVIGATION))
    KeybindingTrie.map(SELECT_LINE, "v", (_, _) => switchMode(NAVIGATION))
    KeybindingTrie.smap(
      "h",
      (w, b) => {
        b.moveCursorLeft()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "j",
      (w, b) => {
        b.moveCursorDown()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "k",
      (w, b) => {
        b.moveCursorUp()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "l",
      (w, b) => {
        b.moveCursorRight()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )

    KeybindingTrie.smap(
      "w",
      (w, b) => {
        b.moveNextWord()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "b",
      (w, b) => {
        b.movePreviousWord()
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "e",
      (w, b) => {
        val line = b.getCurrentLine()
        val len = line.length()
        var i = Math.max(0, Math.min(b.cursorX, len))
        while i < len && !Character.isLetterOrDigit(line.charAt(i)) do i += 1
        while i < len && Character.isLetterOrDigit(line.charAt(i)) do i += 1
        val x = if i > 0 then i - 1 else 0
        b.gotoPosition(x, b.cursorY)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "0",
      (w, b) => {
        b.gotoPosition(0, b.cursorY)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "SHIFT-$",
      (w, b) => {
        val x = Math.max(0, b.getCurrentLine().length() - 1)
        b.gotoPosition(x, b.cursorY)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )
    KeybindingTrie.smap(
      "SHIFT-^",
      (w, b) => {
        val x = firstNonWhitespaceIndex(b.getCurrentLine())
        b.gotoPosition(x, b.cursorY)
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
      }
    )

    KeybindingTrie.smap(
      "y",
      (_, b) => {
        val region = b.getRegion(startVisualX, startVisualY, b.cursorX, b.cursorY)
        setClipboardContent(ClipboardType.INTERNAL, stripTrailingPositionLine(region))
        switchMode(NAVIGATION)
      }
    )
    KeybindingTrie.smap(
      "d",
      (_, b) => {
        val region = b.getRegion(startVisualX, startVisualY, b.cursorX, b.cursorY)
        setClipboardContent(ClipboardType.INTERNAL, stripTrailingPositionLine(region))
        b.deleteRegion(startVisualX, startVisualY, b.cursorX, b.cursorY)
        switchMode(NAVIGATION)
      }
    )

  private def registerInsertMappings(): Unit =
    KeybindingTrie.imap("TAB", (_, b) => b.insertTextAtCursor("\t"))
    KeybindingTrie.imap(
      "DELETE",
      (_, b) => {
        if b != null then b.removeCharAfterCursor()
      }
    )
    KeybindingTrie.imap(
      "CTRL-w",
      (_, b) => {
        if b != null then
          val (s, e) = wordBoundsBackward(b)
          deleteRange(b, s, e)
      }
    )
    KeybindingTrie.imap(
      "CTRL-u",
      (_, b) => {
        if b != null then deleteRange(b, 0, b.cursorX)
      }
    )
    KeybindingTrie.imap(
      "CTRL-p",
      (_, _) => {
        if WindowManager.getAutoCompleteWindow.isVisible then WindowManager.getAutoCompleteWindow.buffer.moveCursorUp()
      }
    )
    KeybindingTrie.imap(
      "CTRL-n",
      (_, _) => {
        if WindowManager.getAutoCompleteWindow.isVisible then
          WindowManager.getAutoCompleteWindow.buffer.moveCursorDown()
      }
    )

    KeybindingTrie.imap(
      "ESC",
      (_, _) => {
        WindowManager.getAutoCompleteWindow.hide()
        WindowManager.getCurrentWindow match
          case dired: dev.cwby.graphics.layout.component.DiredWindow =>
            dired.applyEditsToFilesystem()
          case _ =>
        switchMode(NAVIGATION)
      }
    )
    KeybindingTrie.imap(
      "RET",
      (_, b) => {
        val cmpWindow = WindowManager.getAutoCompleteWindow
        if cmpWindow.isVisible then
          val selectedItem = cmpWindow.select()
          if selectedItem != null then
            val textEdit = selectedItem.getTextEdit()
            if textEdit != null then b.replaceTextInRange(textEdit.getRange(), textEdit.getNewText())
            else
              val insertText = selectedItem.insertText.getOrElse(selectedItem.getLabel())
              b.insertTextAtCursor(insertText)
            if selectedItem.getKind() == CompletionItemKind.Constructor || selectedItem
              .getKind() == CompletionItemKind.Method
            then b.insertTextAtCursor("()")
        else b.smartNewLine()
      }
    )
    KeybindingTrie.imap(
      "BACKSPACE",
      (_, b) => {
        b.removeChar()
        WindowManager.getAutoCompleteWindow.hide()
      }
    )

    KeybindingTrie.imap(
      "CTRL-v",
      (_, b) => {
        b.insertTextAtCursor(getClipboardContent(ClipboardType.SYSTEM))
      }
    )

  def switchMode(mode: TextInteractionMode): Unit =
    if getBufferMode == mode then return
    if mode == INSERT || mode == COMMAND || mode == SEARCH then startTextInput()
    else if mode == SELECT || mode == SELECT_LINE || mode == SELECT_BLOCK then stopTextInput()
    else stopTextInput()

    setBufferMode(mode)

  def stopTextInput(): Unit = {
    SDLKeyboard.SDL_StopTextInput(Engine.getWindow)
  }

  def startTextInput(): Unit = {
    SDLKeyboard.SDL_StartTextInput(Engine.getWindow)
  }

class GlobalKeyHandler extends IKeyHandler:

  import GlobalKeyHandler.*

  registerNormalMappings()
  registerSelectMappings()
  registerInsertMappings()
  registerCommandMappings()
  registerSearchMappings()

  override def handle(e: Ptr[SDL_Event]): Unit =
    val current = WindowManager.getCurrentWindow
    current match
      case tw: TerminalWindow =>
        val keyCode = SDLEventHelpers.getKeyCode(e)
        SDLEventHelpers.getKeyMod(e)

        if keyCode == K_ESCAPE then
          tw.close()
          lastKeyPressTime = System.currentTimeMillis()
          return

        val seq: String =
          if keyCode == K_RETURN then "\r"
          else if keyCode == K_TAB then "\t"
          else if keyCode == K_BACKSPACE then "\u007f"
          else if keyCode == K_UP then "\u001b[A"
          else if keyCode == K_DOWN then "\u001b[B"
          else if keyCode == K_RIGHT then "\u001b[C"
          else if keyCode == K_LEFT then "\u001b[D"
          else if keyCode == K_HOME then "\u001b[H"
          else if keyCode == K_END then "\u001b[F"
          else if keyCode == K_PAGEUP then "\u001b[5~"
          else if keyCode == K_PAGEDOWN then "\u001b[6~"
          else ""

        if seq.nonEmpty then
          tw.getTerminalComponent().onSpecialSequence(seq)
          lastKeyPressTime = System.currentTimeMillis()
        return
      case _ =>

    val mode = getBufferMode
    if mode != lastMode then
      modeNode = KeybindingTrie.getRoot(mode)
      anyNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
      lastMode = mode

    val keyCode = SDLEventHelpers.getKeyCode(e)
    val mod = SDLEventHelpers.getKeyMod(e)
    val scancode = SDLEventHelpers.getKeyScancode(e)

    if isModifierScancode(scancode) then return

    val keyChar = SDLKeyboard.SDL_GetKeyFromScancode(scancode, mod, false).toChar
    val keyPressed = getKey(mod.toShort, keyCode, keyChar)

    if (mode == NAVIGATION || mode == SELECT || mode == SELECT_LINE || mode == SELECT_BLOCK) &&
      Character.isDigit(keyChar) && (KeybindingTrie.getNumberInput() != 0 || keyChar != '0')
    then
      KeybindingTrie.appendNumberInput(keyChar)
      return

    val nextModeNode = if modeNode == null then null else modeNode.search(keyPressed)
    val nextAnyNode = if anyNode == null then null else anyNode.search(keyPressed)

    modeNode = nextModeNode
    anyNode = nextAnyNode

    if modeNode == null && anyNode == null then
      modeNode = KeybindingTrie.getRoot(getBufferMode)
      anyNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
      KeybindingTrie.resetNumberInput()
    else
      val actionNode =
        if modeNode != null && modeNode.action != null then modeNode
        else if anyNode != null && anyNode.action != null then anyNode
        else null

      if actionNode != null then
        val repeatCount = KeybindingTrie.getNumberInput()
        val window = WindowManager.getCurrentWindow
        var buffer: TextBuffer = null
        window.getComponent match
          case textComponent: TextComponent =>
            buffer = textComponent.getBuffer
          case telescopeComponent: TelescopeComponent =>
            buffer = telescopeComponent.getResultsBuffer()
          case _ =>
        if repeatCount > 0 then
          for _ <- 0 until KeybindingTrie.getNumberInput() do actionNode.action(window, buffer)
        else actionNode.action(window, buffer)
        modeNode = KeybindingTrie.getRoot(getBufferMode)
        anyNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
        KeybindingTrie.resetNumberInput()

    lastKeyPressTime = System.currentTimeMillis()

  def getKey(mod: Short, keyCode: Int, keyChar: Char): String =
    if keyCode == K_ESCAPE then "ESC"
    else if keyCode == K_RETURN then "RET"
    else if keyCode == K_SPACE then "SPACE"
    else if keyCode == K_BACKSPACE then "BACKSPACE"
    else if keyCode == K_TAB then "TAB"
    else if keyCode == K_DELETE then "DELETE"
    else if (mod.toInt & KMOD_CTRL.toInt) != 0 then "CTRL-" + keyChar
    else if (mod.toInt & KMOD_SHIFT.toInt) != 0 then
      if Character.isUpperCase(keyChar) then String.valueOf(keyChar)
      else if !Character.isLetterOrDigit(keyChar) then String.valueOf(keyChar)
      else "SHIFT-" + keyChar
    else if (mod.toInt & KMOD_ALT.toInt) != 0 then "ALT-" + keyChar
    else String.valueOf(keyChar)

  override def handleInput(event: Ptr[SDL_Event]): Unit =
    try {
      WindowManager.getCurrentWindow match
        case tw: TerminalWindow =>
          val textInput = SDLEventHelpers.getTextInput(event)
          if textInput == null || textInput.isEmpty then return
          tw.getTerminalComponent().onTextInput(textInput)
          return
        case _ =>

      val mode = getBufferMode
      if mode == INSERT && WindowManager.getCurrentWindow.getComponent.isInstanceOf[TextComponent] then
        val textComponent = WindowManager.getCurrentWindow.getComponent.asInstanceOf[TextComponent]
        val buffer = textComponent.getBuffer
        val textInput = SDLEventHelpers.getTextInput(event)
        if textInput == null || textInput.isEmpty then return

        val c = textInput.charAt(0)

        if autoPairHandled(buffer, c) then
          WindowManager.getAutoCompleteWindow.hide()
          return

        buffer.appendChar(c)

        if c == '.' || Character.isLetterOrDigit(c) then
          try {
            val client = LSPManager.getLSPClient(buffer)
            if client == null || !client.isInitialized then return
            client.sendDidChange(buffer)

            val activeWindow = WindowManager.getCurrentWindow
            val anchorXIndex = Math.max(0, buffer.cursorX - 1)
            val caretX = caretXPxAt(buffer, anchorXIndex)
            val unclampedX = (activeWindow.x + (caretX - activeWindow.offsetX)).toInt
            val windowY = activeWindow.y + ((buffer.cursorY - activeWindow.offsetY + 1) * FontManager.getLineHeight())
            val maxWindowHeight = (Engine.getHeight - FontManager.getLineHeight()) - windowY
            val windowX = Math.max(0.0f, unclampedX.toFloat)

            client.requestCompletionAsync(
              buffer,
              suggestions => {
                if (suggestions.nonEmpty) {
                  val cmpWindow = WindowManager.getAutoCompleteWindow
                  val wasVisible = cmpWindow.isVisible
                  cmpWindow.setSuggestions(suggestions)

                  val preferredWidth = Math.min(cmpWindow.getPreferredWidth(), Engine.getWidth.toFloat)
                  val baseX = if wasVisible then cmpWindow.x else windowX
                  val availableWidth = Math.max(0.0f, Engine.getWidth.toFloat - baseX)
                  val dynamicWidth = Math.min(preferredWidth, availableWidth)

                  val clampedX =
                    if wasVisible then cmpWindow.x
                    else Math.min(windowX, Math.max(0.0f, Engine.getWidth.toFloat - dynamicWidth))

                  if wasVisible then cmpWindow.show(cmpWindow.x, cmpWindow.y, dynamicWidth, maxWindowHeight)
                  else cmpWindow.show(clampedX, windowY, dynamicWidth, maxWindowHeight)
                }
              }
            )
          } catch {
            case e: Exception =>
              System.err.println(s"[LSP] Completion error: ${e.getMessage}")
          }
        else WindowManager.getAutoCompleteWindow.hide()
      else if mode == COMMAND then
        val textInput = SDLEventHelpers.getTextInput(event)
        if textInput != null then
          if textInput != null && textInput.nonEmpty then appendCommandBuffer(textInput.charAt(0))
      else if mode == SEARCH then
        WindowManager.getCurrentWindow match
          case telescope: TelescopeWindow =>
            val textInput = SDLEventHelpers.getTextInput(event)
            if textInput == null || textInput.isEmpty then return
            telescope.onQueryChar(textInput.charAt(0))
          case _ =>
            if WindowManager.getCurrentWindow.getComponent.isInstanceOf[TextComponent] then
              val textComponent = WindowManager.getCurrentWindow.getComponent.asInstanceOf[TextComponent]
              val buffer = textComponent.getBuffer
              val textInput = SDLEventHelpers.getTextInput(event)
              if textInput == null || textInput.isEmpty then return
              buffer.appendSearchChar(textInput.charAt(0))
    } catch {
      case e: Throwable =>
        println(s"ERROR in handleInput: ${e.getMessage}")
        e.printStackTrace()
    }
