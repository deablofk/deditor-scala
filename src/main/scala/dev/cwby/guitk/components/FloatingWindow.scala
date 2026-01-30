package dev.cwby.guitk.components

import dev.cwby.WindowManager

abstract class FloatingWindow[Buffer](
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    val sizeFactor: Float = 1.0f
) extends Window[Buffer]("", x, y, width, height) {

  def show(x: Float, y: Float): Unit = {

    this.x = x
    this.y = y
    this.visible = true
  }

  def show(x: Float, y: Float, width: Float, height: Float): Unit = {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
    this.visible = true
  }

  override def close(): Unit = {
    WindowManager.closeFloatingWindow(this.asInstanceOf[FloatingWindow[dev.cwby.editor.core.TextBuffer]])
    onClose()
  }
}
