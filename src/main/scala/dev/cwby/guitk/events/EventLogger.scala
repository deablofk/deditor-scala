package dev.cwby.guitk.events

import dev.cwby.editor.events.{CommandExecuteEvent, CommandCompleteEvent, CommandErrorEvent}

object EventLogger {
  
  def initialize(): Unit = {
    EventDispatcher.subscribe[WindowOpenEvent](logWindowOpen)
    EventDispatcher.subscribe[WindowCloseEvent](logWindowClose)
    EventDispatcher.subscribe[WindowResizeEvent](logWindowResize)
    EventDispatcher.subscribe[WindowFocusEvent](logWindowFocus)
    EventDispatcher.subscribe[WindowMoveEvent](logWindowMove)
    EventDispatcher.subscribe[WindowVisibilityEvent](logWindowVisibility)
    EventDispatcher.subscribe[CommandExecuteEvent](logCommandExecute)
    EventDispatcher.subscribe[CommandCompleteEvent](logCommandComplete)
    EventDispatcher.subscribe[CommandErrorEvent](logCommandError)
  }

  private def logWindowOpen(event: WindowOpenEvent): Unit = {
    println(s"[WINDOW] Opened: ${event.window.title} at (${event.window.x}, ${event.window.y})")
  }

  private def logWindowClose(event: WindowCloseEvent): Unit = {
    println(s"[WINDOW] Closed: ${event.window.title}")
  }

  private def logWindowResize(event: WindowResizeEvent): Unit = {
    println(s"[WINDOW] Resized: ${event.window.title} from ${event.oldWidth}x${event.oldHeight} to ${event.newWidth}x${event.newHeight}")
  }

  private def logWindowFocus(event: WindowFocusEvent): Unit = {
    val action = if event.gained then "gained" else "lost"
    println(s"[WINDOW] Focus $action: ${event.window.title}")
  }

  private def logWindowMove(event: WindowMoveEvent): Unit = {
    println(s"[WINDOW] Moved: ${event.window.title} from (${event.oldX}, ${event.oldY}) to (${event.newX}, ${event.newY})")
  }

  private def logWindowVisibility(event: WindowVisibilityEvent): Unit = {
    val state = if event.visible then "visible" else "hidden"
    println(s"[WINDOW] Visibility changed: ${event.window.title} is now $state")
  }

  private def logCommandExecute(event: CommandExecuteEvent): Unit = {
    val argsStr = if event.args.length > 1 then event.args.tail.mkString(" ") else ""
    println(s"[COMMAND] Executing: ${event.commandName} $argsStr".trim)
  }

  private def logCommandComplete(event: CommandCompleteEvent): Unit = {
    val status = if event.success then "SUCCESS" else "FAILED"
    println(s"[COMMAND] Completed: ${event.commandName} [$status]")
  }

  private def logCommandError(event: CommandErrorEvent): Unit = {
    println(s"[COMMAND] Error in ${event.commandName}: ${event.error}")
  }
}
