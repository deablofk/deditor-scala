package dev.cwby.guitk.bindings.sdl.internal

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

private[sdl] object SDLEventOffsets {
  inline val TYPE      = 0
  inline val TIMESTAMP = 8
  inline val WINDOW_D1 = 20
  inline val WINDOW_D2 = 24
  inline val KEYCODE   = 28
  inline val KEYMOD    = 32
  inline val SCANCODE  = 24
  inline val TEXTPTR   = 24
}

@link("SDL3")
@extern
private[sdl] object SDLInitRaw {
  def SDL_Init(flags: UInt): Boolean = extern

  def SDL_Quit(): Unit = extern
}

@link("SDL3")
@extern
private[sdl] object SDLVideoRaw {
  def SDL_CreateWindow(title: CString, width: CInt, height: CInt, flags: UInt): Ptr[Byte] = extern

  def SDL_ShowWindow(window: Ptr[Byte]): Boolean = extern

  def SDL_HideWindow(window: Ptr[Byte]): Boolean = extern

  def SDL_DestroyWindow(window: Ptr[Byte]): Unit = extern

  def SDL_GetWindowSize(window: Ptr[Byte], w: Ptr[CInt], h: Ptr[CInt]): Boolean = extern

  def SDL_GetWindowSizeInPixels(window: Ptr[Byte], w: Ptr[CInt], h: Ptr[CInt]): Boolean = extern
}

@link("SDL3")
@extern
private[sdl] object SDLGLRaw {
  def SDL_GL_CreateContext(window: Ptr[Byte]): Ptr[Byte] = extern

  def SDL_GL_MakeCurrent(window: Ptr[Byte], context: Ptr[Byte]): Boolean = extern

  def SDL_GL_GetCurrentContext(): Ptr[Byte] = extern

  def SDL_GL_SetSwapInterval(interval: CInt): Boolean = extern

  def SDL_GL_SwapWindow(window: Ptr[Byte]): Boolean = extern

  def SDL_GL_DestroyContext(context: Ptr[Byte]): Unit = extern
}

@link("SDL3")
@extern
private[sdl] object SDLEventsRaw {
  def SDL_PollEvent(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): Boolean = extern

  def SDL_WaitEvent(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): Boolean = extern
}

@link("SDL3")
@extern
private[sdl] object SDLClipboardRaw {
  def SDL_GetClipboardText(): CString = extern

  def SDL_SetClipboardText(text: CString): Boolean = extern

  def SDL_HasClipboardText(): Boolean = extern
}

@link("SDL3")
@extern
private[sdl] object SDLStdlib {
  def SDL_free(ptr: Ptr[Byte]): Unit = extern
}

@link("SDL3")
@extern
private[sdl] object SDLKeyboardRaw {
  def SDL_StartTextInput(window: Ptr[Byte]): Boolean = extern

  def SDL_StopTextInput(window: Ptr[Byte]): Boolean = extern

  def SDL_GetKeyFromScancode(scancode: CInt, modstate: UShort, key_event: Boolean): CInt = extern
}

private[sdl] object SDLEventHelpersRaw {

  private def readAt[T: Tag](base: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]], offset: Int): T = {
    !(base.asInstanceOf[Ptr[Byte]] + offset).asInstanceOf[Ptr[T]]
  }

  def getEventType(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): UInt = {
    readAt[UInt](event, SDLEventOffsets.TYPE)
  }

  def getTimestamp(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): ULong = {
    readAt[ULong](event, SDLEventOffsets.TIMESTAMP)
  }

  def getWindowData1(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): CInt = {
    readAt[CInt](event, SDLEventOffsets.WINDOW_D1)
  }

  def getWindowData2(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): CInt = {
    readAt[CInt](event, SDLEventOffsets.WINDOW_D2)
  }

  def getKeyCode(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): CInt = {
    readAt[CInt](event, SDLEventOffsets.KEYCODE)
  }

  def getKeyMod(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): UShort = {
    readAt[UShort](event, SDLEventOffsets.KEYMOD)
  }

  def getKeyScancode(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): CInt = {
    readAt[CInt](event, SDLEventOffsets.SCANCODE)
  }

  def getTextInput(event: Ptr[CArray[Byte, Nat.Digit3[Nat._1, Nat._2, Nat._8]]]): CString = {
    val ptr = readAt[Ptr[Byte]](event, SDLEventOffsets.TEXTPTR)

    if ptr == null then null.asInstanceOf[CString]
    else ptr.asInstanceOf[CString]
  }
}
