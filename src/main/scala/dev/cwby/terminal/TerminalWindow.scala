package dev.cwby.terminal

import dev.cwby.guitk.platform.Engine
import dev.cwby.guitk.components.FloatingWindow
import dev.cwby.guitk.bindings.sdl.SDLKeyboard

final class TerminalWindow(x: Float, y: Float, width: Float, height: Float)
    extends FloatingWindow[dev.cwby.editor.core.TextBuffer](x, y, width, height, 0.9f):

  private val defaultShell: String =
    sys.env
      .get("SHELL")
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse("/bin/sh")

  private val session = new TerminalSession(
    rows = 24,
    cols = 80,
    program = defaultShell,
    argv = Seq(defaultShell)
  )

  private val componentImpl = new TerminalComponent(session)
  this.component = componentImpl

  private var started: Boolean = false

  override def open(): Unit =
    if started then return
    started = true
    session.setOnExit(() => close())
    session.start()
    SDLKeyboard.SDL_StartTextInput(Engine.getWindow)

  override def onClose(): Unit =
    SDLKeyboard.SDL_StopTextInput(Engine.getWindow)
    session.close()
    started = false

  def getTerminalComponent(): TerminalComponent = componentImpl
