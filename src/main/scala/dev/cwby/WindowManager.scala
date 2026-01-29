package dev.cwby

import dev.cwby.BufferManager
import dev.cwby.graphics.Engine
import dev.cwby.graphics.FontManager
import dev.cwby.guitk.components.AutoCompleteWindow
import dev.cwby.guitk.components.FloatingWindow
import dev.cwby.guitk.components.TiledWindow
import dev.cwby.guitk.components.Window
import dev.cwby.graphics.layout.component.TextComponent

import scala.collection.mutable.ListBuffer
import scala.compiletime.uninitialized

object WindowManager {
  private var rootNode: TiledWindow =
    TiledWindow(0, 0, Engine.getWidth.toFloat, Engine.getHeight.toFloat - FontManager.getLineHeight(), null)
  this.rootNode.component = TextComponent().setBuffer(BufferManager.addEmptyBuffer())
  private var currentWindow: Window                       = rootNode
  private var currentTiledWindow: TiledWindow             = uninitialized
  private val autoCompleteWindow: AutoCompleteWindow      = AutoCompleteWindow(0, 0, 400, 0)
  private val floatingWindows: ListBuffer[FloatingWindow] = ListBuffer[FloatingWindow]()

  inline def setRootNode(rootNode: TiledWindow): Unit = this.rootNode = rootNode

  inline def getRootNode: TiledWindow = rootNode

  inline def getCurrentWindow: Window = currentWindow

  inline def getAutoCompleteWindow: AutoCompleteWindow = autoCompleteWindow

  inline def getFloatingWindows: ListBuffer[FloatingWindow] = floatingWindows

  def setCurrentWindow(currentWindow: Window): Unit = {
    currentWindow match {
      case tiledWindow: TiledWindow =>
        this.currentTiledWindow = tiledWindow
      case _ => ()
    }

    this.currentWindow = currentWindow
  }

  def openFloatingWindow(window: FloatingWindow): Unit = {
    window.visible = true
    currentWindow match {
      case tiled: TiledWindow =>
        currentTiledWindow = tiled
      case _ => ()
    }
    floatingWindows += window
    currentWindow = window
  }

  private def computeCenteredSize(
      window: FloatingWindow,
      viewWidth: Float,
      viewHeight: Float
  ): (Float, Float, Float, Float) = {
    val factor       = Math.max(0.05f, Math.min(1.0f, window.sizeFactor))
    val targetWidth  = viewWidth * factor
    val targetHeight = viewHeight * factor
    val x            = (viewWidth - targetWidth) / 2.0f
    val y            = (viewHeight - targetHeight) / 2.0f
    (x, y, targetWidth, targetHeight)
  }

  inline private def layoutFloatingWindow(window: FloatingWindow, viewWidth: Float, viewHeight: Float): Unit = {
    val (x, y, targetWidth, targetHeight) = computeCenteredSize(window, viewWidth, viewHeight)
    window.x = x
    window.y = y
    window.width = targetWidth
    window.height = targetHeight
  }

  def showFloatingWindow(window: FloatingWindow): Unit = {
    val (x, y, targetWidth, targetHeight) = computeCenteredSize(window, Engine.getWidth, Engine.getHeight)
    window.show(x, y, targetWidth, targetHeight)
  }

  def resizeFloatingWindows(viewWidth: Float, viewHeight: Float): Unit = {
    floatingWindows.foreach(layoutFloatingWindow(_, viewWidth, viewHeight))
  }

  def closeFloatingWindow(window: FloatingWindow): Unit = {
    window.visible = false
    floatingWindows -= window
    currentWindow =
      if floatingWindows.isEmpty then currentTiledWindow
      else window
  }
}
