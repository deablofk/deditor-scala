package dev.cwby.commands

import dev.cwby.WindowManager
import dev.cwby.graphics.layout.component.TelescopeWindow
import dev.cwby.editor.input.GlobalKeyHandler

private val telescopeWindow: TelescopeWindow = TelescopeWindow(0, 0, 400, 400)

def runTelescopeFiles(args: Array[String]): Boolean = {
  telescopeWindow.openFilesPicker()
  WindowManager.showFloatingWindow(telescopeWindow)
  WindowManager.openFloatingWindow(telescopeWindow)
  GlobalKeyHandler.setMode(dev.cwby.editor.TextInteractionMode.SEARCH)
  true
}

def runTelescopeGrep(args: Array[String]): Boolean = {
  telescopeWindow.openGrepPicker()
  WindowManager.showFloatingWindow(telescopeWindow)
  WindowManager.openFloatingWindow(telescopeWindow)
  GlobalKeyHandler.setMode(dev.cwby.editor.TextInteractionMode.SEARCH)
  true
}
