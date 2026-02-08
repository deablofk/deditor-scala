package dev.cwby.dway.wlroots

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

// Opaque pointer types for wlroots structs
type WlrBackend            = Ptr[Byte]
type WlrRenderer           = Ptr[Byte]
type WlrAllocator          = Ptr[Byte]
type WlrCompositor         = Ptr[Byte]
type WlrSubcompositor      = Ptr[Byte]
type WlrScene              = Ptr[Byte]
type WlrSceneTree          = Ptr[Byte]
type WlrSceneNode          = Ptr[Byte]
type WlrSceneOutput        = Ptr[Byte]
type WlrSceneOutputLayout  = Ptr[Byte]
type WlrSceneBuffer        = Ptr[Byte]
type WlrSceneSurface       = Ptr[Byte]
type WlrOutput             = Ptr[Byte]
type WlrOutputMode         = Ptr[Byte]
type WlrOutputState        = Ptr[Byte]
type WlrOutputLayout       = Ptr[Byte]
type WlrOutputLayoutOutput = Ptr[Byte]
type WlrXdgShell           = Ptr[Byte]
type WlrXdgToplevel        = Ptr[Byte]
type WlrXdgSurface         = Ptr[Byte]
type WlrSurface            = Ptr[Byte]
type WlrSeat               = Ptr[Byte]
type WlrSeatClient         = Ptr[Byte]
type WlrCursor             = Ptr[Byte]
type WlrXcursorManager     = Ptr[Byte]
type WlrKeyboard           = Ptr[Byte]
type WlrKeyboardModifiers  = Ptr[Byte]
type WlrInputDevice        = Ptr[Byte]
type WlrDataDeviceManager  = Ptr[Byte]

@link("wlroots-0.18")
@extern
object WlrLog {
  def wlr_log_init(verbosity: CInt, callback: Ptr[Byte]): Unit = extern
}

object WlrLogVerbosity {
  inline val WLR_SILENT = 0
  inline val WLR_ERROR  = 1
  inline val WLR_INFO   = 2
  inline val WLR_DEBUG  = 3
}

@link("wlroots-0.18")
@extern
object WlrBackendApi {
  def wlr_backend_autocreate(loop: WlEventLoop, session: Ptr[Byte]): WlrBackend = extern
  def wlr_backend_start(backend: WlrBackend): Boolean                           = extern
  def wlr_backend_destroy(backend: WlrBackend): Unit                            = extern
}

@link("wlroots-0.18")
@extern
object WlrRendererApi {
  def wlr_renderer_autocreate(backend: WlrBackend): WlrRenderer                        = extern
  def wlr_renderer_init_wl_display(renderer: WlrRenderer, display: WlDisplay): Boolean = extern
  def wlr_renderer_destroy(renderer: WlrRenderer): Unit                                = extern
}

@link("wlroots-0.18")
@extern
object WlrAllocatorApi {
  def wlr_allocator_autocreate(backend: WlrBackend, renderer: WlrRenderer): WlrAllocator = extern
  def wlr_allocator_destroy(allocator: WlrAllocator): Unit                               = extern
}

@link("wlroots-0.18")
@extern
object WlrCompositorApi {
  def wlr_compositor_create(display: WlDisplay, version: CInt, renderer: WlrRenderer): WlrCompositor = extern
  def wlr_subcompositor_create(display: WlDisplay): WlrSubcompositor                                 = extern
}

@link("wlroots-0.18")
@extern
object WlrDataDeviceApi {
  def wlr_data_device_manager_create(display: WlDisplay): WlrDataDeviceManager = extern
}

@link("wlroots-0.18")
@extern
object WlrOutputApi {
  def wlr_output_init_render(output: WlrOutput, allocator: WlrAllocator, renderer: WlrRenderer): Unit = extern
  def wlr_output_preferred_mode(output: WlrOutput): WlrOutputMode                                     = extern
  def wlr_output_commit_state(output: WlrOutput, state: WlrOutputState): Boolean                      = extern
  def wlr_output_state_init(state: WlrOutputState): Unit                                              = extern
  def wlr_output_state_finish(state: WlrOutputState): Unit                                            = extern
  def wlr_output_state_set_enabled(state: WlrOutputState, enabled: Boolean): Unit                     = extern
  def wlr_output_state_set_mode(state: WlrOutputState, mode: WlrOutputMode): Unit                     = extern
}

@link("wlroots-0.18")
@extern
object WlrOutputLayoutApi {
  def wlr_output_layout_create(display: WlDisplay): WlrOutputLayout                                 = extern
  def wlr_output_layout_add_auto(layout: WlrOutputLayout, output: WlrOutput): WlrOutputLayoutOutput = extern
}

@link("wlroots-0.18")
@extern
object WlrSceneApi {
  def wlr_scene_create(): WlrScene                                                                   = extern
  def wlr_scene_attach_output_layout(scene: WlrScene, layout: WlrOutputLayout): WlrSceneOutputLayout = extern
  def wlr_scene_output_create(scene: WlrScene, output: WlrOutput): WlrSceneOutput                    = extern
  def wlr_scene_output_layout_add_output(
      layout: WlrSceneOutputLayout,
      lo: WlrOutputLayoutOutput,
      so: WlrSceneOutput
  ): Unit                                                                            = extern
  def wlr_scene_output_commit(output: WlrSceneOutput, options: Ptr[Byte]): Boolean   = extern
  def wlr_scene_output_send_frame_done(output: WlrSceneOutput, now: Ptr[Byte]): Unit = extern
  def wlr_scene_get_scene_output(scene: WlrScene, output: WlrOutput): WlrSceneOutput = extern
  def wlr_scene_node_at(
      node: WlrSceneNode,
      lx: CDouble,
      ly: CDouble,
      sx: Ptr[CDouble],
      sy: Ptr[CDouble]
  ): WlrSceneNode                                                                              = extern
  def wlr_scene_node_raise_to_top(node: WlrSceneNode): Unit                                    = extern
  def wlr_scene_node_destroy(node: WlrSceneNode): Unit                                         = extern
  def wlr_scene_xdg_surface_create(parent: WlrSceneTree, surface: WlrXdgSurface): WlrSceneTree = extern
  def wlr_scene_buffer_from_node(node: WlrSceneNode): WlrSceneBuffer                           = extern
  def wlr_scene_surface_try_from_buffer(buffer: WlrSceneBuffer): WlrSceneSurface               = extern
}

@link("wlroots-0.18")
@extern
object WlrXdgShellApi {
  def wlr_xdg_shell_create(display: WlDisplay, version: CInt): WlrXdgShell                 = extern
  def wlr_xdg_toplevel_set_size(toplevel: WlrXdgToplevel, width: CInt, height: CInt): Unit = extern
  def wlr_xdg_toplevel_set_activated(toplevel: WlrXdgToplevel, activated: Boolean): Unit   = extern
  def wlr_xdg_toplevel_try_from_wlr_surface(surface: WlrSurface): WlrXdgToplevel           = extern
}

@link("wlroots-0.18")
@extern
object WlrSeatApi {
  def wlr_seat_create(display: WlDisplay, name: CString): WlrSeat       = extern
  def wlr_seat_set_capabilities(seat: WlrSeat, caps: UInt): Unit        = extern
  def wlr_seat_set_keyboard(seat: WlrSeat, keyboard: WlrKeyboard): Unit = extern
  def wlr_seat_get_keyboard(seat: WlrSeat): WlrKeyboard                 = extern
  def wlr_seat_keyboard_notify_enter(
      seat: WlrSeat,
      surface: WlrSurface,
      keycodes: Ptr[UInt],
      num_keycodes: CSize,
      modifiers: WlrKeyboardModifiers
  ): Unit                                                                                               = extern
  def wlr_seat_keyboard_notify_modifiers(seat: WlrSeat, modifiers: WlrKeyboardModifiers): Unit          = extern
  def wlr_seat_keyboard_notify_key(seat: WlrSeat, time_msec: UInt, keycode: UInt, state: UInt): Unit    = extern
  def wlr_seat_pointer_notify_enter(seat: WlrSeat, surface: WlrSurface, sx: CDouble, sy: CDouble): Unit = extern
  def wlr_seat_pointer_notify_motion(seat: WlrSeat, time_msec: UInt, sx: CDouble, sy: CDouble): Unit    = extern
  def wlr_seat_pointer_notify_button(seat: WlrSeat, time_msec: UInt, button: UInt, state: UInt): Unit   = extern
  def wlr_seat_pointer_notify_axis(
      seat: WlrSeat,
      time_msec: UInt,
      orientation: UInt,
      delta: CDouble,
      delta_discrete: CInt,
      source: UInt,
      relative_direction: UInt
  ): Unit                                                = extern
  def wlr_seat_pointer_notify_frame(seat: WlrSeat): Unit = extern
  def wlr_seat_pointer_clear_focus(seat: WlrSeat): Unit  = extern
}

@link("wlroots-0.18")
@extern
object WlrCursorApi {
  def wlr_cursor_create(): WlrCursor                                                                         = extern
  def wlr_cursor_destroy(cursor: WlrCursor): Unit                                                            = extern
  def wlr_cursor_attach_output_layout(cursor: WlrCursor, layout: WlrOutputLayout): Unit                      = extern
  def wlr_cursor_attach_input_device(cursor: WlrCursor, device: WlrInputDevice): Unit                        = extern
  def wlr_cursor_move(cursor: WlrCursor, device: WlrInputDevice, dx: CDouble, dy: CDouble): Unit             = extern
  def wlr_cursor_warp_absolute(cursor: WlrCursor, device: WlrInputDevice, x: CDouble, y: CDouble): Unit      = extern
  def wlr_cursor_set_xcursor(cursor: WlrCursor, mgr: WlrXcursorManager, name: CString): Unit                 = extern
  def wlr_cursor_set_surface(cursor: WlrCursor, surface: WlrSurface, hotspot_x: CInt, hotspot_y: CInt): Unit = extern
}

@link("wlroots-0.18")
@extern
object WlrXcursorManagerApi {
  def wlr_xcursor_manager_create(theme: CString, size: UInt): WlrXcursorManager = extern
  def wlr_xcursor_manager_destroy(mgr: WlrXcursorManager): Unit                 = extern
}

@link("wlroots-0.18")
@extern
object WlrKeyboardApi {
  def wlr_keyboard_from_input_device(device: WlrInputDevice): WlrKeyboard                = extern
  def wlr_keyboard_set_keymap(keyboard: WlrKeyboard, keymap: XkbKeymap): Unit            = extern
  def wlr_keyboard_set_repeat_info(keyboard: WlrKeyboard, rate: CInt, delay: CInt): Unit = extern
  def wlr_keyboard_get_modifiers(keyboard: WlrKeyboard): UInt                            = extern
}
