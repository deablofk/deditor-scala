package dev.cwby.graphics

import dev.cwby.WindowManager
import dev.cwby.bindings.GLConstants.*
import dev.cwby.bindings.gl
import dev.cwby.clipboard.ClipboardType
import dev.cwby.clipboard.setClipboardContent
import dev.cwby.graphics.OpenGLRenderer
import dev.cwby.guitk.bindings.sdl.{SDL, SDLClipboard, SDLEventHelpers, SDLEvents, SDLGL, SDLVideo, SDL_Event, SDL_Window}
import dev.cwby.guitk.bindings.sdl.SDLConstants.*
import dev.cwby.input.GlobalKeyHandler
import dev.cwby.input.IKeyHandler
import dev.cwby.lsp.LSPManager

import scala.compiletime.uninitialized
import scala.scalanative.unsafe.*
import scala.scalanative.unsafe.Size.intToSize

object Engine {
  private var window: SDL_Window = uninitialized
  private var width: Int = 1280
  private var height: Int = 720
  private var shouldClose: Boolean = false

  def getWidth: Int = width

  def getHeight: Int = height

  def getWindow: SDL_Window = window

  def setShouldClose(shouldClose: Boolean): Unit = {
    Engine.shouldClose = shouldClose
  }
}

class Engine {
  private val keyHandler: IKeyHandler = GlobalKeyHandler()

  inline def initSDL(): Unit = {
    if (!SDL.init(INIT_VIDEO)) {
      throw IllegalStateException("Unable to initialize SDL")
    }

    Engine.window = SDLVideo.createWindow("ForgeBorn", Engine.width, Engine.height, WINDOW_OPENGL | WINDOW_BORDERLESS | WINDOW_RESIZABLE)

    if (Engine.window == null) {
      throw RuntimeException("Failed to create the SDL window")
    }

    val context = SDLGL.SDL_GL_CreateContext(Engine.window)
    SDLGL.SDL_GL_MakeCurrent(Engine.window, context)

    val renderer = OpenGLRenderer()
    val event = stackalloc[SDL_Event](1)

    while (!Engine.shouldClose) {
      // handle events
      while (SDLEvents.SDL_PollEvent(event)) {
        val eventType = SDLEventHelpers.getEventType(event)

        eventType match {
          case EVENT_WINDOW_CLOSE_REQUESTED | EVENT_QUIT =>
            Engine.shouldClose = true
            LSPManager.closeAllLsp()
          case EVENT_KEY_DOWN =>
            keyHandler.handle(event)
          case EVENT_TEXT_INPUT =>
            keyHandler.handleInput(event)
          case EVENT_CLIPBOARD_UPDATE =>
            val clipboardText = SDLClipboard.SDL_GetClipboardText()
            if (clipboardText != "") {
              setClipboardContent(ClipboardType.SYSTEM, clipboardText)
            }
          case EVENT_WINDOW_RESIZED =>
            val (w, h) = SDLVideo.SDL_GetWindowSizeInPixels(Engine.window)
            println(s"Window resized to $w x $h")
            Engine.width = w
            Engine.height = h
            renderer.onResize(Engine.width, Engine.height)
            WindowManager.resizeFloatingWindows(Engine.width, Engine.height)
          case _ => ()
        }
      }

      // render
      gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
      renderer.render(Engine.width, Engine.height)

      SDLGL.SDL_GL_SwapWindow(Engine.window)

    }
    SDL.quit()
  }
}
