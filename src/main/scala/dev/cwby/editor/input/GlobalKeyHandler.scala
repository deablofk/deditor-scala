package dev.cwby.editor.input

import dev.cwby.WindowManager
import dev.cwby.editor.TextBuffer
import dev.cwby.editor.TextInteractionMode
import dev.cwby.editor.TextInteractionMode.*
import dev.cwby.getBufferMode
import dev.cwby.guitk.platform.Engine
import dev.cwby.guitk.renderer.OpenGLRenderer
import dev.cwby.guitk.components.Window
import dev.cwby.guitk.bindings.sdl.SDLKeyboard
import dev.cwby.setBufferMode

object GlobalKeyHandler extends BaseInputHandler {
  var startVisualX: Int = 0
  var startVisualY: Int = 0

  registerAllKeybindings()

  override protected def getCurrentMode: TextInteractionMode = getBufferMode
  
  override protected def getCurrentWindow: Window = WindowManager.getCurrentWindow
  
  override protected def getCurrentBuffer: TextBuffer = OpenGLRenderer.getCurrentTextBuffer()

  override protected def switchMode(mode: TextInteractionMode): Unit = {
    if getBufferMode == mode then return
    if mode == INSERT || mode == COMMAND || mode == SEARCH then SDLKeyboard.SDL_StartTextInput(Engine.getWindow)
    else SDLKeyboard.SDL_StopTextInput(Engine.getWindow)
    setBufferMode(mode)
  }

  def setMode(mode: TextInteractionMode): Unit = switchMode(mode)

  override protected def registerAllKeybindings(): Unit = {
    VimKeybindingRegistry.registerAllKeybindings(
      switchMode,
      VimTextOperations.yankToClipboard,
      VimTextOperations.stripTrailingPositionLine,
      VimTextOperations.deleteRangeAndYank,
      VimTextOperations.deleteRange,
      VimTextOperations.wordBoundsForward,
      VimTextOperations.wordBoundsBackward,
      (b, open, close) => VimTextOperations.changeInsideDelimiter(b, open, close, switchMode),
      b => VimTextOperations.changeInsideTag(b, switchMode),
      VimTextOperations.leadingWhitespace,
      VimTextOperations.firstNonWhitespaceIndex
    )
  }
}
