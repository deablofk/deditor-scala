package dev.cwby.editor.components

import dev.cwby.editor.core.TextBuffer
import dev.cwby.guitk.components.Window
import dev.cwby.guitk.renderer.Renderer2D

import scala.compiletime.uninitialized

class TelescopeComponent(resultsBuffer: TextBuffer, previewBuffer: TextBuffer) extends IComponent {

  private val resultsText: TextComponent = new TextComponent().setBuffer(resultsBuffer)
  private val previewText: TextComponent = new TextComponent().setBuffer(previewBuffer)

  private var renderWindow: Window = uninitialized

  def setRenderWindow(window: Window): TelescopeComponent = {
    this.renderWindow = window
    resultsText.setRenderWindow(window)
    previewText.setRenderWindow(window)
    this
  }

  def getResultsBuffer(): TextBuffer = resultsBuffer

  def getPreviewBuffer(): TextBuffer = previewBuffer

  override def render(renderer: Renderer2D, x: Float, y: Float, width: Float, height: Float): Unit = {
    val dividerWidth = 1.0f
    val leftWidth    = (width * 0.45f).toFloat
    val rightWidth   = Math.max(0.0f, width - leftWidth - dividerWidth)

    val leftX  = x
    val rightX = x + leftWidth + dividerWidth

    resultsText.render(renderer, leftX, y, leftWidth, height)

    renderer.drawRect(x + leftWidth, y, dividerWidth, height, 0xff000000)

    previewText.render(renderer, rightX, y, rightWidth, height)
  }
}
