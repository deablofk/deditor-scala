package dev.cwby.graphics.layout

import dev.cwby.WindowManager
import dev.cwby.graphics.layout.Window

/** Base class for transient overlay windows that float above the tiled layout.
  *
  * @param x
  *   initial x coordinate
  * @param y
  *   initial y coordinate
  * @param width
  *   initial width
  * @param height
  *   initial height
  * @param sizeFactor
  *   fraction of the available viewport to occupy (0, 1]
  */
abstract class FloatingWindow(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    val sizeFactor: Float = 1.0f
) extends Window("", x, y, width, height) {

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
    WindowManager.closeFloatingWindow(this)
    onClose()
  }
}
