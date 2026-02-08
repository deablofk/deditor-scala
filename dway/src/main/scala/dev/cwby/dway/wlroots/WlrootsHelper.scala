package dev.cwby.dway.wlroots

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

// Bindings to the minimal C bridge (dway_wlroots_bridge.c)
// Only covers: wl_signal_add, struct field accessors, and sizeof helpers.

@link("dway_wlroots_bridge")
@extern
object WlrootsBridge {

  // -- wl_signal_add wrapper --
  def dway_signal_add(signal: WlSignal, listener: WlListener): Unit = extern

  // -- wl_listener helpers --
  def dway_listener_create(notify: WlNotifyFunc): WlListener                     = extern
  def dway_listener_destroy(listener: WlListener): Unit                          = extern
  def dway_listener_sizeof(): CSize                                              = extern
  def dway_listener_set_notify(listener: WlListener, notify: WlNotifyFunc): Unit = extern

  // -- wlr_output_state helpers --
  def dway_output_state_create(): WlrOutputState          = extern
  def dway_output_state_free(state: WlrOutputState): Unit = extern

  // -- Backend signal accessors --
  def dway_backend_events_new_output(backend: WlrBackend): WlSignal = extern
  def dway_backend_events_new_input(backend: WlrBackend): WlSignal  = extern

  // -- Output signal & field accessors --
  def dway_output_events_frame(output: WlrOutput): WlSignal         = extern
  def dway_output_events_request_state(output: WlrOutput): WlSignal = extern
  def dway_output_events_destroy(output: WlrOutput): WlSignal       = extern
  def dway_output_get_name(output: WlrOutput): CString              = extern

  // -- Output request_state event accessor --
  def dway_output_event_request_state_get_state(event: Ptr[Byte]): WlrOutputState = extern

  // -- XDG shell signal accessors --
  def dway_xdg_shell_events_new_toplevel(shell: WlrXdgShell): WlSignal = extern

  // -- XDG toplevel signal & field accessors --
  def dway_xdg_toplevel_events_destroy(toplevel: WlrXdgToplevel): WlSignal        = extern
  def dway_xdg_toplevel_events_request_move(toplevel: WlrXdgToplevel): WlSignal   = extern
  def dway_xdg_toplevel_events_request_resize(toplevel: WlrXdgToplevel): WlSignal = extern
  def dway_xdg_toplevel_get_base(toplevel: WlrXdgToplevel): WlrXdgSurface         = extern
  def dway_xdg_toplevel_get_title(toplevel: WlrXdgToplevel): CString              = extern

  // -- XDG surface field accessors --
  def dway_xdg_surface_get_surface(xdgSurface: WlrXdgSurface): WlrSurface  = extern
  def dway_xdg_surface_get_initial_commit(xdgSurface: WlrXdgSurface): CInt = extern

  // -- wlr_surface signal accessors --
  def dway_surface_events_map(surface: WlrSurface): WlSignal    = extern
  def dway_surface_events_unmap(surface: WlrSurface): WlSignal  = extern
  def dway_surface_events_commit(surface: WlrSurface): WlSignal = extern

  // -- Keyboard signal & field accessors --
  def dway_keyboard_events_modifiers(keyboard: WlrKeyboard): WlSignal              = extern
  def dway_keyboard_events_key(keyboard: WlrKeyboard): WlSignal                    = extern
  def dway_keyboard_get_xkb_state(keyboard: WlrKeyboard): XkbState                 = extern
  def dway_keyboard_get_modifiers_ptr(keyboard: WlrKeyboard): WlrKeyboardModifiers = extern
  def dway_keyboard_get_keycodes(keyboard: WlrKeyboard): Ptr[UInt]                 = extern
  def dway_keyboard_get_num_keycodes(keyboard: WlrKeyboard): CSize                 = extern

  // -- Keyboard key event accessors --
  def dway_key_event_get_time_msec(event: Ptr[Byte]): UInt = extern
  def dway_key_event_get_keycode(event: Ptr[Byte]): UInt   = extern
  def dway_key_event_get_state(event: Ptr[Byte]): UInt     = extern

  // -- Input device signal & field accessors --
  def dway_input_device_events_destroy(device: WlrInputDevice): WlSignal = extern
  def dway_input_device_get_type(device: WlrInputDevice): CInt           = extern

  // -- Cursor signal & field accessors --
  def dway_cursor_events_motion(cursor: WlrCursor): WlSignal          = extern
  def dway_cursor_events_motion_absolute(cursor: WlrCursor): WlSignal = extern
  def dway_cursor_events_button(cursor: WlrCursor): WlSignal          = extern
  def dway_cursor_events_axis(cursor: WlrCursor): WlSignal            = extern
  def dway_cursor_events_frame(cursor: WlrCursor): WlSignal           = extern
  def dway_cursor_get_x(cursor: WlrCursor): CDouble                   = extern
  def dway_cursor_get_y(cursor: WlrCursor): CDouble                   = extern

  // -- Pointer motion event accessors --
  def dway_pointer_motion_get_device(event: Ptr[Byte]): WlrInputDevice = extern
  def dway_pointer_motion_get_delta_x(event: Ptr[Byte]): CDouble       = extern
  def dway_pointer_motion_get_delta_y(event: Ptr[Byte]): CDouble       = extern
  def dway_pointer_motion_get_time_msec(event: Ptr[Byte]): UInt        = extern

  // -- Pointer motion absolute event accessors --
  def dway_pointer_motion_abs_get_device(event: Ptr[Byte]): WlrInputDevice = extern
  def dway_pointer_motion_abs_get_x(event: Ptr[Byte]): CDouble             = extern
  def dway_pointer_motion_abs_get_y(event: Ptr[Byte]): CDouble             = extern
  def dway_pointer_motion_abs_get_time_msec(event: Ptr[Byte]): UInt        = extern

  // -- Pointer button event accessors --
  def dway_pointer_button_get_time_msec(event: Ptr[Byte]): UInt = extern
  def dway_pointer_button_get_button(event: Ptr[Byte]): UInt    = extern
  def dway_pointer_button_get_state(event: Ptr[Byte]): UInt     = extern

  // -- Pointer axis event accessors --
  def dway_pointer_axis_get_time_msec(event: Ptr[Byte]): UInt          = extern
  def dway_pointer_axis_get_orientation(event: Ptr[Byte]): UInt        = extern
  def dway_pointer_axis_get_delta(event: Ptr[Byte]): CDouble           = extern
  def dway_pointer_axis_get_delta_discrete(event: Ptr[Byte]): CInt     = extern
  def dway_pointer_axis_get_source(event: Ptr[Byte]): UInt             = extern
  def dway_pointer_axis_get_relative_direction(event: Ptr[Byte]): UInt = extern

  // -- Seat signal & field accessors --
  def dway_seat_events_request_set_cursor(seat: WlrSeat): WlSignal       = extern
  def dway_seat_keyboard_get_focused_surface(seat: WlrSeat): WlrSurface  = extern
  def dway_seat_pointer_get_focused_client(seat: WlrSeat): WlrSeatClient = extern

  // -- Seat pointer request_set_cursor event accessors --
  def dway_set_cursor_event_get_seat_client(event: Ptr[Byte]): WlrSeatClient = extern
  def dway_set_cursor_event_get_surface(event: Ptr[Byte]): WlrSurface        = extern
  def dway_set_cursor_event_get_hotspot_x(event: Ptr[Byte]): CInt            = extern
  def dway_set_cursor_event_get_hotspot_y(event: Ptr[Byte]): CInt            = extern

  // -- Scene node helpers --
  def dway_scene_node_get_type(node: WlrSceneNode): CInt                        = extern
  def dway_scene_node_get_parent(node: WlrSceneNode): WlrSceneTree              = extern
  def dway_scene_node_get_data(node: WlrSceneNode): Ptr[Byte]                   = extern
  def dway_scene_node_set_data(node: WlrSceneNode, data: Ptr[Byte]): Unit       = extern
  def dway_scene_tree_get_node(tree: WlrSceneTree): WlrSceneNode                = extern
  def dway_scene_get_tree_node(scene: WlrScene): WlrSceneNode                   = extern
  def dway_scene_get_tree(scene: WlrScene): WlrSceneTree                        = extern
  def dway_scene_surface_get_surface(sceneSurface: WlrSceneSurface): WlrSurface = extern

  // -- Timespec helper --
  def dway_clock_gettime_monotonic(ts: Ptr[Byte]): Unit = extern
  def dway_timespec_sizeof(): CSize                     = extern

  // -- Constants --
  def dway_WLR_SCENE_NODE_BUFFER(): CInt            = extern
  def dway_WLR_SCENE_NODE_TREE(): CInt              = extern
  def dway_WLR_INPUT_DEVICE_KEYBOARD(): CInt        = extern
  def dway_WLR_INPUT_DEVICE_POINTER(): CInt         = extern
  def dway_WL_KEYBOARD_KEY_STATE_PRESSED(): CInt    = extern
  def dway_WL_POINTER_BUTTON_STATE_RELEASED(): CInt = extern
  def dway_WLR_MODIFIER_ALT(): CInt                 = extern
  def dway_WL_SEAT_CAPABILITY_POINTER(): UInt       = extern
  def dway_WL_SEAT_CAPABILITY_KEYBOARD(): UInt      = extern
  def dway_XKB_KEY_Escape(): UInt                   = extern
}
