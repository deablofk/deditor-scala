package dev.cwby.editor.input

import dev.cwby.WindowManager
import dev.cwby.editor.core.given
import dev.cwby.clearCommandBuffer
import dev.cwby.clipboard.ClipboardType
import dev.cwby.clipboard.getClipboardContent
import dev.cwby.clipboard.setClipboardContent
import dev.cwby.commandHandlerState
import dev.cwby.editor.core.TextBuffer
import dev.cwby.editor.core.TextInteractionMode
import dev.cwby.editor.core.TextInteractionMode.*
import dev.cwby.executeCommand
import dev.cwby.getBufferMode
import dev.cwby.getCommandBuffer
import dev.cwby.guitk.text.FontManager
import dev.cwby.editor.renderer.EditorRenderer
import dev.cwby.guitk.components.{TiledWindow, Window}
import dev.cwby.editor.components.TelescopeWindow
import dev.cwby.lsp.CompletionItemKind
import dev.cwby.lsp.LSPManager
import dev.cwby.setBufferMode

object VimKeybindingRegistry {
  def registerAllKeybindings(
    switchMode: TextInteractionMode => Unit,
    yankToClipboard: String => Unit,
    stripTrailingPositionLine: String => String,
    deleteRangeAndYank: (TextBuffer, Int, Int) => Unit,
    deleteRange: (TextBuffer, Int, Int) => Unit,
    wordBoundsForward: TextBuffer => (Int, Int),
    wordBoundsBackward: TextBuffer => (Int, Int),
    changeInsideDelimiter: (TextBuffer, Char, Char) => Unit,
    changeInsideTag: TextBuffer => Unit,
    leadingWhitespace: StringBuilder => String,
    firstNonWhitespaceIndex: StringBuilder => Int
  ): Unit = {
    registerCommandMappings(switchMode)
    registerSearchMappings(switchMode)
    registerNormalMappings(
      switchMode,
      yankToClipboard,
      deleteRangeAndYank,
      wordBoundsForward,
      wordBoundsBackward,
      changeInsideDelimiter,
      changeInsideTag,
      leadingWhitespace,
      firstNonWhitespaceIndex
    )
    registerSelectMappings(switchMode, stripTrailingPositionLine, firstNonWhitespaceIndex)
    registerInsertMappings(switchMode, wordBoundsBackward, deleteRange)
  }

  private def registerCommandMappings(switchMode: TextInteractionMode => Unit): Unit =
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

  private def registerSearchMappings(switchMode: TextInteractionMode => Unit): Unit =
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

  private def registerNormalMappings(
    switchMode: TextInteractionMode => Unit,
    yankToClipboard: String => Unit,
    deleteRangeAndYank: (TextBuffer, Int, Int) => Unit,
    wordBoundsForward: TextBuffer => (Int, Int),
    wordBoundsBackward: TextBuffer => (Int, Int),
    changeInsideDelimiter: (TextBuffer, Char, Char) => Unit,
    changeInsideTag: TextBuffer => Unit,
    leadingWhitespace: StringBuilder => String,
    firstNonWhitespaceIndex: StringBuilder => Int
  ): Unit =
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
        GlobalKeyHandler.startVisualX = b.cursorX
        GlobalKeyHandler.startVisualY = b.cursorY
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
          case dired: dev.cwby.editor.components.DiredWindow =>
            dired.close()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "g r",
      (w, _) => {
        w match
          case dired: dev.cwby.editor.components.DiredWindow =>
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
            case dired: dev.cwby.editor.components.DiredWindow =>
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
    val wordSearchAction: (Window[TextBuffer], TextBuffer) => Unit = (w, b) => {
      if b != null && b.beginSearchForWordUnderCursor() then
        w.ensureCursorVisible(b)
        w.ensureCursorVisibleHorizontal(b)
    }

    KeybindingTrie.nmap("#", wordSearchAction)
    KeybindingTrie.nmap("*", wordSearchAction)

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
          case tiledWindow: TiledWindow[TextBuffer] =>
            tiledWindow.moveLeft()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w l",
      (w, _) => {
        w match
          case tiledWindow: TiledWindow[TextBuffer] =>
            tiledWindow.moveRight()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w j",
      (w, _) => {
        w match
          case tiledWindow: TiledWindow[TextBuffer] =>
            tiledWindow.moveDown()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w k",
      (w, _) => {
        w match
          case tiledWindow: TiledWindow[TextBuffer] =>
            tiledWindow.moveUp()
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w v",
      (w, _) => {
        w match
          case _: TiledWindow[TextBuffer] =>
            executeCommand("vs")
          case _ =>
      }
    )

    KeybindingTrie.nmap(
      "CTRL-w s",
      (w, _) => {
        w match
          case _: TiledWindow[TextBuffer] =>
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
          EditorRenderer.getCurrentTextBuffer().gotoPosition(x, y)
        else
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

  private def registerSelectMappings(
    switchMode: TextInteractionMode => Unit,
    stripTrailingPositionLine: String => String,
    firstNonWhitespaceIndex: StringBuilder => Int
  ): Unit =
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
        val region = b.getRegion(GlobalKeyHandler.startVisualX, GlobalKeyHandler.startVisualY, b.cursorX, b.cursorY)
        setClipboardContent(ClipboardType.INTERNAL, stripTrailingPositionLine(region))
        switchMode(NAVIGATION)
      }
    )
    KeybindingTrie.smap(
      "d",
      (_, b) => {
        val region = b.getRegion(GlobalKeyHandler.startVisualX, GlobalKeyHandler.startVisualY, b.cursorX, b.cursorY)
        setClipboardContent(ClipboardType.INTERNAL, stripTrailingPositionLine(region))
        b.deleteRegion(GlobalKeyHandler.startVisualX, GlobalKeyHandler.startVisualY, b.cursorX, b.cursorY)
        switchMode(NAVIGATION)
      }
    )

  private def registerInsertMappings(
    switchMode: TextInteractionMode => Unit,
    wordBoundsBackward: TextBuffer => (Int, Int),
    deleteRange: (TextBuffer, Int, Int) => Unit
  ): Unit =
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
          case dired: dev.cwby.editor.components.DiredWindow =>
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
}
