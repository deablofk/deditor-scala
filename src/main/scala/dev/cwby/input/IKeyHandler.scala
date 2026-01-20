package dev.cwby.input

import dev.cwby.bindings.SDL3.SDL_Event

import scala.scalanative.unsafe._

trait IKeyHandler {

  def handle(event: Ptr[SDL_Event]): Unit

  def handleInput(event: Ptr[SDL_Event]): Unit
}
