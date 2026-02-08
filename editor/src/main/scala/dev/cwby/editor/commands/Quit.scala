package dev.cwby.editor.commands

import dev.cwby.WindowManager

def runQuit(args: Array[String]): Boolean = {
  WindowManager.getCurrentWindow.close()
  true
}
