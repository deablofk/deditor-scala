package dev.cwby.editor.events

import dev.cwby.editor.core.TextBuffer
import dev.cwby.guitk.events.{EventDispatcher, WindowOpenEvent, WindowCloseEvent, WindowResizeEvent, WindowFocusEvent, WindowMoveEvent, WindowVisibilityEvent}

object EventLogger {
  
  def initialize(): Unit = {
    EventDispatcher.subscribe[WindowOpenEvent[TextBuffer]](logWindowOpen)
    EventDispatcher.subscribe[WindowCloseEvent[TextBuffer]](logWindowClose)
    EventDispatcher.subscribe[WindowResizeEvent[TextBuffer]](logWindowResize)
    EventDispatcher.subscribe[WindowFocusEvent[TextBuffer]](logWindowFocus)
    EventDispatcher.subscribe[WindowMoveEvent[TextBuffer]](logWindowMove)
    EventDispatcher.subscribe[WindowVisibilityEvent[TextBuffer]](logWindowVisibility)
    EventDispatcher.subscribe[CommandExecuteEvent](logCommandExecute)
    EventDispatcher.subscribe[CommandCompleteEvent](logCommandComplete)
    EventDispatcher.subscribe[CommandErrorEvent](logCommandError)
  }

  private def logWindowOpen(event: WindowOpenEvent[TextBuffer]): Unit = {
    println(s"[WINDOW] Opened: ${event.window.title} at (${event.window.x}, ${event.window.y})")
  }

  private def logWindowClose(event: WindowCloseEvent[TextBuffer]): Unit = {
    println(s"[WINDOW] Closed: ${event.window.title}")
  }

  private def logWindowResize(event: WindowResizeEvent[TextBuffer]): Unit = {
    println(s"[WINDOW] Resized: ${event.window.title} from ${event.oldWidth}x${event.oldHeight} to ${event.newWidth}x${event.newHeight}")
  }

  private def logWindowFocus(event: WindowFocusEvent[TextBuffer]): Unit = {
    val action = if event.gained then "gained" else "lost"
    println(s"[WINDOW] Focus $action: ${event.window.title}")
  }

  private def logWindowMove(event: WindowMoveEvent[TextBuffer]): Unit = {
    println(s"[WINDOW] Moved: ${event.window.title} from (${event.oldX}, ${event.oldY}) to (${event.newX}, ${event.newY})")
  }

  private def logWindowVisibility(event: WindowVisibilityEvent[TextBuffer]): Unit = {
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
