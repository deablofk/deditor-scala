package dev.cwby.bindings

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

/** SDL3 Bindings for Scala Native Provides bindings for SDL initialization, window management, OpenGL context, and
  * event handling
  */
object SDL3 {

  final val SDL_INIT_VIDEO: UInt                   = 0x00000020.toUInt
  final val SDL_WINDOW_OPENGL: UInt                = 0x00000002.toUInt
  final val SDL_WINDOW_BORDERLESS: UInt            = 0x00000010.toUInt
  final val SDL_WINDOW_RESIZABLE: UInt             = 0x00000020.toUInt
  final val SDL_EVENT_QUIT: UInt                   = 0x100.toUInt
  final val SDL_EVENT_WINDOW_CLOSE_REQUESTED: UInt = 0x203.toUInt
  final val SDL_EVENT_WINDOW_RESIZED: UInt         = 0x205.toUInt
  final val SDL_EVENT_KEY_DOWN: UInt               = 0x300.toUInt
  final val SDL_EVENT_KEY_UP: UInt                 = 0x301.toUInt
  final val SDL_EVENT_TEXT_EDITING: UInt           = 0x302.toUInt
  final val SDL_EVENT_TEXT_INPUT: UInt             = 0x303.toUInt
  final val SDL_EVENT_CLIPBOARD_UPDATE: UInt       = 0x900.toUInt
  final val SDLK_ESCAPE: CInt                      = 27
  final val SDLK_RETURN: CInt                      = 13
  final val SDLK_BACKSPACE: CInt                   = 8
  final val SDLK_TAB: CInt                         = 9
  final val SDLK_SPACE: CInt                       = 32
  final val SDLK_DELETE: CInt                      = 127
  final val SDLK_UP: CInt                          = 1073741906
  final val SDLK_DOWN: CInt                        = 1073741905
  final val SDLK_LEFT: CInt                        = 1073741904
  final val SDLK_RIGHT: CInt                       = 1073741903
  final val SDLK_HOME: CInt                        = 1073741898
  final val SDLK_END: CInt                         = 1073741901
  final val SDLK_PAGEUP: CInt                      = 1073741899
  final val SDLK_PAGEDOWN: CInt                    = 1073741902
  final val SDL_KMOD_NONE: UShort                  = 0x0000.toUShort
  final val SDL_KMOD_LSHIFT: UShort                = 0x0001.toUShort
  final val SDL_KMOD_RSHIFT: UShort                = 0x0002.toUShort
  final val SDL_KMOD_SHIFT: UShort                 = (SDL_KMOD_LSHIFT | SDL_KMOD_RSHIFT).toUShort
  final val SDL_KMOD_LCTRL: UShort                 = 0x0040.toUShort
  final val SDL_KMOD_RCTRL: UShort                 = 0x0080.toUShort
  final val SDL_KMOD_CTRL: UShort                  = (SDL_KMOD_LCTRL | SDL_KMOD_RCTRL).toUShort
  final val SDL_KMOD_LALT: UShort                  = 0x0100.toUShort
  final val SDL_KMOD_RALT: UShort                  = 0x0200.toUShort
  final val SDL_KMOD_ALT: UShort                   = (SDL_KMOD_LALT | SDL_KMOD_RALT).toUShort

  type SDL_Window    = Ptr[Byte]
  type SDL_GLContext = Ptr[Byte]

  /** SDL Event structure Note: This is a simplified version. The actual SDL_Event is a union. SDL3 events are typically
    * 128 bytes to accommodate all event types.
    */
  type SDL_Event = CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]] // 128 bytes for SDL event structure

  @link("SDL3")
  @extern
  object SDLInit {

    /** Initialize SDL subsystems
      *
      * @param flags
      *   Subsystem initialization flags (e.g., SDL_INIT_VIDEO)
      * @return
      *   true on success, false on failure (SDL3 uses bool, not int)
      */
    @name("SDL_Init")
    def SDL_Init(flags: UInt): Boolean = extern

    /** Shut down SDL */
    @name("SDL_Quit")
    def SDL_Quit(): Unit = extern
  }

  @link("SDL3")
  @extern
  object SDLError {

    /** Get the last error message set by SDL
      *
      * @return
      *   Pointer to error string or null if no error
      */
    @name("SDL_GetError")
    def SDL_GetError(): CString = extern

    /** Clear any previous error message
      */
    @name("SDL_ClearError")
    def SDL_ClearError(): Unit = extern
  }

  @link("SDL3")
  @extern
  object SDLVideo {

    /** Create a window
      *
      * @param title
      *   Window title
      * @param width
      *   Window width
      * @param height
      *   Window height
      * @param flags
      *   Window flags (e.g., SDL_WINDOW_OPENGL | SDL_WINDOW_RESIZABLE)
      * @return
      *   Pointer to SDL_Window or null on failure
      */
    @name("SDL_CreateWindow")
    def SDL_CreateWindow(title: CString, width: CInt, height: CInt, flags: UInt): SDL_Window = extern

    /** Show a window
      *
      * @param window
      *   Window to show
      * @return
      *   true on success, false on failure
      */
    @name("SDL_ShowWindow")
    def SDL_ShowWindow(window: SDL_Window): Boolean = extern

    /** Hide a window
      *
      * @param window
      *   Window to hide
      * @return
      *   true on success, false on failure
      */
    @name("SDL_HideWindow")
    def SDL_HideWindow(window: SDL_Window): Boolean = extern

    /** Destroy a window
      *
      * @param window
      *   Window to destroy
      */
    @name("SDL_DestroyWindow")
    def SDL_DestroyWindow(window: SDL_Window): Unit = extern

    /** Get the size of a window's client area
      *
      * @param window
      *   The window to query
      * @param w
      *   Pointer to store width
      * @param h
      *   Pointer to store height
      * @return
      *   true on success, false on failure
      */
    @name("SDL_GetWindowSize")
    def SDL_GetWindowSize(window: SDL_Window, w: Ptr[CInt], h: Ptr[CInt]): Boolean = extern

    /** Get the size of a window's underlying drawable in pixels (for use with glViewport)
      *
      * @param window
      *   The window to query
      * @param w
      *   Pointer to store width
      * @param h
      *   Pointer to store height
      * @return
      *   true on success, false on failure
      */
    @name("SDL_GetWindowSizeInPixels")
    def SDL_GetWindowSizeInPixels(window: SDL_Window, w: Ptr[CInt], h: Ptr[CInt]): Boolean = extern
  }

  @link("SDL3")
  @extern
  object SDLGL {

    /** Create an OpenGL context for use with an OpenGL window
      *
      * @param window
      *   The window to associate with the context
      * @return
      *   The OpenGL context or null on failure
      */
    @name("SDL_GL_CreateContext")
    def SDL_GL_CreateContext(window: SDL_Window): SDL_GLContext = extern

    /** Set up an OpenGL context for rendering into an OpenGL window
      *
      * @param window
      *   The window to associate with the context
      * @param context
      *   The OpenGL context to make current
      * @return
      *   true on success, false on failure
      */
    @name("SDL_GL_MakeCurrent")
    def SDL_GL_MakeCurrent(window: SDL_Window, context: SDL_GLContext): Boolean = extern

    /** Get the currently active OpenGL context
      *
      * @return
      *   The currently active OpenGL context or null
      */
    @name("SDL_GL_GetCurrentContext")
    def SDL_GL_GetCurrentContext(): SDL_GLContext = extern

    /** Set the swap interval for the current OpenGL context
      *
      * @param interval
      *   0 for immediate updates, 1 for vsync, -1 for adaptive vsync
      * @return
      *   true on success, false on failure
      */
    @name("SDL_GL_SetSwapInterval")
    def SDL_GL_SetSwapInterval(interval: CInt): Boolean = extern

    /** Swap the OpenGL buffers for a window (present rendered content)
      *
      * @param window
      *   The window to swap
      * @return
      *   true on success, false on failure
      */
    @name("SDL_GL_SwapWindow")
    def SDL_GL_SwapWindow(window: SDL_Window): Boolean = extern

    /** Delete an OpenGL context
      *
      * @param context
      *   The OpenGL context to delete
      */
    @name("SDL_GL_DestroyContext")
    def SDL_GL_DestroyContext(context: SDL_GLContext): Unit = extern
  }

  @link("SDL3")
  @extern
  object SDLEvents {

    /** Poll for currently pending events
      *
      * @param event
      *   The SDL_Event structure to be filled with event data
      * @return
      *   true if there are pending events, false if none available
      */
    @name("SDL_PollEvent")
    def SDL_PollEvent(event: Ptr[SDL_Event]): Boolean = extern

    /** Wait indefinitely for the next available event
      *
      * @param event
      *   The SDL_Event structure to be filled with event data
      * @return
      *   true on success, false on error
      */
    @name("SDL_WaitEvent")
    def SDL_WaitEvent(event: Ptr[SDL_Event]): Boolean = extern
  }

  @link("SDL3")
  @extern
  object SDLClipboard {

    /** Get text from the clipboard
      *
      * @return
      *   The clipboard text or null if empty
      */
    @name("SDL_GetClipboardText")
    def SDL_GetClipboardText(): CString = extern

    /** Set text in the clipboard
      *
      * @param text
      *   The text to store in the clipboard
      * @return
      *   true on success, false on failure
      */
    @name("SDL_SetClipboardText")
    def SDL_SetClipboardText(text: CString): Boolean = extern

    /** Check whether the clipboard has text
      *
      * @return
      *   true if clipboard has text, false otherwise
      */
    @name("SDL_HasClipboardText")
    def SDL_HasClipboardText(): Boolean = extern
  }

  @link("SDL3")
  @extern
  object SDLKeyboard {

    /** Start accepting Unicode text input events
      *
      * @param window
      *   The window to enable text input for
      * @return
      *   true on success, false on failure
      */
    @name("SDL_StartTextInput")
    def SDL_StartTextInput(window: SDL_Window): Boolean = extern

    /** Stop receiving any text input events
      *
      * @param window
      *   The window to disable text input for
      * @return
      *   true on success, false on failure
      */
    @name("SDL_StopTextInput")
    def SDL_StopTextInput(window: SDL_Window): Boolean = extern

    /** Get a key code from a scancode
      *
      * @param scancode
      *   The scancode to translate
      * @param modstate
      *   The modifier state to use
      * @param key_event
      *   True if this is from a key press event
      * @return
      *   The key code
      */
    @name("SDL_GetKeyFromScancode")
    def SDL_GetKeyFromScancode(scancode: CInt, modstate: UShort, key_event: Boolean): CInt = extern
  }

  /** Helper functions for working with SDL events */
  object SDLEventHelpers {

    /** Get the event type from an event The event type is stored at the beginning of the SDL_Event structure
      */
    def getEventType(event: Ptr[SDL_Event]): UInt = {
      val eventPtr = event.asInstanceOf[Ptr[UInt]]
      !eventPtr
    }

    /** Get timestamp from event The timestamp follows the event type and reserved field in the structure
      */
    def getTimestamp(event: Ptr[SDL_Event]): ULong = {
      val timestampPtr = (event.asInstanceOf[Ptr[Byte]] + 8).asInstanceOf[Ptr[ULong]]
      !timestampPtr
    }

    /** Access window event data (for window resize events) Note: This assumes the event is a window event. Check event
      * type first. SDL3 window event structure: type(4), reserved(4), timestamp(8), windowID(4), data1(4), data2(4)
      */
    def getWindowData1(event: Ptr[SDL_Event]): CInt = {
      val dataPtr = (event.asInstanceOf[Ptr[Byte]] + 20).asInstanceOf[Ptr[CInt]]
      !dataPtr
    }

    def getWindowData2(event: Ptr[SDL_Event]): CInt = {
      val dataPtr = (event.asInstanceOf[Ptr[Byte]] + 24).asInstanceOf[Ptr[CInt]]
      !dataPtr
    }

    /** Get key from keyboard event SDL3 keyboard event structure: type(4), reserved(4), timestamp(8), windowID(4),
      * which(4), scancode(4), key(4), mod(2), raw(2), down(1), repeat(1)
      */
    def getKeyCode(event: Ptr[SDL_Event]): CInt = {
      val keyPtr = (event.asInstanceOf[Ptr[Byte]] + 28).asInstanceOf[Ptr[CInt]]
      !keyPtr
    }

    def getKeyMod(event: Ptr[SDL_Event]): UShort = {
      val modPtr = (event.asInstanceOf[Ptr[Byte]] + 32).asInstanceOf[Ptr[UShort]]
      !modPtr
    }

    def getKeyScancode(event: Ptr[SDL_Event]): CInt = {
      val scancodePtr = (event.asInstanceOf[Ptr[Byte]] + 24).asInstanceOf[Ptr[CInt]]
      !scancodePtr
    }

    /** Get text from text input event SDL_TextInputEvent structure: Uint32 type; // 4 bytes, offset 0 Uint32 reserved;
      * // 4 bytes, offset 4 Uint64 timestamp; // 8 bytes, offset 8 SDL_WindowID windowID; // 4 bytes (Uint32), offset
      * 16 [padding 4 bytes for alignment] // offset 20 const char *text; // 8 bytes pointer, offset 24
      */
    def getTextInput(event: Ptr[SDL_Event]): CString = {

      val eventBytes      = event.asInstanceOf[Ptr[Byte]]
      val textPtrLocation = (eventBytes + 24).asInstanceOf[Ptr[Ptr[Byte]]]
      val textPtr         = !textPtrLocation

      if (textPtr == null) {
        null.asInstanceOf[CString]
      } else {
        textPtr.asInstanceOf[CString]
      }
    }
  }

}
