package dev.cwby.graphics

import dev.cwby.WindowManager
import dev.cwby.bindings.GLConstants._
import dev.cwby.bindings.SDL3._
import dev.cwby.bindings.gl
import dev.cwby.clipboard.ClipboardType
import dev.cwby.clipboard.setClipboardContent
import dev.cwby.graphics.OpenGLRenderer
import dev.cwby.input.GlobalKeyHandler
import dev.cwby.input.IKeyHandler
import dev.cwby.lsp.LSPManager

import scala.compiletime.uninitialized
import scala.scalanative.unsafe._

object Engine {
  private var window: SDL_Window   = uninitialized
  private var width: Int           = 1280
  private var height: Int          = 720
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
    if (!SDLInit.SDL_Init(SDL_INIT_VIDEO)) {
      throw IllegalStateException("Unable to initialize SDL")
    }

    Zone {
      Engine.window = SDLVideo.SDL_CreateWindow(
        toCString("ForgeBorn"),
        Engine.width,
        Engine.height,
        SDL_WINDOW_OPENGL | SDL_WINDOW_BORDERLESS | SDL_WINDOW_RESIZABLE
      )
    }

    if (Engine.window == null) {
      throw RuntimeException("Failed to create the SDL window")
    }

    val context = SDLGL.SDL_GL_CreateContext(Engine.window)
    SDLGL.SDL_GL_MakeCurrent(Engine.window, context)

    val renderer = OpenGLRenderer()
    val event    = stackalloc[SDL_Event](1)

    while (!Engine.shouldClose) {
      // handle events
      while (SDLEvents.SDL_PollEvent(event)) {
        val eventType = SDLEventHelpers.getEventType(event)

        eventType match {
          case SDL_EVENT_WINDOW_CLOSE_REQUESTED | SDL_EVENT_QUIT =>
            Engine.shouldClose = true
            LSPManager.closeAllLsp()
          case SDL_EVENT_KEY_DOWN =>
            keyHandler.handle(event)
          case SDL_EVENT_TEXT_INPUT =>
            keyHandler.handleInput(event)
          case SDL_EVENT_CLIPBOARD_UPDATE =>
            val clipboardText = SDLClipboard.SDL_GetClipboardText()
            if (clipboardText != null) {
              setClipboardContent(ClipboardType.SYSTEM, fromCString(clipboardText))
            }
          case SDL_EVENT_WINDOW_RESIZED =>
            val w = stackalloc[CInt](1)
            val h = stackalloc[CInt](1)
            SDLVideo.SDL_GetWindowSizeInPixels(Engine.window, w, h)
            Engine.width = !w
            Engine.height = !h
            renderer.onResize(Engine.width, Engine.height)
            WindowManager.resizeFloatingWindows(Engine.width, Engine.height)
          case _ =>
            // Handle TEXT_INPUT events if they occur
            if (eventType == SDL_EVENT_TEXT_INPUT) {
              keyHandler.handleInput(event)
            }
        }
      }

      // render
      gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
      renderer.render(Engine.width, Engine.height)

      SDLGL.SDL_GL_SwapWindow(Engine.window)

    }
    SDLInit.SDL_Quit()
  }
}
