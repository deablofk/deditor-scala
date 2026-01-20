package dev.cwby.commands

import dev.cwby.WindowManager
import dev.cwby.graphics.layout.component.DiredWindow

private val dired: DiredWindow = DiredWindow(0, 0, 400, 400)

def runDired(args: Array[String]): Boolean = {
  if (args.length >= 2) {
    dired.openPath(args(1))
  }

  WindowManager.showFloatingWindow(dired)
  WindowManager.openFloatingWindow(dired)
  true
}
