package dev.cwby.guitk.renderer

import dev.cwby.WindowManager
import dev.cwby.editor.core.TextBuffer
import dev.cwby.editor.core.TextInteractionMode
import dev.cwby.getBufferMode
import dev.cwby.getCommandBuffer
import dev.cwby.getConfig
import dev.cwby.guitk.components.SPLIT_HORIZONTAL
import dev.cwby.guitk.components.SPLIT_VERTICAL
import dev.cwby.guitk.components.TiledWindow
import dev.cwby.editor.components.TextComponent
import dev.cwby.guitk.text.FontManager

object OpenGLRenderer {
  def getCurrentTextBuffer(): TextBuffer = {
    WindowManager.getCurrentWindow.component match {
      case textComponent: TextComponent =>
        textComponent.getBuffer
      case telescopeComponent: dev.cwby.editor.components.TelescopeComponent =>
        telescopeComponent.getResultsBuffer()
      case _ =>
        null
    }
  }
}

class OpenGLRenderer(renderer: Renderer2D) {
  private val windowBorderColor: Int       = 0xff3a3a3a
  private val windowBorderThickness: Float = 2.0f

  def onResize(width: Int, height: Int): Unit = {
    renderer.updateProjection(width, height)
    val root = WindowManager.getRootNode
    if root != null then
      val usableHeight = height - FontManager.getLineHeight()
      root.updateSize(0, 0, width, usableHeight)
  }

  def renderStatusLine(x: Float, y: Float, width: Float, height: Float): Unit = {
    renderer.pushClip(x, y, width, height)
    renderer.drawRect(x, y, width, height, 0xff000000)

    val buffer =
      try OpenGLRenderer.getCurrentTextBuffer()
      catch case _: Throwable => null

    val statusText =
      if (getBufferMode == TextInteractionMode.COMMAND) {
        ":" + getCommandBuffer
      } else if (getBufferMode == TextInteractionMode.SEARCH) {
        val query  = if buffer != null then buffer.getSearchQuery() else ""
        val status = if buffer != null then buffer.getSearchStatusText() else ""
        "/" + query + (if status.nonEmpty then " " + status else "")
      } else {
        val base   = getBufferMode.toString
        val status = if buffer != null then buffer.getSearchStatusText() else ""
        base + (if status.nonEmpty then " " + status else "")
      }

    val textColor = getConfig.treesitter.getOrElse("default", 0xffffffff)
    val font      = FontManager.getDefaultFont()
    renderer.beginTextBatch(font)
    renderer.drawText(statusText, 5, y + FontManager.getLineHeight() - 5, font, textColor)
    renderer.endTextBatch(font)
    renderer.popClip()
  }

  def render(width: Int, height: Int): Unit = {
    renderer.clear(0xff1b1b1b)
    renderer.startFrame()

    val root = WindowManager.getRootNode
    if root != null then
      val usableHeight = height - FontManager.getLineHeight()
      root.updateSize(0, 0, width, usableHeight)

    renderTiledWindows(WindowManager.getRootNode)
    renderStatusLine(0, height - FontManager.getLineHeight(), width, FontManager.getLineHeight())
    renderFloatingWindows()
    renderAutoCompleteWindow()

    renderer.endFrame()

    FontManager.getDefaultFont().flushPendingUpdates()
  }

  def renderTiledWindows(node: TiledWindow[TextBuffer]): Unit = {
    if (node.isLeaf) {
      if (node.component != null) {
        node.component match {
          case textComponent: dev.cwby.editor.components.TextComponent =>
            textComponent.setRenderWindow(node)
          case _ =>
        }
        node.component.render(renderer, node.x, node.y, node.width, node.height)
      }
    } else {
      renderTiledWindows(node.leftChild)
      renderTiledWindows(node.rightChild)

      if (node.splitType == SPLIT_VERTICAL) {
        val splitX = node.leftChild.x + node.leftChild.width - (windowBorderThickness / 2.0f)
        renderer.drawRect(splitX, node.y, windowBorderThickness, node.height, windowBorderColor)
      } else if (node.splitType == SPLIT_HORIZONTAL) {
        val splitY = node.leftChild.y + node.leftChild.height - (windowBorderThickness / 2.0f)
        renderer.drawRect(node.x, splitY, node.width, windowBorderThickness, windowBorderColor)
      }
    }
  }

  def renderAutoCompleteWindow(): Unit = {
    val cmpWindow = WindowManager.getAutoCompleteWindow
    if (!cmpWindow.isVisible) {
      return
    }
    cmpWindow.getComponent match {
      case textComponent: dev.cwby.editor.components.TextComponent =>
        textComponent.setRenderWindow(cmpWindow)
      case _ =>
    }
    cmpWindow.getComponent.render(renderer, cmpWindow.x, cmpWindow.y, cmpWindow.width, cmpWindow.height)
  }

  def renderFloatingWindows(): Unit = {
    val windows = WindowManager.getFloatingWindows
    if (windows.isEmpty) {
      return
    }

    for (window <- windows) {
      if (window.isVisible) {
        window.component match {
          case textComponent: dev.cwby.editor.components.TextComponent =>
            textComponent.setRenderWindow(window)
          case telescopeComponent: dev.cwby.editor.components.TelescopeComponent =>
            telescopeComponent.setRenderWindow(window)
          case _ =>
        }
        window.component.render(renderer, window.x, window.y, window.width, window.height)

        renderer.drawRect(window.x, window.y, window.width, windowBorderThickness, windowBorderColor)
        renderer.drawRect(
          window.x,
          window.y + window.height - windowBorderThickness,
          window.width,
          windowBorderThickness,
          windowBorderColor
        )
        renderer.drawRect(window.x, window.y, windowBorderThickness, window.height, windowBorderColor)
        renderer.drawRect(
          window.x + window.width - windowBorderThickness,
          window.y,
          windowBorderThickness,
          window.height,
          windowBorderColor
        )
      }
    }
  }

  def cleanup(): Unit = {
    renderer.cleanup()
  }
}
