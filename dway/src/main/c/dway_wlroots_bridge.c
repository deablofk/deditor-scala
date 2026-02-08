// Minimal C bridge for wlroots <-> Scala Native.
// Only provides things impossible from pure Scala Native:
//   1. wl_signal_add (static inline in wayland headers)
//   2. Struct field accessors for signals and internal fields
//   3. wlr_output_state alloc/free (size unknown to Scala)
//   4. wl_listener allocation with notify callback
// All compositor logic lives in Scala.

#include <stdlib.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#include <wayland-server-core.h>
#include <wlr/backend.h>
#include <wlr/render/wlr_renderer.h>
#include <wlr/render/allocator.h>
#include <wlr/types/wlr_compositor.h>
#include <wlr/types/wlr_subcompositor.h>
#include <wlr/types/wlr_output.h>
#include <wlr/types/wlr_output_layout.h>
#include <wlr/types/wlr_scene.h>
#include <wlr/types/wlr_xdg_shell.h>
#include <wlr/types/wlr_seat.h>
#include <wlr/types/wlr_cursor.h>
#include <wlr/types/wlr_xcursor_manager.h>
#include <wlr/types/wlr_data_device.h>
#include <wlr/types/wlr_input_device.h>
#include <wlr/types/wlr_keyboard.h>
#include <wlr/types/wlr_pointer.h>
#include <wlr/util/log.h>

// ============================================================
// 1. wl_signal_add wrapper (static inline in C headers)
// ============================================================

void dway_signal_add(struct wl_signal *signal, struct wl_listener *listener) {
    wl_signal_add(signal, listener);
}

// ============================================================
// 2. wl_listener helpers
//    Layout: { wl_list link (prev,next); void (*notify)(...) }
//    Total size = 3 pointers = 24 bytes on 64-bit
// ============================================================

struct wl_listener* dway_listener_create(
        void (*notify)(struct wl_listener *, void *)) {
    struct wl_listener *l = calloc(1, sizeof(struct wl_listener));
    l->notify = notify;
    return l;
}

void dway_listener_destroy(struct wl_listener *listener) {
    if (listener) {
        wl_list_remove(&listener->link);
        free(listener);
    }
}

size_t dway_listener_sizeof(void) {
    return sizeof(struct wl_listener);
}

// Set notify on a pre-allocated listener (e.g. stackalloc'd from Scala)
void dway_listener_set_notify(struct wl_listener *listener,
        void (*notify)(struct wl_listener *, void *)) {
    memset(listener, 0, sizeof(struct wl_listener));
    listener->notify = notify;
}

// ============================================================
// 3. wlr_output_state helpers (opaque size)
// ============================================================

struct wlr_output_state* dway_output_state_create(void) {
    struct wlr_output_state *s = calloc(1, sizeof(struct wlr_output_state));
    wlr_output_state_init(s);
    return s;
}

void dway_output_state_free(struct wlr_output_state *state) {
    if (state) {
        wlr_output_state_finish(state);
        free(state);
    }
}

// ============================================================
// 4. Backend signal accessors
// ============================================================

struct wl_signal* dway_backend_events_new_output(struct wlr_backend *b) {
    return &b->events.new_output;
}

struct wl_signal* dway_backend_events_new_input(struct wlr_backend *b) {
    return &b->events.new_input;
}

// ============================================================
// 5. Output signal & field accessors
// ============================================================

struct wl_signal* dway_output_events_frame(struct wlr_output *o) {
    return &o->events.frame;
}

struct wl_signal* dway_output_events_request_state(struct wlr_output *o) {
    return &o->events.request_state;
}

struct wl_signal* dway_output_events_destroy(struct wlr_output *o) {
    return &o->events.destroy;
}

const char* dway_output_get_name(struct wlr_output *o) {
    return o->name;
}

// ============================================================
// 6. Output request_state event accessor
// ============================================================

const struct wlr_output_state* dway_output_event_request_state_get_state(
        struct wlr_output_event_request_state *e) {
    return e->state;
}

// ============================================================
// 7. XDG shell signal accessors
// ============================================================

struct wl_signal* dway_xdg_shell_events_new_toplevel(struct wlr_xdg_shell *s) {
    return &s->events.new_toplevel;
}

// ============================================================
// 8. XDG toplevel signal & field accessors
// ============================================================

struct wl_signal* dway_xdg_toplevel_events_destroy(struct wlr_xdg_toplevel *t) {
    return &t->events.destroy;
}

struct wl_signal* dway_xdg_toplevel_events_request_move(struct wlr_xdg_toplevel *t) {
    return &t->events.request_move;
}

struct wl_signal* dway_xdg_toplevel_events_request_resize(struct wlr_xdg_toplevel *t) {
    return &t->events.request_resize;
}

struct wlr_xdg_surface* dway_xdg_toplevel_get_base(struct wlr_xdg_toplevel *t) {
    return t->base;
}

const char* dway_xdg_toplevel_get_title(struct wlr_xdg_toplevel *t) {
    return t->title;
}

// ============================================================
// 9. XDG surface field accessors
// ============================================================

struct wlr_surface* dway_xdg_surface_get_surface(struct wlr_xdg_surface *s) {
    return s->surface;
}

int dway_xdg_surface_get_initial_commit(struct wlr_xdg_surface *s) {
    return s->initial_commit;
}

// ============================================================
// 10. wlr_surface signal accessors
// ============================================================

struct wl_signal* dway_surface_events_map(struct wlr_surface *s) {
    return &s->events.map;
}

struct wl_signal* dway_surface_events_unmap(struct wlr_surface *s) {
    return &s->events.unmap;
}

struct wl_signal* dway_surface_events_commit(struct wlr_surface *s) {
    return &s->events.commit;
}

// ============================================================
// 11. Keyboard signal & field accessors
// ============================================================

struct wl_signal* dway_keyboard_events_modifiers(struct wlr_keyboard *k) {
    return &k->events.modifiers;
}

struct wl_signal* dway_keyboard_events_key(struct wlr_keyboard *k) {
    return &k->events.key;
}

struct xkb_state* dway_keyboard_get_xkb_state(struct wlr_keyboard *k) {
    return k->xkb_state;
}

struct wlr_keyboard_modifiers* dway_keyboard_get_modifiers_ptr(struct wlr_keyboard *k) {
    return &k->modifiers;
}

const uint32_t* dway_keyboard_get_keycodes(struct wlr_keyboard *k) {
    return k->keycodes;
}

size_t dway_keyboard_get_num_keycodes(struct wlr_keyboard *k) {
    return k->num_keycodes;
}

// ============================================================
// 12. Keyboard key event accessors
// ============================================================

uint32_t dway_key_event_get_time_msec(struct wlr_keyboard_key_event *e) {
    return e->time_msec;
}

uint32_t dway_key_event_get_keycode(struct wlr_keyboard_key_event *e) {
    return e->keycode;
}

uint32_t dway_key_event_get_state(struct wlr_keyboard_key_event *e) {
    return e->state;
}

// ============================================================
// 13. Input device signal & field accessors
// ============================================================

struct wl_signal* dway_input_device_events_destroy(struct wlr_input_device *d) {
    return &d->events.destroy;
}

int dway_input_device_get_type(struct wlr_input_device *d) {
    return (int)d->type;
}

// ============================================================
// 14. Cursor signal & field accessors
// ============================================================

struct wl_signal* dway_cursor_events_motion(struct wlr_cursor *c) {
    return &c->events.motion;
}

struct wl_signal* dway_cursor_events_motion_absolute(struct wlr_cursor *c) {
    return &c->events.motion_absolute;
}

struct wl_signal* dway_cursor_events_button(struct wlr_cursor *c) {
    return &c->events.button;
}

struct wl_signal* dway_cursor_events_axis(struct wlr_cursor *c) {
    return &c->events.axis;
}

struct wl_signal* dway_cursor_events_frame(struct wlr_cursor *c) {
    return &c->events.frame;
}

double dway_cursor_get_x(struct wlr_cursor *c) { return c->x; }
double dway_cursor_get_y(struct wlr_cursor *c) { return c->y; }

// ============================================================
// 15. Pointer motion event accessors
// ============================================================

struct wlr_input_device* dway_pointer_motion_get_device(
        struct wlr_pointer_motion_event *e) {
    return &e->pointer->base;
}

double dway_pointer_motion_get_delta_x(struct wlr_pointer_motion_event *e) {
    return e->delta_x;
}

double dway_pointer_motion_get_delta_y(struct wlr_pointer_motion_event *e) {
    return e->delta_y;
}

uint32_t dway_pointer_motion_get_time_msec(struct wlr_pointer_motion_event *e) {
    return e->time_msec;
}

// ============================================================
// 16. Pointer motion absolute event accessors
// ============================================================

struct wlr_input_device* dway_pointer_motion_abs_get_device(
        struct wlr_pointer_motion_absolute_event *e) {
    return &e->pointer->base;
}

double dway_pointer_motion_abs_get_x(struct wlr_pointer_motion_absolute_event *e) {
    return e->x;
}

double dway_pointer_motion_abs_get_y(struct wlr_pointer_motion_absolute_event *e) {
    return e->y;
}

uint32_t dway_pointer_motion_abs_get_time_msec(
        struct wlr_pointer_motion_absolute_event *e) {
    return e->time_msec;
}

// ============================================================
// 17. Pointer button event accessors
// ============================================================

uint32_t dway_pointer_button_get_time_msec(struct wlr_pointer_button_event *e) {
    return e->time_msec;
}

uint32_t dway_pointer_button_get_button(struct wlr_pointer_button_event *e) {
    return e->button;
}

uint32_t dway_pointer_button_get_state(struct wlr_pointer_button_event *e) {
    return e->state;
}

// ============================================================
// 18. Pointer axis event accessors
// ============================================================

uint32_t dway_pointer_axis_get_time_msec(struct wlr_pointer_axis_event *e) {
    return e->time_msec;
}

uint32_t dway_pointer_axis_get_orientation(struct wlr_pointer_axis_event *e) {
    return e->orientation;
}

double dway_pointer_axis_get_delta(struct wlr_pointer_axis_event *e) {
    return e->delta;
}

int32_t dway_pointer_axis_get_delta_discrete(struct wlr_pointer_axis_event *e) {
    return e->delta_discrete;
}

uint32_t dway_pointer_axis_get_source(struct wlr_pointer_axis_event *e) {
    return e->source;
}

uint32_t dway_pointer_axis_get_relative_direction(struct wlr_pointer_axis_event *e) {
    return e->relative_direction;
}

// ============================================================
// 19. Seat signal & field accessors
// ============================================================

struct wl_signal* dway_seat_events_request_set_cursor(struct wlr_seat *s) {
    return &s->events.request_set_cursor;
}

struct wlr_surface* dway_seat_keyboard_get_focused_surface(struct wlr_seat *s) {
    return s->keyboard_state.focused_surface;
}

struct wlr_seat_client* dway_seat_pointer_get_focused_client(struct wlr_seat *s) {
    return s->pointer_state.focused_client;
}

// ============================================================
// 20. Seat pointer request_set_cursor event accessors
// ============================================================

struct wlr_seat_client* dway_set_cursor_event_get_seat_client(
        struct wlr_seat_pointer_request_set_cursor_event *e) {
    return e->seat_client;
}

struct wlr_surface* dway_set_cursor_event_get_surface(
        struct wlr_seat_pointer_request_set_cursor_event *e) {
    return e->surface;
}

int32_t dway_set_cursor_event_get_hotspot_x(
        struct wlr_seat_pointer_request_set_cursor_event *e) {
    return e->hotspot_x;
}

int32_t dway_set_cursor_event_get_hotspot_y(
        struct wlr_seat_pointer_request_set_cursor_event *e) {
    return e->hotspot_y;
}

// ============================================================
// 21. Scene node helpers
// ============================================================

int dway_scene_node_get_type(struct wlr_scene_node *n) {
    return (int)n->type;
}

struct wlr_scene_tree* dway_scene_node_get_parent(struct wlr_scene_node *n) {
    return n->parent;
}

void* dway_scene_node_get_data(struct wlr_scene_node *n) {
    return n->data;
}

void dway_scene_node_set_data(struct wlr_scene_node *n, void *data) {
    n->data = data;
}

struct wlr_scene_node* dway_scene_tree_get_node(struct wlr_scene_tree *t) {
    return &t->node;
}

struct wlr_scene_node* dway_scene_get_tree_node(struct wlr_scene *s) {
    return &s->tree.node;
}

struct wlr_scene_tree* dway_scene_get_tree(struct wlr_scene *s) {
    return &s->tree;
}

struct wlr_surface* dway_scene_surface_get_surface(
        struct wlr_scene_surface *ss) {
    return ss->surface;
}

// ============================================================
// 22. Timespec helper (clock_gettime)
// ============================================================

void dway_clock_gettime_monotonic(struct timespec *ts) {
    clock_gettime(CLOCK_MONOTONIC, ts);
}

size_t dway_timespec_sizeof(void) {
    return sizeof(struct timespec);
}

// ============================================================
// 23. Constants
// ============================================================

int dway_WLR_SCENE_NODE_BUFFER(void) { return WLR_SCENE_NODE_BUFFER; }
int dway_WLR_SCENE_NODE_TREE(void) { return WLR_SCENE_NODE_TREE; }
int dway_WLR_INPUT_DEVICE_KEYBOARD(void) { return WLR_INPUT_DEVICE_KEYBOARD; }
int dway_WLR_INPUT_DEVICE_POINTER(void) { return WLR_INPUT_DEVICE_POINTER; }
int dway_WL_KEYBOARD_KEY_STATE_PRESSED(void) { return WL_KEYBOARD_KEY_STATE_PRESSED; }
int dway_WL_POINTER_BUTTON_STATE_RELEASED(void) { return WL_POINTER_BUTTON_STATE_RELEASED; }
int dway_WLR_MODIFIER_ALT(void) { return WLR_MODIFIER_ALT; }
uint32_t dway_WL_SEAT_CAPABILITY_POINTER(void) { return WL_SEAT_CAPABILITY_POINTER; }
uint32_t dway_WL_SEAT_CAPABILITY_KEYBOARD(void) { return WL_SEAT_CAPABILITY_KEYBOARD; }

// XKB_KEY_Escape = 0xff1b
uint32_t dway_XKB_KEY_Escape(void) { return 0xff1b; }
