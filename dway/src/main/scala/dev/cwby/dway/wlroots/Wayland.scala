package dev.cwby.dway.wlroots

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

type WlDisplay    = Ptr[Byte]
type WlEventLoop  = Ptr[Byte]
type WlSignal     = Ptr[Byte]
type WlListener   = Ptr[Byte]
type WlList       = Ptr[Byte]
type WlNotifyFunc = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]

@link("wayland-server")
@extern
object WaylandServer {
  def wl_display_create(): WlDisplay                             = extern
  def wl_display_destroy(display: WlDisplay): Unit               = extern
  def wl_display_run(display: WlDisplay): Unit                   = extern
  def wl_display_terminate(display: WlDisplay): Unit             = extern
  def wl_display_destroy_clients(display: WlDisplay): Unit       = extern
  def wl_display_get_event_loop(display: WlDisplay): WlEventLoop = extern
  def wl_display_add_socket_auto(display: WlDisplay): CString    = extern

  def wl_list_init(list: WlList): Unit                = extern
  def wl_list_insert(list: WlList, elm: WlList): Unit = extern
  def wl_list_remove(elm: WlList): Unit               = extern
  def wl_list_empty(list: WlList): CInt               = extern
}
