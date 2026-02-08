package dev.cwby.dway.compositor

import dev.cwby.dway.wlroots.*

import scala.collection.mutable
import scala.scalanative.libc.stdlib
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

// Per-output state tracked in Scala
final class OutputState(
    val wlrOutput: WlrOutput,
    var sceneOutput: WlrSceneOutput,
    val frameListener: WlListener,
    val requestStateListener: WlListener,
    val destroyListener: WlListener
)

// Per-toplevel state tracked in Scala
final class ToplevelState(
    val xdgToplevel: WlrXdgToplevel,
    var sceneTree: WlrSceneTree,
    val mapListener: WlListener,
    val unmapListener: WlListener,
    val commitListener: WlListener,
    val destroyListener: WlListener,
    val requestMoveListener: WlListener,
    val requestResizeListener: WlListener
)

// Per-keyboard state tracked in Scala
final class KeyboardState(
    val wlrKeyboard: WlrKeyboard,
    val modifiersListener: WlListener,
    val keyListener: WlListener,
    val destroyListener: WlListener
)

object Compositor {
  import WaylandServer.*
  import WlrootsBridge.*

  // Server state
  private var display: WlDisplay                = null
  private var backend: WlrBackend               = null
  private var renderer: WlrRenderer             = null
  private var allocator: WlrAllocator           = null
  private var scene: WlrScene                   = null
  private var sceneLayout: WlrSceneOutputLayout = null
  private var outputLayout: WlrOutputLayout     = null
  private var xdgShell: WlrXdgShell             = null
  private var seat: WlrSeat                     = null
  private var cursor: WlrCursor                 = null
  private var cursorMgr: WlrXcursorManager      = null
  private var socket: CString                   = null

  // Listeners owned by the compositor itself
  private var newOutputListener: WlListener        = null
  private var newInputListener: WlListener         = null
  private var newXdgToplevelListener: WlListener   = null
  private var cursorMotionListener: WlListener     = null
  private var cursorMotionAbsListener: WlListener  = null
  private var cursorButtonListener: WlListener     = null
  private var cursorAxisListener: WlListener       = null
  private var cursorFrameListener: WlListener      = null
  private var requestSetCursorListener: WlListener = null

  // Collections for sub-objects
  private val outputs: mutable.Map[WlListener, OutputState]          = mutable.Map.empty
  private val toplevels: mutable.Map[WlListener, ToplevelState]      = mutable.Map.empty
  private val keyboards: mutable.Map[WlListener, KeyboardState]      = mutable.Map.empty
  private val sceneTreeMap: mutable.Map[WlrSceneTree, ToplevelState] = mutable.Map.empty

  // Lifecycle
  def create(): Boolean = {
    WlrLog.wlr_log_init(WlrLogVerbosity.WLR_DEBUG, null)

    display = wl_display_create()
    if (display == null) return false

    backend = WlrBackendApi.wlr_backend_autocreate(wl_display_get_event_loop(display), null)
    if (backend == null) { cleanup(); return false }

    renderer = WlrRendererApi.wlr_renderer_autocreate(backend)
    if (renderer == null) { cleanup(); return false }
    WlrRendererApi.wlr_renderer_init_wl_display(renderer, display)

    allocator = WlrAllocatorApi.wlr_allocator_autocreate(backend, renderer)
    if (allocator == null) { cleanup(); return false }

    WlrCompositorApi.wlr_compositor_create(display, 5, renderer)
    WlrCompositorApi.wlr_subcompositor_create(display)
    WlrDataDeviceApi.wlr_data_device_manager_create(display)

    // Output layout
    outputLayout = WlrOutputLayoutApi.wlr_output_layout_create(display)

    // Scene graph
    scene = WlrSceneApi.wlr_scene_create()
    sceneLayout = WlrSceneApi.wlr_scene_attach_output_layout(scene, outputLayout)

    // XDG shell
    xdgShell = WlrXdgShellApi.wlr_xdg_shell_create(display, 3)
    newXdgToplevelListener = dway_listener_create(onNewXdgToplevel)
    dway_signal_add(dway_xdg_shell_events_new_toplevel(xdgShell), newXdgToplevelListener)

    // New output
    newOutputListener = dway_listener_create(onNewOutput)
    dway_signal_add(dway_backend_events_new_output(backend), newOutputListener)

    // Cursor
    cursor = WlrCursorApi.wlr_cursor_create()
    WlrCursorApi.wlr_cursor_attach_output_layout(cursor, outputLayout)
    cursorMgr = WlrXcursorManagerApi.wlr_xcursor_manager_create(null, 24.toUInt)

    cursorMotionListener = dway_listener_create(onCursorMotion)
    dway_signal_add(dway_cursor_events_motion(cursor), cursorMotionListener)

    cursorMotionAbsListener = dway_listener_create(onCursorMotionAbsolute)
    dway_signal_add(dway_cursor_events_motion_absolute(cursor), cursorMotionAbsListener)

    cursorButtonListener = dway_listener_create(onCursorButton)
    dway_signal_add(dway_cursor_events_button(cursor), cursorButtonListener)

    cursorAxisListener = dway_listener_create(onCursorAxis)
    dway_signal_add(dway_cursor_events_axis(cursor), cursorAxisListener)

    cursorFrameListener = dway_listener_create(onCursorFrame)
    dway_signal_add(dway_cursor_events_frame(cursor), cursorFrameListener)

    // Seat
    seat = WlrSeatApi.wlr_seat_create(display, c"seat0")

    requestSetCursorListener = dway_listener_create(onRequestSetCursor)
    dway_signal_add(dway_seat_events_request_set_cursor(seat), requestSetCursorListener)

    // Input
    newInputListener = dway_listener_create(onNewInput)
    dway_signal_add(dway_backend_events_new_input(backend), newInputListener)

    System.err.println("[dway] Server created successfully")
    true
  }

  def start(): Boolean = {
    socket = wl_display_add_socket_auto(display)
    if (socket == null) {
      System.err.println("[dway] Failed to add socket")
      return false
    }
    if (!WlrBackendApi.wlr_backend_start(backend)) {
      System.err.println("[dway] Failed to start backend")
      return false
    }
    System.err.println(s"[dway] Running on Wayland display: ${fromCString(socket)}")
    true
  }

  def getSocket: String =
    if (socket != null) fromCString(socket) else ""

  def run(): Unit =
    if (display != null) wl_display_run(display)

  def terminate(): Unit =
    if (display != null) wl_display_terminate(display)

  def destroy(): Unit = {
    if (display != null) {
      wl_display_destroy_clients(display)
      WlrSceneApi.wlr_scene_node_destroy(dway_scene_get_tree_node(scene))
      WlrXcursorManagerApi.wlr_xcursor_manager_destroy(cursorMgr)
      WlrCursorApi.wlr_cursor_destroy(cursor)
      WlrAllocatorApi.wlr_allocator_destroy(allocator)
      WlrRendererApi.wlr_renderer_destroy(renderer)
      WlrBackendApi.wlr_backend_destroy(backend)
      wl_display_destroy(display)
      display = null
      System.err.println("[dway] Server destroyed")
    }
  }

  private def cleanup(): Unit = {
    if (display != null) { wl_display_destroy(display); display = null }
  }

  // ════════════════════════════════════════════
  // Output callbacks
  // ════════════════════════════════════════════

  private val onNewOutput: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], data: Ptr[Byte]) =>
      val wlrOutput: WlrOutput = data

      WlrOutputApi.wlr_output_init_render(wlrOutput, allocator, renderer)

      val state = dway_output_state_create()
      WlrOutputApi.wlr_output_state_set_enabled(state, true)
      val mode = WlrOutputApi.wlr_output_preferred_mode(wlrOutput)
      if (mode != null) WlrOutputApi.wlr_output_state_set_mode(state, mode)
      WlrOutputApi.wlr_output_commit_state(wlrOutput, state)
      dway_output_state_free(state)

      val frameL = dway_listener_create(onOutputFrame)
      dway_signal_add(dway_output_events_frame(wlrOutput), frameL)

      val reqStateL = dway_listener_create(onOutputRequestState)
      dway_signal_add(dway_output_events_request_state(wlrOutput), reqStateL)

      val destroyL = dway_listener_create(onOutputDestroy)
      dway_signal_add(dway_output_events_destroy(wlrOutput), destroyL)

      val layoutOutput = WlrOutputLayoutApi.wlr_output_layout_add_auto(outputLayout, wlrOutput)
      val sceneOut     = WlrSceneApi.wlr_scene_output_create(scene, wlrOutput)
      WlrSceneApi.wlr_scene_output_layout_add_output(sceneLayout, layoutOutput, sceneOut)

      val os = new OutputState(wlrOutput, sceneOut, frameL, reqStateL, destroyL)
      outputs(frameL) = os
      outputs(reqStateL) = os
      outputs(destroyL) = os

      val name = fromCString(dway_output_get_name(wlrOutput))
      System.err.println(s"[dway] New output: $name")
    }

  private val onOutputFrame: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], _: Ptr[Byte]) =>
      outputs.get(listener).foreach { os =>
        val sceneOut = WlrSceneApi.wlr_scene_get_scene_output(scene, os.wlrOutput)
        WlrSceneApi.wlr_scene_output_commit(sceneOut, null)
        val tsSize = dway_timespec_sizeof()
        val ts     = stdlib.malloc(tsSize)
        dway_clock_gettime_monotonic(ts)
        WlrSceneApi.wlr_scene_output_send_frame_done(sceneOut, ts)
        stdlib.free(ts)
      }
    }

  private val onOutputRequestState: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], data: Ptr[Byte]) =>
      outputs.get(listener).foreach { os =>
        val reqState = dway_output_event_request_state_get_state(data)
        WlrOutputApi.wlr_output_commit_state(os.wlrOutput, reqState)
      }
    }

  private val onOutputDestroy: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], _: Ptr[Byte]) =>
      outputs.get(listener).foreach { os =>
        wl_list_remove(os.frameListener.asInstanceOf[WlList])
        wl_list_remove(os.requestStateListener.asInstanceOf[WlList])
        wl_list_remove(os.destroyListener.asInstanceOf[WlList])
        outputs.remove(os.frameListener)
        outputs.remove(os.requestStateListener)
        outputs.remove(os.destroyListener)
        stdlib.free(os.frameListener)
        stdlib.free(os.requestStateListener)
        stdlib.free(os.destroyListener)
      }
    }

  // ════════════════════════════════════════════
  // XDG toplevel callbacks
  // ════════════════════════════════════════════

  private val onNewXdgToplevel: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], data: Ptr[Byte]) =>
      val xdgToplevel: WlrXdgToplevel = data
      val base                        = dway_xdg_toplevel_get_base(xdgToplevel)
      val sceneTree                   = WlrSceneApi.wlr_scene_xdg_surface_create(dway_scene_get_tree(scene), base)

      val mapL     = dway_listener_create(onToplevelMap)
      val unmapL   = dway_listener_create(onToplevelUnmap)
      val commitL  = dway_listener_create(onToplevelCommit)
      val destroyL = dway_listener_create(onToplevelDestroy)
      val moveL    = dway_listener_create(onToplevelRequestMove)
      val resizeL  = dway_listener_create(onToplevelRequestResize)

      val surface = dway_xdg_surface_get_surface(base)
      dway_signal_add(dway_surface_events_map(surface), mapL)
      dway_signal_add(dway_surface_events_unmap(surface), unmapL)
      dway_signal_add(dway_surface_events_commit(surface), commitL)
      dway_signal_add(dway_xdg_toplevel_events_destroy(xdgToplevel), destroyL)
      dway_signal_add(dway_xdg_toplevel_events_request_move(xdgToplevel), moveL)
      dway_signal_add(dway_xdg_toplevel_events_request_resize(xdgToplevel), resizeL)

      val ts = new ToplevelState(xdgToplevel, sceneTree, mapL, unmapL, commitL, destroyL, moveL, resizeL)

      sceneTreeMap(sceneTree) = ts

      toplevels(mapL) = ts
      toplevels(unmapL) = ts
      toplevels(commitL) = ts
      toplevels(destroyL) = ts
      toplevels(moveL) = ts
      toplevels(resizeL) = ts

      val titlePtr = dway_xdg_toplevel_get_title(xdgToplevel)
      val title    = if (titlePtr != null) fromCString(titlePtr) else "(untitled)"
      System.err.println(s"[dway] New XDG toplevel: $title")
    }

  private val onToplevelMap: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], _: Ptr[Byte]) =>
      toplevels.get(listener).foreach { ts =>
        val base    = dway_xdg_toplevel_get_base(ts.xdgToplevel)
        val surface = dway_xdg_surface_get_surface(base)
        focusToplevel(ts, surface)
      }
    }

  private val onToplevelUnmap: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], _: Ptr[Byte]) => () }

  private val onToplevelCommit: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], _: Ptr[Byte]) =>
      toplevels.get(listener).foreach { ts =>
        val base = dway_xdg_toplevel_get_base(ts.xdgToplevel)
        if (dway_xdg_surface_get_initial_commit(base) != 0) {
          WlrXdgShellApi.wlr_xdg_toplevel_set_size(ts.xdgToplevel, 0, 0)
        }
      }
    }

  private val onToplevelDestroy: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], _: Ptr[Byte]) =>
      toplevels.get(listener).foreach { ts =>
        wl_list_remove(ts.mapListener.asInstanceOf[WlList])
        wl_list_remove(ts.unmapListener.asInstanceOf[WlList])
        wl_list_remove(ts.commitListener.asInstanceOf[WlList])
        wl_list_remove(ts.destroyListener.asInstanceOf[WlList])
        wl_list_remove(ts.requestMoveListener.asInstanceOf[WlList])
        wl_list_remove(ts.requestResizeListener.asInstanceOf[WlList])
        toplevels.remove(ts.mapListener)
        toplevels.remove(ts.unmapListener)
        toplevels.remove(ts.commitListener)
        toplevels.remove(ts.destroyListener)
        toplevels.remove(ts.requestMoveListener)
        toplevels.remove(ts.requestResizeListener)
        sceneTreeMap.remove(ts.sceneTree)
        stdlib.free(ts.mapListener)
        stdlib.free(ts.unmapListener)
        stdlib.free(ts.commitListener)
        stdlib.free(ts.destroyListener)
        stdlib.free(ts.requestMoveListener)
        stdlib.free(ts.requestResizeListener)
      }
    }

  private val onToplevelRequestMove: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], _: Ptr[Byte]) => () }

  private val onToplevelRequestResize: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], _: Ptr[Byte]) => () }

  // ════════════════════════════════════════════
  // Input callbacks
  // ════════════════════════════════════════════

  private val onNewInput: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], data: Ptr[Byte]) =>
      val device: WlrInputDevice = data
      val devType                = dway_input_device_get_type(device)

      if (devType == dway_WLR_INPUT_DEVICE_KEYBOARD()) {
        newKeyboard(device)
      } else if (devType == dway_WLR_INPUT_DEVICE_POINTER()) {
        WlrCursorApi.wlr_cursor_attach_input_device(cursor, device)
        System.err.println("[dway] New pointer")
      }

      var caps = dway_WL_SEAT_CAPABILITY_POINTER()
      if (keyboards.nonEmpty) caps = caps | dway_WL_SEAT_CAPABILITY_KEYBOARD()
      WlrSeatApi.wlr_seat_set_capabilities(seat, caps)
    }

  private def newKeyboard(device: WlrInputDevice): Unit = {
    val wlrKeyboard = WlrKeyboardApi.wlr_keyboard_from_input_device(device)

    val ctx    = Xkb.xkb_context_new(XkbConstants.XKB_CONTEXT_NO_FLAGS)
    val keymap = Xkb.xkb_keymap_new_from_names(ctx, null, XkbConstants.XKB_KEYMAP_COMPILE_NO_FLAGS)
    WlrKeyboardApi.wlr_keyboard_set_keymap(wlrKeyboard, keymap)
    Xkb.xkb_keymap_unref(keymap)
    Xkb.xkb_context_unref(ctx)
    WlrKeyboardApi.wlr_keyboard_set_repeat_info(wlrKeyboard, 25, 600)

    val modL = dway_listener_create(onKeyboardModifiers)
    dway_signal_add(dway_keyboard_events_modifiers(wlrKeyboard), modL)

    val keyL = dway_listener_create(onKeyboardKey)
    dway_signal_add(dway_keyboard_events_key(wlrKeyboard), keyL)

    val destroyL = dway_listener_create(onKeyboardDestroy)
    dway_signal_add(dway_input_device_events_destroy(device), destroyL)

    WlrSeatApi.wlr_seat_set_keyboard(seat, wlrKeyboard)

    val ks = new KeyboardState(wlrKeyboard, modL, keyL, destroyL)
    keyboards(modL) = ks
    keyboards(keyL) = ks
    keyboards(destroyL) = ks

    System.err.println("[dway] New keyboard")
  }

  private val onKeyboardModifiers: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], _: Ptr[Byte]) =>
      keyboards.get(listener).foreach { ks =>
        WlrSeatApi.wlr_seat_set_keyboard(seat, ks.wlrKeyboard)
        WlrSeatApi.wlr_seat_keyboard_notify_modifiers(seat, dway_keyboard_get_modifiers_ptr(ks.wlrKeyboard))
      }
    }

  private val onKeyboardKey: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], data: Ptr[Byte]) =>
      keyboards.get(listener).foreach { ks =>
        val keycode  = dway_key_event_get_keycode(data)
        val keyState = dway_key_event_get_state(data)
        val timeMsec = dway_key_event_get_time_msec(data)

        val evdevKeycode = keycode + 8.toUInt
        val symsPtr      = stackalloc[Ptr[UInt]](1)
        val xkbState     = dway_keyboard_get_xkb_state(ks.wlrKeyboard)
        val nsyms        = Xkb.xkb_state_key_get_syms(xkbState, evdevKeycode, symsPtr)
        val syms         = !symsPtr

        val modifiers = WlrKeyboardApi.wlr_keyboard_get_modifiers(ks.wlrKeyboard)
        var handled   = false

        if (keyState == dway_WL_KEYBOARD_KEY_STATE_PRESSED().toUInt) {
          var i = 0
          while (i < nsyms && !handled) {
            if (
              (modifiers & dway_WLR_MODIFIER_ALT().toUInt) != 0.toUInt
              && syms(i) == dway_XKB_KEY_Escape()
            ) {
              wl_display_terminate(display)
              handled = true
            }
            i += 1
          }
        }

        if (!handled) {
          WlrSeatApi.wlr_seat_set_keyboard(seat, ks.wlrKeyboard)
          WlrSeatApi.wlr_seat_keyboard_notify_key(seat, timeMsec, keycode, keyState)
        }
      }
    }

  private val onKeyboardDestroy: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (listener: Ptr[Byte], _: Ptr[Byte]) =>
      keyboards.get(listener).foreach { ks =>
        wl_list_remove(ks.modifiersListener.asInstanceOf[WlList])
        wl_list_remove(ks.keyListener.asInstanceOf[WlList])
        wl_list_remove(ks.destroyListener.asInstanceOf[WlList])
        keyboards.remove(ks.modifiersListener)
        keyboards.remove(ks.keyListener)
        keyboards.remove(ks.destroyListener)
        stdlib.free(ks.modifiersListener)
        stdlib.free(ks.keyListener)
        stdlib.free(ks.destroyListener)
      }
    }

  // ════════════════════════════════════════════
  // Cursor callbacks
  // ════════════════════════════════════════════

  private def processCursorMotion(timeMsec: UInt): Unit = {
    val sx   = stackalloc[CDouble](1)
    val sy   = stackalloc[CDouble](1)
    val curX = dway_cursor_get_x(cursor)
    val curY = dway_cursor_get_y(cursor)
    val node = WlrSceneApi.wlr_scene_node_at(dway_scene_get_tree_node(scene), curX, curY, sx, sy)

    if (node == null) {
      Zone { WlrCursorApi.wlr_cursor_set_xcursor(cursor, cursorMgr, c"default") }
      WlrSeatApi.wlr_seat_pointer_clear_focus(seat)
      return
    }

    val nodeType = dway_scene_node_get_type(node)
    if (nodeType != dway_WLR_SCENE_NODE_BUFFER()) {
      Zone { WlrCursorApi.wlr_cursor_set_xcursor(cursor, cursorMgr, c"default") }
      WlrSeatApi.wlr_seat_pointer_clear_focus(seat)
      return
    }

    val sceneBuf     = WlrSceneApi.wlr_scene_buffer_from_node(node)
    val sceneSurface = WlrSceneApi.wlr_scene_surface_try_from_buffer(sceneBuf)
    if (sceneSurface == null) {
      Zone { WlrCursorApi.wlr_cursor_set_xcursor(cursor, cursorMgr, c"default") }
      WlrSeatApi.wlr_seat_pointer_clear_focus(seat)
      return
    }

    val surface = dway_scene_surface_get_surface(sceneSurface)
    WlrSeatApi.wlr_seat_pointer_notify_enter(seat, surface, !sx, !sy)
    WlrSeatApi.wlr_seat_pointer_notify_motion(seat, timeMsec, !sx, !sy)
  }

  private val onCursorMotion: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], data: Ptr[Byte]) =>
      val device = dway_pointer_motion_get_device(data)
      val dx     = dway_pointer_motion_get_delta_x(data)
      val dy     = dway_pointer_motion_get_delta_y(data)
      val time   = dway_pointer_motion_get_time_msec(data)
      WlrCursorApi.wlr_cursor_move(cursor, device, dx, dy)
      processCursorMotion(time)
    }

  private val onCursorMotionAbsolute: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], data: Ptr[Byte]) =>
      val device = dway_pointer_motion_abs_get_device(data)
      val x      = dway_pointer_motion_abs_get_x(data)
      val y      = dway_pointer_motion_abs_get_y(data)
      val time   = dway_pointer_motion_abs_get_time_msec(data)
      WlrCursorApi.wlr_cursor_warp_absolute(cursor, device, x, y)
      processCursorMotion(time)
    }

  private val onCursorButton: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], data: Ptr[Byte]) =>
      val timeMsec = dway_pointer_button_get_time_msec(data)
      val button   = dway_pointer_button_get_button(data)
      val state    = dway_pointer_button_get_state(data)

      WlrSeatApi.wlr_seat_pointer_notify_button(seat, timeMsec, button, state)

      if (state != dway_WL_POINTER_BUTTON_STATE_RELEASED().toUInt) {
        val sx   = stackalloc[CDouble](1)
        val sy   = stackalloc[CDouble](1)
        val curX = dway_cursor_get_x(cursor)
        val curY = dway_cursor_get_y(cursor)
        val node = WlrSceneApi.wlr_scene_node_at(dway_scene_get_tree_node(scene), curX, curY, sx, sy)

        if (node != null && dway_scene_node_get_type(node) == dway_WLR_SCENE_NODE_BUFFER()) {
          val sceneBuf     = WlrSceneApi.wlr_scene_buffer_from_node(node)
          val sceneSurface = WlrSceneApi.wlr_scene_surface_try_from_buffer(sceneBuf)
          if (sceneSurface != null) {
            val surface = dway_scene_surface_get_surface(sceneSurface)
            // Walk up the scene tree to find the toplevel
            var tree  = dway_scene_node_get_parent(node)
            var found = false
            while (tree != null && !found) {
              sceneTreeMap.get(tree) match {
                case Some(ts) =>
                  focusToplevel(ts, surface)
                  found = true
                case None =>
                  val treeNode = dway_scene_tree_get_node(tree)
                  tree = dway_scene_node_get_parent(treeNode)
              }
            }
          }
        }
      }
    }

  private val onCursorAxis: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], data: Ptr[Byte]) =>
      WlrSeatApi.wlr_seat_pointer_notify_axis(
        seat,
        dway_pointer_axis_get_time_msec(data),
        dway_pointer_axis_get_orientation(data),
        dway_pointer_axis_get_delta(data),
        dway_pointer_axis_get_delta_discrete(data),
        dway_pointer_axis_get_source(data),
        dway_pointer_axis_get_relative_direction(data)
      )
    }

  private val onCursorFrame: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], _: Ptr[Byte]) =>
      WlrSeatApi.wlr_seat_pointer_notify_frame(seat)
    }

  private val onRequestSetCursor: WlNotifyFunc =
    CFuncPtr2.fromScalaFunction { (_: Ptr[Byte], data: Ptr[Byte]) =>
      val eventClient   = dway_set_cursor_event_get_seat_client(data)
      val focusedClient = dway_seat_pointer_get_focused_client(seat)
      if (focusedClient == eventClient) {
        val surface = dway_set_cursor_event_get_surface(data)
        val hx      = dway_set_cursor_event_get_hotspot_x(data)
        val hy      = dway_set_cursor_event_get_hotspot_y(data)
        WlrCursorApi.wlr_cursor_set_surface(cursor, surface, hx, hy)
      }
    }

  // ════════════════════════════════════════════
  // Focus helper
  // ════════════════════════════════════════════

  private def focusToplevel(ts: ToplevelState, surface: WlrSurface): Unit = {
    val prevSurface = dway_seat_keyboard_get_focused_surface(seat)
    if (prevSurface == surface) return

    if (prevSurface != null) {
      val prevToplevel = WlrXdgShellApi.wlr_xdg_toplevel_try_from_wlr_surface(prevSurface)
      if (prevToplevel != null) {
        WlrXdgShellApi.wlr_xdg_toplevel_set_activated(prevToplevel, false)
      }
    }

    WlrSceneApi.wlr_scene_node_raise_to_top(dway_scene_tree_get_node(ts.sceneTree))
    WlrXdgShellApi.wlr_xdg_toplevel_set_activated(ts.xdgToplevel, true)

    val keyboard = WlrSeatApi.wlr_seat_get_keyboard(seat)
    if (keyboard != null) {
      val base = dway_xdg_toplevel_get_base(ts.xdgToplevel)
      val surf = dway_xdg_surface_get_surface(base)
      WlrSeatApi.wlr_seat_keyboard_notify_enter(
        seat,
        surf,
        dway_keyboard_get_keycodes(keyboard),
        dway_keyboard_get_num_keycodes(keyboard),
        dway_keyboard_get_modifiers_ptr(keyboard)
      )
    }
  }
}
