package dev.cwby.dway.wlroots

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

type XkbContext = Ptr[Byte]
type XkbKeymap  = Ptr[Byte]
type XkbState   = Ptr[Byte]
type XkbKeysym  = UInt

@link("xkbcommon")
@extern
object Xkb {
  def xkb_context_new(flags: CInt): XkbContext     = extern
  def xkb_context_unref(context: XkbContext): Unit = extern
  def xkb_keymap_new_from_names(
      context: XkbContext,
      names: Ptr[Byte],
      flags: CInt
  ): XkbKeymap                                  = extern
  def xkb_keymap_unref(keymap: XkbKeymap): Unit = extern
  def xkb_state_key_get_syms(
      state: XkbState,
      key: UInt,
      syms: Ptr[Ptr[UInt]]
  ): CInt = extern
}

object XkbConstants {
  inline val XKB_CONTEXT_NO_FLAGS        = 0
  inline val XKB_KEYMAP_COMPILE_NO_FLAGS = 0
}
