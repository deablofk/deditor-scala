package dev.cwby.commands

import dev.cwby.WindowManager
import dev.cwby.graphics.layout.component.PkgManWindow

private val pkgManWindow: PkgManWindow = PkgManWindow(0, 0, 400, 400)

def runPkgMan(args: Array[String]): Boolean = {
  WindowManager.showFloatingWindow(pkgManWindow)
  WindowManager.openFloatingWindow(pkgManWindow)
  true
}
