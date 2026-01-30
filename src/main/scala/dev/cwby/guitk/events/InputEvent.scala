package dev.cwby.guitk.events

import dev.cwby.editor.core.TextInteractionMode

sealed trait InputEvent extends Event {
  def timestamp: Long
}

final case class KeyPressEvent(
    key: String,
    scancode: Int,
    keycode: Int,
    modifiers: Short,
    mode: TextInteractionMode,
    timestamp: Long = System.currentTimeMillis()
) extends InputEvent

final case class KeyReleaseEvent(
    key: String,
    scancode: Int,
    keycode: Int,
    modifiers: Short,
    timestamp: Long = System.currentTimeMillis()
) extends InputEvent

final case class TextInputEvent(
    text: String,
    timestamp: Long = System.currentTimeMillis()
) extends InputEvent

final case class MouseMoveEvent(
    x: Int,
    y: Int,
    deltaX: Int,
    deltaY: Int,
    timestamp: Long = System.currentTimeMillis()
) extends InputEvent

final case class MouseButtonEvent(
    button: Int,
    x: Int,
    y: Int,
    pressed: Boolean,
    clicks: Int,
    timestamp: Long = System.currentTimeMillis()
) extends InputEvent

final case class MouseWheelEvent(
    deltaX: Int,
    deltaY: Int,
    timestamp: Long = System.currentTimeMillis()
) extends InputEvent
