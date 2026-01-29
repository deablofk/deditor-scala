package dev.cwby.guitk.input

import dev.cwby.editor.TextBuffer
import dev.cwby.editor.TextInteractionMode
import dev.cwby.guitk.bindings.sdl.SDLConstants.*
import dev.cwby.guitk.bindings.sdl.{SDLEventHelpers, SDLKeyboard, SDL_Event}
import dev.cwby.guitk.components.Window

import scala.scalanative.unsafe.*

abstract class KeyHandler extends IKeyHandler {
  
  protected var modeNode: TrieNode = KeybindingTrie.getRoot(getCurrentMode)
  protected var anyNode: TrieNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
  protected var lastMode: TextInteractionMode = getCurrentMode
  var lastKeyPressTime: Long = 0

  protected def getCurrentMode: TextInteractionMode
  
  protected def getCurrentWindow: Window
  
  protected def getCurrentBuffer: TextBuffer

  protected def switchMode(mode: TextInteractionMode): Unit

  protected def registerAllKeybindings(): Unit

  protected def isModifierScancode(scancode: Int): Boolean = {
    scancode match
      case 224 | 225 | 226 | 227 | 228 | 229 | 230 | 231 => true
      case _ => false
  }

  protected def getKey(mod: Short, keyCode: Int, keyChar: Char): String =
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

  override def handle(event: Ptr[SDL_Event]): Unit = {
    val scancode = SDLEventHelpers.getKeyScancode(event)
    if isModifierScancode(scancode) then return

    val mod = SDLEventHelpers.getKeyMod(event)
    val keyCode = SDLEventHelpers.getKeyCode(event)
    val keyChar = SDLKeyboard.SDL_GetKeyFromScancode(scancode, mod, false).toChar
    val key = getKey(mod.toShort, keyCode, keyChar)

    val currentMode = getCurrentMode

    if currentMode != lastMode then
      lastMode = currentMode
      modeNode = KeybindingTrie.getRoot(currentMode)
      anyNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)

    val anyChild = anyNode.search(key)
    if anyChild != null then
      if anyChild.action != null then
        val window = getCurrentWindow
        val buffer = getCurrentBuffer
        anyChild.action(window, buffer)
        modeNode = KeybindingTrie.getRoot(getCurrentMode)
        anyNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
        KeybindingTrie.resetNumberInput()
        lastKeyPressTime = System.currentTimeMillis()
        return
      else
        anyNode = anyChild

    if currentMode == TextInteractionMode.NAVIGATION then
      if key.length == 1 && Character.isDigit(key.charAt(0)) && (key.charAt(0) != '0' || KeybindingTrie.getNumberInput() > 0) then
        KeybindingTrie.appendNumberInput(key.charAt(0))
        lastKeyPressTime = System.currentTimeMillis()
        return

    val child = modeNode.search(key)
    if child != null then
      modeNode = child
      if child.action != null then
        val window = getCurrentWindow
        val buffer = getCurrentBuffer
        
        val repeat = Math.max(1, KeybindingTrie.getNumberInput())
        var i = 0
        while i < repeat do
          child.action(window, buffer)
          i += 1

        modeNode = KeybindingTrie.getRoot(getCurrentMode)
        anyNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
        KeybindingTrie.resetNumberInput()
    else
      modeNode = KeybindingTrie.getRoot(getCurrentMode)
      anyNode = KeybindingTrie.getRoot(TextInteractionMode.ANY)
      KeybindingTrie.resetNumberInput()

    lastKeyPressTime = System.currentTimeMillis()
  }
}
