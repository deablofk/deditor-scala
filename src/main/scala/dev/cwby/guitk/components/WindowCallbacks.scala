package dev.cwby.guitk.components

trait WindowCallbacks[Buffer] {
  def onWindowClosed(window: Window[Buffer]): Unit
  def onWindowFocused(window: Window[Buffer]): Unit
  def onRootWindowClosed(): Unit
}

object WindowCallbacks {
  def empty[Buffer]: WindowCallbacks[Buffer] = new WindowCallbacks[Buffer] {
    def onWindowClosed(window: Window[Buffer]): Unit = ()
    def onWindowFocused(window: Window[Buffer]): Unit = ()
    def onRootWindowClosed(): Unit = ()
  }
}
