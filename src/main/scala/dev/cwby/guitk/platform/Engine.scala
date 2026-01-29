package dev.cwby.guitk.platform

import dev.cwby.WindowManager
import dev.cwby.clipboard.{ClipboardType, setClipboardContent}
import dev.cwby.guitk.bindings.opengl.GLConstants.*
import dev.cwby.guitk.bindings.opengl.gl
import dev.cwby.guitk.bindings.sdl.*
import dev.cwby.guitk.bindings.sdl.SDLConstants.*
import dev.cwby.guitk.renderer.{Renderer2D, OpenGLRenderer}
import dev.cwby.editor.input.GlobalKeyHandler
import dev.cwby.lsp.LSPManager

import scala.compiletime.uninitialized
import scala.scalanative.unsafe.*

object Engine {
  private var window: SDL_Window = null
  private var width: Int = 1280
  private var height: Int = 720
  private var shouldClose: Boolean = false

  private val keyHandler = GlobalKeyHandler

  inline def getWidth: Int = width

  inline def getHeight: Int = height

  inline def getWindow: SDL_Window = window

  inline def requestClose(): Unit = shouldClose = true

  private inline def createWindow(): Unit = {
    if !SDL.init(INIT_VIDEO) then
      throw IllegalStateException("Unable to initialize SDL")

    val flags = WINDOW_OPENGL | WINDOW_BORDERLESS | WINDOW_RESIZABLE
    window = SDLVideo.createWindow("ForgeBorn", width, height, flags)

    if window == null then
      throw RuntimeException("Failed to create SDL window")
  }

  private inline def createRenderer(): OpenGLRenderer = {
    val ctx = SDLGL.SDL_GL_CreateContext(window)
    SDLGL.SDL_GL_MakeCurrent(window, ctx)

    OpenGLRenderer(Renderer2D(width, height))
  }

  private inline def handleEvents(renderer: OpenGLRenderer, event: Ptr[SDL_Event]): Unit = {
    while (SDLEvents.SDL_PollEvent(event)) {
      SDLEventHelpers.getEventType(event) match {

        case EVENT_QUIT | EVENT_WINDOW_CLOSE_REQUESTED =>
          shouldClose = true
          LSPManager.closeAllLsp()

        case EVENT_KEY_DOWN =>
          keyHandler.handle(event)

        case EVENT_TEXT_INPUT =>
          keyHandler.handleInput(event)

        case EVENT_CLIPBOARD_UPDATE =>
          setClipboardContent(
            ClipboardType.SYSTEM,
            SDLClipboard.SDL_GetClipboardText()
          )

        case EVENT_WINDOW_RESIZED =>
          val (w, h) = SDLVideo.SDL_GetWindowSizeInPixels(window)
          width = w
          height = h
          renderer.onResize(w, h)
          WindowManager.resizeFloatingWindows(w, h)
        case _ => ()
      }
    }
  }

  private inline def renderFrame(renderer: OpenGLRenderer): Unit = {
    gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    renderer.render(width, height)
    SDLGL.SDL_GL_SwapWindow(window)
  }

  private inline def mainLoop(renderer: OpenGLRenderer, event: Ptr[SDL_Event]): Unit = {
    while !shouldClose do
      handleEvents(renderer, event)
      renderFrame(renderer)
  }

  inline def run(): Unit = {
    createWindow()
    val renderer = createRenderer()
    val event = stackalloc[SDL_Event](1)
    mainLoop(renderer, event)
    SDL.quit()
  }
}
