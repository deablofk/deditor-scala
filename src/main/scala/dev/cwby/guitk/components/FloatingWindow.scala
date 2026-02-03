package dev.cwby.guitk.components

abstract class FloatingWindow[Buffer](
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    val sizeFactor: Float = 1.0f,
    private var callbacks: WindowCallbacks[Buffer] = WindowCallbacks.empty[Buffer]
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

  def setCallbacks(cb: WindowCallbacks[Buffer]): Unit = {
    this.callbacks = cb
  }

  override def close(): Unit = {
    callbacks.onWindowClosed(this)
    onClose()
  }
}
