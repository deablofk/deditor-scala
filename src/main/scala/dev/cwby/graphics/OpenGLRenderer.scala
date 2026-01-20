package dev.cwby.graphics

import dev.cwby.WindowManager
import dev.cwby.editor.TextBuffer
import dev.cwby.editor.TextInteractionMode
import dev.cwby.getBufferMode
import dev.cwby.getCommandBuffer
import dev.cwby.getConfig
import dev.cwby.graphics.layout.SPLIT_HORIZONTAL
import dev.cwby.graphics.layout.SPLIT_VERTICAL
import dev.cwby.graphics.layout.TiledWindow
import dev.cwby.graphics.layout.component.TextComponent
import dev.cwby.graphics.opengl.Renderer2D

object OpenGLRenderer {
  def getCurrentTextBuffer(): TextBuffer = {
    WindowManager.getCurrentWindow.component match {
      case textComponent: TextComponent =>
        textComponent.getBuffer
      case telescopeComponent: dev.cwby.graphics.layout.component.TelescopeComponent =>
        telescopeComponent.getResultsBuffer()
      case _ =>
        null
    }
  }
}

class OpenGLRenderer {
  private var currentWidth: Int            = Engine.getWidth
  private var currentHeight: Int           = Engine.getHeight
  private val renderer: Renderer2D         = Renderer2D(currentWidth, currentHeight)
  private val windowBorderColor: Int       = 0xff3a3a3a
  private val windowBorderThickness: Float = 2.0f

  def onResize(width: Int, height: Int): Unit = {
    currentWidth = width
    currentHeight = height
    renderer.updateProjection(width, height)
    val root = WindowManager.getRootNode
    if root != null then
      val usableHeight = height - FontManager.getLineHeight()
      root.updateSize(0, 0, width, usableHeight)
  }

  def renderStatusLine(x: Float, y: Float, width: Float, height: Float): Unit = {
    renderer.pushClip(x, y, width, height)
    renderer.drawRect(x, y, width, height, 0xff000000) // Black background

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
    renderer.clear(0xff1b1b1b) // Match theme background
    renderer.startFrame()

    // Ensure layout reflects latest surface size even if resize events are missed
    val root = WindowManager.getRootNode
    if root != null then
      val usableHeight = height - FontManager.getLineHeight()
      root.updateSize(0, 0, width, usableHeight)

    renderTiledWindows(WindowManager.getRootNode)
    renderStatusLine(0, height - FontManager.getLineHeight(), width, FontManager.getLineHeight())
    renderFloatingWindows()
    renderAutoCompleteWindow()

    renderer.endFrame()

    // Flush pending font texture updates after frame completes to avoid blinks
    FontManager.getDefaultFont().flushPendingUpdates()
  }

  def renderTiledWindows(node: TiledWindow): Unit = {
    if (node.isLeaf) {
      if (node.component != null) {
        node.component match {
          case textComponent: dev.cwby.graphics.layout.component.TextComponent =>
            textComponent.setRenderWindow(node)
          case _ =>
        }
        node.component.render(renderer, node.x, node.y, node.width, node.height)
      }
    } else {
      renderTiledWindows(node.leftChild)
      renderTiledWindows(node.rightChild)

      // Draw a single shared border along the split (on top of children) to avoid duplicates
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
      case textComponent: dev.cwby.graphics.layout.component.TextComponent =>
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
          case textComponent: dev.cwby.graphics.layout.component.TextComponent =>
            textComponent.setRenderWindow(window)
          case telescopeComponent: dev.cwby.graphics.layout.component.TelescopeComponent =>
            telescopeComponent.setRenderWindow(window)
          case _ =>
        }
        window.component.render(renderer, window.x, window.y, window.width, window.height)

        // Draw border around the floating window (all sides)
        renderer.drawRect(window.x, window.y, window.width, windowBorderThickness, windowBorderColor) // top
        renderer.drawRect(
          window.x,
          window.y + window.height - windowBorderThickness,
          window.width,
          windowBorderThickness,
          windowBorderColor
        )                                                                                              // bottom
        renderer.drawRect(window.x, window.y, windowBorderThickness, window.height, windowBorderColor) // left
        renderer.drawRect(
          window.x + window.width - windowBorderThickness,
          window.y,
          windowBorderThickness,
          window.height,
          windowBorderColor
        ) // right
      }
    }
  }

  def cleanup(): Unit = {
    renderer.cleanup()
  }
}
