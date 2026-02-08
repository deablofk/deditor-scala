package dev.cwby

import dev.cwby.BufferManager
import dev.cwby.guitk.platform.Engine
import dev.cwby.guitk.text.FontManager
import dev.cwby.editor.components.AutoCompleteWindow
import dev.cwby.editor.core.TextBuffer
import dev.cwby.guitk.components.{FloatingWindow, TiledWindow, Window, WindowCallbacks}
import dev.cwby.editor.components.TextComponent

import dev.cwby.lsp.LSPManager

import scala.collection.mutable.ListBuffer
import scala.compiletime.uninitialized

object WindowManager {
  private val windowCallbacks = new WindowCallbacks {
    def onWindowClosed(window: Window): Unit = {
      window match {
        case fw: FloatingWindow => closeFloatingWindow(fw)
        case _ => ()
      }
    }

    def onWindowFocused(window: Window): Unit = {
      setCurrentWindow(window)
    }

    def onRootWindowClosed(): Unit = {
      LSPManager.closeAllLsp()
      Engine.requestClose()
    }

    def onRootNodeChanged(newRoot: TiledWindow): Unit = {
      rootNode = newRoot
    }
  }

  private var rootNode: TiledWindow = {
    val node = TiledWindow(0, 0, Engine.getWidth.toFloat, Engine.getHeight.toFloat - FontManager.getLineHeight(), null)
    node.setCallbacks(windowCallbacks)
    node.component = TextComponent().setBuffer(BufferManager.addEmptyBuffer())
    node
  }
  private var currentWindow: Window           = rootNode
  private var currentTiledWindow: TiledWindow = uninitialized
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
    window.setCallbacks(windowCallbacks)
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
