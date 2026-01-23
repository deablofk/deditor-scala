package dev.cwby.guitk.bindings.sdl

import dev.cwby.guitk.bindings.sdl.internal.{SDLClipboardRaw, SDLEventHelpersRaw, SDLEventsRaw, SDLGLRaw, SDLInitRaw, SDLKeyboardRaw, SDLStdlib, SDLVideoRaw}

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

object SDLConstants {
  /**
   * Window Flags
   */
  inline val INIT_VIDEO = 0x00000020
  inline val WINDOW_OPENGL = 0x00000002
  inline val WINDOW_BORDERLESS = 0x00000010
  inline val WINDOW_RESIZABLE = 0x00000020

  /**
   * Event Types
   */
  inline val EVENT_QUIT = 0x100
  inline val EVENT_WINDOW_CLOSE_REQUESTED = 0x203
  inline val EVENT_WINDOW_RESIZED = 0x205
  inline val EVENT_KEY_DOWN = 0x300
  inline val EVENT_KEY_UP = 0x301
  inline val EVENT_TEXT_EDITING = 0x302
  inline val EVENT_TEXT_INPUT = 0x303
  inline val EVENT_CLIPBOARD_UPDATE = 0x900

  /**
   * KEYS
   */
  inline val K_ESCAPE = 27
  inline val K_RETURN = 13
  inline val K_BACKSPACE = 8
  inline val K_TAB = 9
  inline val K_SPACE = 32
  inline val K_DELETE = 127
  inline val K_UP = 1073741906
  inline val K_DOWN = 1073741905
  inline val K_LEFT = 1073741904
  inline val K_RIGHT = 1073741903
  inline val K_HOME = 1073741898
  inline val K_END = 1073741901
  inline val K_PAGEUP = 1073741899
  inline val K_PAGEDOWN = 1073741902

  /**
   * KEY MODIFIERS
   */
  inline val KMOD_NONE = 0x0000
  inline val KMOD_LSHIFT = 0x0001
  inline val KMOD_RSHIFT = 0x0002
  inline val KMOD_SHIFT = KMOD_LSHIFT | KMOD_RSHIFT
  inline val KMOD_LCTRL = 0x0040
  inline val KMOD_RCTRL = 0x0080
  inline val KMOD_CTRL = KMOD_LCTRL | KMOD_RCTRL
  inline val KMOD_LALT = 0x0100
  inline val KMOD_RALT = 0x0200
  inline val KMOD_ALT = KMOD_LALT | KMOD_RALT
}

type SDL_Window = Ptr[Byte]
type SDL_GLContext = Ptr[Byte]
type SDL_Event = CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]] // SDL3 event size is 128 bytes


object SDL {
  def init(flags: Int): Boolean = {
    SDLInitRaw.SDL_Init(flags.toUInt)
  }

  def quit(): Unit = {
    SDLInitRaw.SDL_Quit()
  }
}


object SDLVideo {
  def createWindow(title: String, width: Int, height: Int, flags: Int): SDL_Window = {
    Zone {
      SDLVideoRaw.SDL_CreateWindow(
        toCString(title),
        width,
        height,
        flags.toUInt
      )
    }
  }

  def SDL_GetWindowSizeInPixels(window: SDL_Window): (Int, Int) = {
    val w, h = stackalloc[CInt](1)

    if !SDLVideoRaw.SDL_GetWindowSizeInPixels(window, w, h) then
      throw new RuntimeException("Failed to get window size")

    (!w, !h)
  }

}

object SDLGL {
  def SDL_GL_CreateContext(window: SDL_Window): SDL_GLContext = {
    SDLGLRaw.SDL_GL_CreateContext(window)
  }

  def SDL_GL_MakeCurrent(window: SDL_Window, context: SDL_GLContext): Boolean = {
    SDLGLRaw.SDL_GL_MakeCurrent(window, context)
  }

  def SDL_GL_GetCurrentContext(): SDL_GLContext = {
    SDLGLRaw.SDL_GL_GetCurrentContext()
  }

  def SDL_GL_SetSwapInterval(interval: Int): Boolean = {
    SDLGLRaw.SDL_GL_SetSwapInterval(interval)
  }

  def SDL_GL_SwapWindow(window: SDL_Window): Boolean = {
    SDLGLRaw.SDL_GL_SwapWindow(window)
  }

  def SDL_GL_DestroyContext(context: SDL_GLContext): Unit = {
    SDLGLRaw.SDL_GL_DestroyContext(context)
  }
}

object SDLEvents {
  def SDL_PollEvent(event: Ptr[SDL_Event]): Boolean = {
    SDLEventsRaw.SDL_PollEvent(event)
  }

  def SDL_WaitEvent(event: Ptr[SDL_Event]): Boolean = {
    SDLEventsRaw.SDL_WaitEvent(event)
  }
}


object SDLClipboard {
  def SDL_GetClipboardText(): String = {
    val cstr = SDLClipboardRaw.SDL_GetClipboardText()
    if cstr == null then ""
    else {
      val s = fromCString(cstr)
      SDLStdlib.SDL_free(cstr)
      s
    }
  }

  def SDL_SetClipboardText(text: String): Boolean = {
    Zone {
      SDLClipboardRaw.SDL_SetClipboardText(toCString(text))
    }
  }

  def SDL_HasClipboardText(): Boolean = {
    SDLClipboardRaw.SDL_HasClipboardText()
  }
}

object SDLKeyboard {
  def SDL_StartTextInput(window: SDL_Window): Boolean = {
    SDLKeyboardRaw.SDL_StartTextInput(window)
  }

  def SDL_StopTextInput(window: SDL_Window): Boolean = {
    SDLKeyboardRaw.SDL_StopTextInput(window)
  }

  def SDL_GetKeyFromScancode(scancode: CInt, modstate: UShort, key_event: Boolean): CInt = {
    SDLKeyboardRaw.SDL_GetKeyFromScancode(scancode, modstate, key_event)
  }
}


object SDLEventHelpers {

  def getEventType(event: Ptr[SDL_Event]): UInt = {
    SDLEventHelpersRaw.getEventType(event)
  }

  def getTimestamp(event: Ptr[SDL_Event]): ULong = {
    SDLEventHelpersRaw.getTimestamp(event)
  }

  def getWindowData1(event: Ptr[SDL_Event]): Int = {
    SDLEventHelpersRaw.getWindowData1(event)
  }

  def getWindowData2(event: Ptr[SDL_Event]): Int = {
    SDLEventHelpersRaw.getWindowData2(event)
  }

  def getKeyCode(event: Ptr[SDL_Event]): Int = {
    SDLEventHelpersRaw.getKeyCode(event)
  }

  def getKeyMod(event: Ptr[SDL_Event]): UShort = {
    SDLEventHelpersRaw.getKeyMod(event)
  }

  def getKeyScancode(event: Ptr[SDL_Event]): Int = {
    SDLEventHelpersRaw.getKeyScancode(event)
  }

  def getTextInput(event: Ptr[SDL_Event]): String = {
    fromCString(SDLEventHelpersRaw.getTextInput(event))
  }
}

