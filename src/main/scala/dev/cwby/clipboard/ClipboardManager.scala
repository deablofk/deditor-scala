package dev.cwby.clipboard

import dev.cwby.bindings.SDL3.SDLClipboard.SDL_GetClipboardText
import dev.cwby.bindings.SDL3.SDLClipboard.SDL_SetClipboardText

import scala.scalanative.unsafe.Zone
import scala.scalanative.unsafe.fromCString
import scala.scalanative.unsafe.toCString

enum ClipboardType {
  case INTERNAL
  case SYSTEM
}

final case class ClipboardState(var text: String)

private val systemClipboard   = ClipboardState("")
private val internalClipboard = ClipboardState("")

// Internal clipboard functions
@inline private def setInternalClipboard(state: ClipboardState, text: String): Unit = {
  state.text = text
}

@inline private def getInternalClipboard(state: ClipboardState): String = {
  state.text
}

// System clipboard functions
@inline private def setSystemClipboard(state: ClipboardState, text: String): Unit = {
  if (state.text != text) {
    Zone {
      SDL_SetClipboardText(toCString(text))
    }
    state.text = text
  }
}

@inline private def getSystemClipboard(state: ClipboardState): String = {
  if (state.text.nonEmpty) return state.text

  val cstr = SDL_GetClipboardText()
  val text = if cstr != null then fromCString(cstr) else ""

  state.text = text
  text
}

@inline def setClipboardContent(kind: ClipboardType, text: String): Unit = {
  kind match {
    case ClipboardType.INTERNAL => setInternalClipboard(internalClipboard, text)
    case ClipboardType.SYSTEM   => setSystemClipboard(systemClipboard, text)
  }
}

@inline def getClipboardContent(kind: ClipboardType): String = {
  kind match {
    case ClipboardType.INTERNAL => getInternalClipboard(internalClipboard)
    case ClipboardType.SYSTEM   => getSystemClipboard(systemClipboard)
  }
}
