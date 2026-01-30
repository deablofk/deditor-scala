package dev.cwby.guitk.events

import dev.cwby.guitk.components.Window

sealed trait WindowEvent[Buffer] extends Event {
  def window: Window[Buffer]
  def timestamp: Long
}

final case class WindowOpenEvent[Buffer](
    window: Window[Buffer],
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent[Buffer]

final case class WindowCloseEvent[Buffer](
    window: Window[Buffer],
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent[Buffer]

final case class WindowResizeEvent[Buffer](
    window: Window[Buffer],
    oldWidth: Float,
    oldHeight: Float,
    newWidth: Float,
    newHeight: Float,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent[Buffer]

final case class WindowFocusEvent[Buffer](
    window: Window[Buffer],
    gained: Boolean,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent[Buffer]

final case class WindowMoveEvent[Buffer](
    window: Window[Buffer],
    oldX: Float,
    oldY: Float,
    newX: Float,
    newY: Float,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent[Buffer]

final case class WindowVisibilityEvent[Buffer](
    window: Window[Buffer],
    visible: Boolean,
    timestamp: Long = System.currentTimeMillis()
) extends WindowEvent[Buffer]
