package dev.cwby.guitk.components

trait WindowCallbacks {
  def onWindowClosed(window: Window): Unit
  def onWindowFocused(window: Window): Unit
  def onRootWindowClosed(): Unit
  def onRootNodeChanged(newRoot: TiledWindow): Unit
}

object WindowCallbacks {
  def empty: WindowCallbacks = new WindowCallbacks {
    def onWindowClosed(window: Window): Unit = ()
    def onWindowFocused(window: Window): Unit = ()
    def onRootWindowClosed(): Unit = ()
    def onRootNodeChanged(newRoot: TiledWindow): Unit = ()
  }
}
