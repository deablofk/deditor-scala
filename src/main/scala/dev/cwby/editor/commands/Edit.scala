package dev.cwby.editor.commands

import dev.cwby.BufferManager
import dev.cwby.WindowManager
import dev.cwby.editor.components.TextComponent

def runEdit(args: Array[String]): Boolean = {
  if (args.length < 2) {
    println("Specify the File Path")
    return false
  }
  val window = WindowManager.getCurrentWindow
  val buffer = BufferManager.openFileBuffer(args(1))
  buffer.moveCursor(0, 0)
  window.resetScroll()
  window.component = TextComponent().setBuffer(buffer)
  true
}
