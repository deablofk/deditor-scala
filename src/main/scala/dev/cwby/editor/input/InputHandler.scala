package dev.cwby.editor.input

import dev.cwby.WindowManager
import dev.cwby.appendCommandBuffer
import dev.cwby.clipboard.ClipboardType
import dev.cwby.clipboard.getClipboardContent
import dev.cwby.editor.core.TextBuffer
import dev.cwby.editor.core.TextInteractionMode
import dev.cwby.editor.core.TextInteractionMode.*
import dev.cwby.getBufferMode
import dev.cwby.guitk.platform.Engine
import dev.cwby.guitk.text.FontManager
import dev.cwby.guitk.renderer.OpenGLRenderer
import dev.cwby.editor.components.TextComponent
import dev.cwby.editor.components.TelescopeWindow
import dev.cwby.guitk.bindings.sdl.SDLConstants.*
import dev.cwby.guitk.bindings.sdl.{SDLEventHelpers, SDL_Event}
import dev.cwby.guitk.input.KeyHandler
import dev.cwby.lsp.CompletionItemKind
import dev.cwby.lsp.LSPManager
import dev.cwby.terminal.TerminalWindow

import scala.scalanative.unsafe.*

abstract class BaseInputHandler extends KeyHandler {
  override def handle(event: Ptr[SDL_Event]): Unit = {
    WindowManager.getCurrentWindow match
      case tw: TerminalWindow =>
        val keyCode = SDLEventHelpers.getKeyCode(event)
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

    super.handle(event)
  }

  override def handleInput(event: Ptr[SDL_Event]): Unit = {
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
  }

  protected def caretXPxAt(buffer: TextBuffer, cursorX: Int): Int =
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

  protected def autoPairHandled(b: TextBuffer, c: Char): Boolean =
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

  protected def countUnescapedQuotesBeforeCursor(line: StringBuilder, cursorX: Int): Int =
    val end = Math.max(0, Math.min(cursorX, line.length()))
    var i = 0
    var count = 0
    while i < end do
      if line.charAt(i) == '"' && !isEscapedAt(line, i) then count += 1
      i += 1
    count

  protected def isEscapedAt(line: StringBuilder, index: Int): Boolean =
    var i = index - 1
    var backslashes = 0
    while i >= 0 && line.charAt(i) == '\\' do
      backslashes += 1
      i -= 1
    (backslashes % 2) == 1
}
