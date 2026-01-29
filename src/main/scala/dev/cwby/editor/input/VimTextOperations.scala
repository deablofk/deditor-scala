package dev.cwby.editor.input

import dev.cwby.clipboard.ClipboardType
import dev.cwby.clipboard.setClipboardContent
import dev.cwby.editor.TextBuffer
import dev.cwby.editor.TextInteractionMode
import dev.cwby.editor.TextInteractionMode.*

object VimTextOperations {
  private case class BufferPos(y: Int, x: Int)

  private def comparePos(a: BufferPos, b: BufferPos): Int = {
    if a.y != b.y then Integer.compare(a.y, b.y)
    else Integer.compare(a.x, b.x)
  }

  private def isWithinInclusive(open: BufferPos, cursor: BufferPos, close: BufferPos): Boolean = {
    comparePos(open, cursor) <= 0 && comparePos(cursor, close) <= 0
  }

  def wordBoundsForward(b: TextBuffer): (Int, Int) =
    val currentLine = b.getCurrentLine()
    val len = currentLine.length()
    val start = Math.max(0, Math.min(b.cursorX, len))
    var end = start
    while end < len && Character.isLetterOrDigit(currentLine.charAt(end)) do end += 1
    while end < len && !Character.isLetterOrDigit(currentLine.charAt(end)) do end += 1
    (start, end)

  def wordBoundsBackward(b: TextBuffer): (Int, Int) =
    val currentLine = b.getCurrentLine()
    val len = currentLine.length()
    val end = Math.max(0, Math.min(b.cursorX, len))
    var start = end
    while start > 0 && !Character.isLetterOrDigit(currentLine.charAt(start - 1)) do start -= 1
    while start > 0 && Character.isLetterOrDigit(currentLine.charAt(start - 1)) do start -= 1
    (start, end)

  def yankToClipboard(text: String): Unit =
    setClipboardContent(ClipboardType.INTERNAL, text)

  def stripTrailingPositionLine(text: String): String =
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

  def deleteRangeAndYank(b: TextBuffer, start: Int, end: Int): Unit =
    b.pushUndoState()
    val line = b.getCurrentLine()
    val s = Math.max(0, Math.min(start, line.length()))
    val e = Math.max(s, Math.min(end, line.length()))
    yankToClipboard(line.substring(s, e))
    line.delete(s, e)
    b.gotoPosition(s, b.cursorY)

  def deleteRange(b: TextBuffer, start: Int, end: Int): Unit =
    b.pushUndoState()
    val line = b.getCurrentLine()
    val s = Math.max(0, Math.min(start, line.length()))
    val e = Math.max(s, Math.min(end, line.length()))
    line.delete(s, e)
    b.gotoPosition(s, b.cursorY)

  def leadingWhitespace(line: StringBuilder): String =
    val s = line.toString
    var i = 0
    while i < s.length && (s.charAt(i) == ' ' || s.charAt(i) == '\t') do i += 1
    s.substring(0, i)

  def firstNonWhitespaceIndex(line: StringBuilder): Int =
    val s = line.toString
    var i = 0
    while i < s.length && (s.charAt(i) == ' ' || s.charAt(i) == '\t') do i += 1
    i

  private def isEscapedAt(line: StringBuilder, index: Int): Boolean =
    var i = index - 1
    var backslashes = 0
    while i >= 0 && line.charAt(i) == '\\' do
      backslashes += 1
      i -= 1
    (backslashes % 2) == 1

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

  private def deleteInsidePairAndYank(b: TextBuffer, openPos: BufferPos, closePos: BufferPos, switchMode: TextInteractionMode => Unit): Unit =
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

  def changeInsideTag(b: TextBuffer, switchMode: TextInteractionMode => Unit): Unit =
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

    switchMode(INSERT)

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

  private def deleteInsideQuoteAndYank(b: TextBuffer, openPos: BufferPos, closePos: BufferPos, switchMode: TextInteractionMode => Unit): Unit =
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

  def changeInsideDelimiter(b: TextBuffer, open: Char, close: Char, switchMode: TextInteractionMode => Unit): Unit =
    if b == null then return

    if open == close then
      findEnclosingQuoteInBuffer(b, open) match
        case Some((openPos, closePos)) =>
          deleteInsideQuoteAndYank(b, openPos, closePos, switchMode)
        case None =>
          ()
    else
      findEnclosingPairInBuffer(b, open, close) match
        case Some((openPos, closePos)) =>
          deleteInsidePairAndYank(b, openPos, closePos, switchMode)
        case None =>
          ()
}
