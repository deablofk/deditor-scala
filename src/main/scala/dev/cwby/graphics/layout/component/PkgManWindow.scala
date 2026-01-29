package dev.cwby.graphics.layout.component

import dev.cwby.BufferManager
import dev.cwby.editor.TextBuffer
import dev.cwby.guitk.components.FloatingWindow
import dev.cwby.graphics.layout.component.TextComponent
import dev.cwby.pkgs.PackageManager

class PkgManWindow(x: Float, y: Float, width: Float, height: Float) extends FloatingWindow(x, y, width, height, 0.9f) {

  private val buffer: TextBuffer = BufferManager.addEmptyBuffer()

  this.component = new TextComponent().setBuffer(buffer)
  initializeBuffer()

  def initializeBuffer(): Unit = {
    val newLines = scala.collection.mutable.ArrayBuffer[StringBuilder]()
    for (packageData <- PackageManager.getPackages()) {
      newLines += new StringBuilder(packageData.name + " | installed: " + packageData.isInstalled)
    }
    buffer.setLines(newLines.toArray)
  }

  override def onTrigger(): Unit = {
    close()
    val packageName = select().split(" | ")(0)
    PackageManager.installPackage(packageName)
  }

  // return a path to a file
  def select(): String = {
    hide()
    buffer.getCurrentLine().toString()
  }
}
