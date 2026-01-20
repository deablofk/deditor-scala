package dev.cwby.commands

import dev.cwby.WindowManager
import dev.cwby.terminal.TerminalWindow

private val terminalWindow: TerminalWindow = TerminalWindow(0, 0, 640, 480)

def runTerminal(args: Array[String]): Boolean = {
  terminalWindow.open()
  WindowManager.showFloatingWindow(terminalWindow)
  WindowManager.openFloatingWindow(terminalWindow)
  true
}
