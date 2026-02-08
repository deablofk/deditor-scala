package dev.cwby.terminal

import dev.cwby.bindings.VTerm
import dev.cwby.bindings.VTerm.{Lib => VLib}
import dev.cwby.bindings.VTerm.{Wrapper => W}

import scala.scalanative.posix.sys.{wait => posixWait}
import scala.scalanative.posix.unistd.read
import scala.scalanative.posix.unistd.write
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

final class TerminalSession(
    private var rows: Int,
    private var cols: Int,
    private val program: String,
    private val argv: Seq[String]
):

  private var masterFd: Int = -1
  private var childPid: Int = -1

  private var vt: VTerm.VTermPtr           = null
  private var screen: VTerm.VTermScreenPtr = null

  private var onExit: (() => Unit) | Null = null
  private var exitFired: Boolean          = false

  private val readBuf: Array[Byte] = new Array[Byte](64 * 1024)

  def setOnExit(callback: () => Unit): Unit =
    onExit = callback
    exitFired = false

  def start(): Unit =
    if vt != null then return

    Zone {
      val outMaster = stackalloc[CInt](1)
      val outPid    = stackalloc[CInt](1)

      val cProg = toCString(program)

      val argsWithNull = (argv :+ "").toArray
      val cArgv        = stackalloc[CString](argsWithNull.length)
      var i            = 0
      while i < argsWithNull.length - 1 do
        !(cArgv + i) = toCString(argsWithNull(i))
        i += 1
      !(cArgv + (argsWithNull.length - 1)) = null

      val r = W.term_openpty_spawn(cProg, cArgv, outMaster, outPid)
      if r != 0 then throw new RuntimeException("term_openpty_spawn failed")

      masterFd = (!outMaster).toInt
      childPid = (!outPid).toInt
    }

    vt = VLib.vterm_new(rows, cols)
    if vt == null then throw new RuntimeException("vterm_new failed")

    screen = VLib.vterm_obtain_screen(vt)
    VLib.vterm_screen_reset(screen, 1)

    resize(rows, cols, 0, 0)

  def resize(newRows: Int, newCols: Int, xpixel: Int, ypixel: Int): Unit =
    if newRows <= 0 || newCols <= 0 then return

    rows = newRows
    cols = newCols

    if vt != null then VLib.vterm_set_size(vt, rows, cols)

    if masterFd >= 0 then W.term_resize(masterFd, rows, cols, xpixel, ypixel)

  def poll(): Unit =
    if masterFd < 0 || vt == null then return

    val ptr       = readBuf.at(0)
    var keepGoing = true
    while keepGoing do
      val n = read(masterFd, ptr, readBuf.length.toUSize)
      if n > 0 then
        val cstr = ptr.asInstanceOf[CString]
        VLib.vterm_input_write(vt, cstr, n.toUSize)
        VLib.vterm_screen_flush_damage(screen)
      else keepGoing = false

    checkChildExited()

  private def checkChildExited(): Unit =
    if childPid <= 0 || exitFired then return

    Zone {
      val st = stackalloc[CInt](1)
      val r  = W.term_waitpid(childPid, st, posixWait.WNOHANG)
      if r == childPid then
        exitFired = true
        masterFd = -1
        val cb = onExit
        if cb != null then cb()
    }

  final case class Cell(
      text: String,
      fgRgb: Int,
      bgRgb: Int,
      attrs: Int,
      width: Int
  )

  def fillCell(
      row: Int,
      col: Int,
      dst: CString,
      dstLen: Int,
      fg: Ptr[CInt],
      bg: Ptr[CInt],
      attrs: Ptr[CInt],
      w: Ptr[CInt]
  ): Boolean =
    if screen == null || dst == null || dstLen <= 0 then return false
    W.term_vterm_screen_get_cell(screen, row, col, dst, dstLen.toUSize, fg, bg, attrs, w) == 0

  def getCell(row: Int, col: Int, dst: CString, dstLen: Int): Cell | Null =
    if screen == null || dst == null || dstLen <= 0 then return null

    Zone {
      val fg    = stackalloc[CInt](1)
      val bg    = stackalloc[CInt](1)
      val attrs = stackalloc[CInt](1)
      val w     = stackalloc[CInt](1)
      if !fillCell(row, col, dst, dstLen, fg, bg, attrs, w) then null
      else
        val s = fromCString(dst)
        Cell(s, (!fg).toInt, (!bg).toInt, (!attrs).toInt, (!w).toInt)
    }

  final case class Cursor(row: Int, col: Int, visible: Boolean)

  def getCursor(): Cursor | Null =
    if vt == null then return null

    Zone {
      val r  = stackalloc[CInt](1)
      val c  = stackalloc[CInt](1)
      val v  = stackalloc[CInt](1)
      val rc = W.term_vterm_get_cursor(vt, r, c, v)
      if rc != 0 then null
      else Cursor((!r).toInt, (!c).toInt, (!v).toInt != 0)
    }

  def writeUtf8(s: String): Unit =
    if masterFd < 0 || s == null || s.isEmpty then return

    val bytes = s.getBytes("UTF-8")
    writeBytes(bytes, bytes.length)

  def writeBytes(bytes: Array[Byte], len: Int): Unit =
    if masterFd < 0 || bytes == null || len <= 0 then return

    val n = Math.min(len, bytes.length)
    write(masterFd, bytes.at(0), n.toUSize)

  def getLineText(row: Int, dst: CString, dstLen: Int): Int =
    if screen == null || dst == null || dstLen <= 0 then return 0

    val written = W.term_vterm_screen_get_text(screen, dst, dstLen.toUSize, row, 0, row + 1, cols)
    written.toInt

  def getRows(): Int = rows

  def getCols(): Int = cols

  def close(): Unit =
    if masterFd >= 0 then
      W.term_kill(childPid, 1)
      masterFd = -1
    childPid = -1
    exitFired = true

    if vt != null then
      VLib.vterm_free(vt)
      vt = null
      screen = null
