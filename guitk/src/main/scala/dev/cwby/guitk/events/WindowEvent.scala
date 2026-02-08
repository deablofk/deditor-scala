package dev.cwby.guitk.events

import dev.cwby.guitk.components.Window

sealed trait WindowEvent extends Event {
  def window: Window
  def timestamp: Long
}

final case class WindowOpenEvent(
    window: Window,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent

final case class WindowCloseEvent(
    window: Window,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent

final case class WindowResizeEvent(
    window: Window,
    oldWidth: Float,
    oldHeight: Float,
    newWidth: Float,
    newHeight: Float,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent

final case class WindowFocusEvent(
    window: Window,
    gained: Boolean,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent

final case class WindowMoveEvent(
    window: Window,
    oldX: Float,
    oldY: Float,
    newX: Float,
    newY: Float,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent

final case class WindowVisibilityEvent(
    window: Window,
    visible: Boolean,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent
