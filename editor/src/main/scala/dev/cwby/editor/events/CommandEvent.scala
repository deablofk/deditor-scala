package dev.cwby.editor.events

import dev.cwby.guitk.events.Event

sealed trait CommandEvent extends Event {
  def commandName: String
  def timestamp: Long
}

final case class CommandExecuteEvent(
    commandName: String,
    args: Array[String],
    timestamp: Long = System.currentTimeMillis()
) extends CommandEvent

final case class CommandCompleteEvent(
    commandName: String,
    args: Array[String],
    success: Boolean,
    timestamp: Long = System.currentTimeMillis()
) extends CommandEvent

final case class CommandErrorEvent(
    commandName: String,
    args: Array[String],
    error: String,
    timestamp: Long = System.currentTimeMillis()
) extends CommandEvent
