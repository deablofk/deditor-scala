package dev.cwby.bindings

import scala.scalanative.unsafe._
object VTerm:

  type VTermPtr       = Ptr[Byte]
  type VTermScreenPtr = Ptr[Byte]
  type VTermRect      = CStruct4[CInt, CInt, CInt, CInt]

  @link("vterm")
  @extern
  object Lib:
    def vterm_new(rows: CInt, cols: CInt): VTermPtr = extern
    def vterm_free(vt: VTermPtr): Unit              = extern

    def vterm_set_size(vt: VTermPtr, rows: CInt, cols: CInt): Unit         = extern
    def vterm_input_write(vt: VTermPtr, bytes: CString, len: CSize): CSize = extern

    def vterm_obtain_screen(vt: VTermPtr): VTermScreenPtr = extern

    def vterm_screen_reset(screen: VTermScreenPtr, hard: CInt): Unit = extern
    def vterm_screen_flush_damage(screen: VTermScreenPtr): Unit      = extern
  @link("terminal_wrapper")
  @extern
  object Wrapper:
    def term_openpty_spawn(
        prog: CString,
        argv: Ptr[CString],
        out_master_fd: Ptr[CInt],
        out_child_pid: Ptr[CInt]
    ): CInt                                                                                    = extern
    def term_resize(master_fd: CInt, rows: CInt, cols: CInt, xpixel: CInt, ypixel: CInt): CInt = extern
    def term_kill(pid: CInt, sig: CInt): CInt                                                  = extern
    def term_waitpid(pid: CInt, out_status: Ptr[CInt], options: CInt): CInt                    = extern

    def term_vterm_screen_get_text(
        screen: VTermScreenPtr,
        str: CString,
        len: CSize,
        start_row: CInt,
        start_col: CInt,
        end_row: CInt,
        end_col: CInt
    ): CSize = extern

    def term_vterm_screen_get_cell(
        screen: VTermScreenPtr,
        row: CInt,
        col: CInt,
        out_utf8: CString,
        out_utf8_len: CSize,
        out_fg_rgb: Ptr[CInt],
        out_bg_rgb: Ptr[CInt],
        out_attrs: Ptr[CInt],
        out_width: Ptr[CInt]
    ): CInt = extern

    def term_vterm_get_cursor(
        vt: VTermPtr,
        out_row: Ptr[CInt],
        out_col: Ptr[CInt],
        out_visible: Ptr[CInt]
    ): CInt = extern
