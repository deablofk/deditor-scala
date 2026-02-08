package dev.cwby.guitk.platform

import dev.cwby.guitk.bindings.opengl.GLConstants.*
import dev.cwby.guitk.bindings.opengl.gl
import dev.cwby.guitk.bindings.sdl.*
import dev.cwby.guitk.bindings.sdl.SDLConstants.*
import dev.cwby.guitk.renderer.Renderer2D
import dev.cwby.guitk.input.IKeyHandler

import scala.compiletime.uninitialized
import scala.scalanative.unsafe.*

object Engine {
  private var window: SDL_Window = null
  private var width: Int = 1280
  private var height: Int = 720
  private var shouldClose: Boolean = false

  private var keyHandler: IKeyHandler = _
  private var onCloseCallback: () => Unit = () => ()
  private var onResizeCallback: (Int, Int) => Unit = (_, _) => ()
  private var onClipboardUpdateCallback: String => Unit = _ => ()
  private var onRenderCallback: (Int, Int) => Unit = (_, _) => ()

  inline def getWidth: Int = width

  inline def getHeight: Int = height

  inline def getWindow: SDL_Window = window

  inline def requestClose(): Unit = shouldClose = true

  def setKeyHandler(handler: IKeyHandler): Unit = {
    keyHandler = handler
  }

  def setOnCloseCallback(callback: () => Unit): Unit = {
    onCloseCallback = callback
  }

  def setOnResizeCallback(callback: (Int, Int) => Unit): Unit = {
    onResizeCallback = callback
  }

  def setOnClipboardUpdateCallback(callback: String => Unit): Unit = {
    onClipboardUpdateCallback = callback
  }

  def setOnRenderCallback(callback: (Int, Int) => Unit): Unit = {
    onRenderCallback = callback
  }

  private inline def createWindow(): Unit = {
    if !SDL.init(INIT_VIDEO) then
      throw IllegalStateException("Unable to initialize SDL")

    val flags = WINDOW_OPENGL | WINDOW_BORDERLESS | WINDOW_RESIZABLE
    window = SDLVideo.createWindow("ForgeBorn", width, height, flags)

    if window == null then
      throw RuntimeException("Failed to create SDL window")
  }

  private inline def createRenderer(): Renderer2D = {
    val ctx = SDLGL.SDL_GL_CreateContext(window)
    SDLGL.SDL_GL_MakeCurrent(window, ctx)

    Renderer2D(width, height)
  }

  private inline def handleEvents(renderer: Renderer2D, event: Ptr[SDL_Event]): Unit = {
    while (SDLEvents.SDL_PollEvent(event)) {
      SDLEventHelpers.getEventType(event) match {

        case EVENT_QUIT | EVENT_WINDOW_CLOSE_REQUESTED =>
          shouldClose = true
          if onCloseCallback != null then onCloseCallback()

        case EVENT_KEY_DOWN =>
          if keyHandler != null then keyHandler.handle(event)

        case EVENT_TEXT_INPUT =>
          if keyHandler != null then keyHandler.handleInput(event)

        case EVENT_CLIPBOARD_UPDATE =>
          if onClipboardUpdateCallback != null then
            onClipboardUpdateCallback(SDLClipboard.SDL_GetClipboardText())

        case EVENT_WINDOW_RESIZED =>
          val (w, h) = SDLVideo.SDL_GetWindowSizeInPixels(window)
          width = w
          height = h
          renderer.updateProjection(w, h)
          if onResizeCallback != null then onResizeCallback(w, h)
        case _ => ()
      }
    }
  }

  private inline def renderFrame(): Unit = {
    gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    if onRenderCallback != null then onRenderCallback(width, height)
    SDLGL.SDL_GL_SwapWindow(window)
  }

  def run(initCallback: Renderer2D => Unit): Unit = Zone {
    createWindow()
    val renderer = createRenderer()
    val event = stackalloc[SDL_Event](1)
    
    initCallback(renderer)
    
    while !shouldClose do
      handleEvents(renderer, event)
      renderFrame()
  }

  def shutdown(): Unit = {
    SDL.quit()
  }
}
