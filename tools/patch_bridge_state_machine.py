from pathlib import Path

root = Path("scrcpy")
screen_c = root / "app/src/screen.c"
screen_h = root / "app/src/screen.h"
input_c = root / "app/src/input_manager.c"
events_h = root / "app/src/events.h"

# Phase 2 runs after patch_dex_bridge.py.

# Add timer state to sc_screen.
h = screen_h.read_text(encoding="utf-8")
needle = "    uint64_t dex_bridge_last_switch_ms;\n"
insert = needle + "    unsigned dex_bridge_edge_ticks;\n    SDL_TimerID dex_bridge_timer;\n"
if needle not in h:
    raise SystemExit("phase-1 screen state not found")
h = h.replace(needle, insert, 1)
screen_h.write_text(h, encoding="utf-8")

# Add a private SDL event for polling the global Windows pointer.
h = events_h.read_text(encoding="utf-8")
needle = "    SC_EVENT_DISCONNECTED_TIMEOUT,\n"
if needle not in h:
    raise SystemExit("events insertion point not found")
h = h.replace(needle, needle + "    SC_EVENT_DEX_BRIDGE_TICK,\n", 1)
events_h.write_text(h, encoding="utf-8")

s = screen_c.read_text(encoding="utf-8")

# Timer posts onto the SDL main thread; all capture/window work remains there.
needle = "bool\nsc_screen_init(struct sc_screen *screen,\n               const struct sc_screen_params *params) {\n"
insert = """static Uint32\ndex_bridge_timer_cb(void *userdata, SDL_TimerID timer_id, Uint32 interval) {\n    (void) userdata;\n    (void) timer_id;\n    sc_push_event(SC_EVENT_DEX_BRIDGE_TICK);\n    return interval;\n}\n\nbool\nsc_screen_init(struct sc_screen *screen,\n               const struct sc_screen_params *params) {\n"""
if needle not in s:
    raise SystemExit("screen init hook not found")
s = s.replace(needle, insert, 1)

needle = "    sc_mouse_capture_init(&screen->mc, screen->window, params->shortcut_mods);\n"
insert = needle + """    screen->dex_bridge_active = false;\n    screen->dex_bridge_virtual_x = 0;\n    screen->dex_bridge_virtual_y = 0;\n    screen->dex_bridge_last_switch_ms = 0;\n    screen->dex_bridge_edge_ticks = 0;\n    screen->dex_bridge_timer = 0;\n    if (!params->video && sc_screen_is_relative_mode(screen)) {\n        screen->dex_bridge_timer = SDL_AddTimer(10, dex_bridge_timer_cb, screen);\n        if (!screen->dex_bridge_timer) {\n            LOGW(\"DeX Bridge: could not start edge timer: %s\", SDL_GetError());\n        }\n    }\n"""
if needle not in s:
    raise SystemExit("mouse capture init hook not found")
s = s.replace(needle, insert, 1)

needle = "void\nsc_screen_destroy(struct sc_screen *screen) {\n#ifndef NDEBUG\n"
insert = """void\nsc_screen_destroy(struct sc_screen *screen) {\n    if (screen->dex_bridge_timer) {\n        SDL_RemoveTimer(screen->dex_bridge_timer);\n        screen->dex_bridge_timer = 0;\n    }\n#ifndef NDEBUG\n"""
if needle not in s:
    raise SystemExit("screen destroy hook not found")
s = s.replace(needle, insert, 1)

needle = "void\nsc_screen_handle_event(struct sc_screen *screen, const SDL_Event *event) {\n    switch (event->type) {\n"
insert = """void\nsc_screen_handle_event(struct sc_screen *screen, const SDL_Event *event) {\n    switch (event->type) {\n        case SC_EVENT_DEX_BRIDGE_TICK:\n            sc_input_manager_dex_bridge_tick(&screen->im);\n            return;\n"""
if needle not in s:
    raise SystemExit("screen event hook not found")
s = s.replace(needle, insert, 1)
screen_c.write_text(s, encoding="utf-8")

s = input_c.read_text(encoding="utf-8")
needle = "static void\nsc_input_manager_process_mouse_motion(struct sc_input_manager *im,\n                                      const SDL_MouseMotionEvent *event) {\n"
helper = r'''static int32_t
dex_bridge_env_dimension(const char *name, int32_t fallback) {
    const char *value = getenv(name);
    if (!value || !*value) {
        return fallback;
    }
    char *end;
    long parsed = strtol(value, &end, 10);
    return *end == '\0' && parsed > 0 && parsed <= 16384
         ? (int32_t) parsed : fallback;
}

static void
dex_bridge_send_relative(struct sc_input_manager *im, int32_t dx, int32_t dy) {
    struct sc_mouse_motion_event evt = {
        .position = {
            .screen_size = {0, 0},
            .point = {0, 0},
        },
        .pointer_id = SC_POINTER_ID_MOUSE,
        .xrel = dx,
        .yrel = dy,
        .buttons_state = im->mouse_buttons_state,
    };
    im->mp->ops->process_mouse_motion(im->mp, &evt);
}

void
sc_input_manager_dex_bridge_tick(struct sc_input_manager *im) {
    if (!im->mp || !im->mp->relative_mode || im->disconnected
            || im->screen->paused || im->screen->dex_bridge_active) {
        return;
    }

    float gx;
    float gy;
    SDL_GetGlobalMouseState(&gx, &gy);

    SDL_DisplayID display = SDL_GetPrimaryDisplay();
    SDL_Rect bounds;
    if (!display || !SDL_GetDisplayBounds(display, &bounds)) {
        im->screen->dex_bridge_edge_ticks = 0;
        return;
    }

    uint64_t now = SDL_GetTicks();
    int32_t right = bounds.x + bounds.w - 1;
    bool at_right_edge = gx >= right - 1 && gy >= bounds.y
                      && gy < bounds.y + bounds.h;

    if (!at_right_edge || now - im->screen->dex_bridge_last_switch_ms < 350) {
        im->screen->dex_bridge_edge_ticks = 0;
        return;
    }

    // Three consecutive polls at the edge filter accidental one-frame touches.
    if (++im->screen->dex_bridge_edge_ticks < 3) {
        return;
    }
    im->screen->dex_bridge_edge_ticks = 0;

    int32_t dex_w = dex_bridge_env_dimension("DEX_BRIDGE_WIDTH", 1080);
    int32_t dex_h = dex_bridge_env_dimension("DEX_BRIDGE_HEIGHT", 1920);
    int32_t entry_x = dex_w < 96 ? dex_w / 8 : 48;
    int32_t rel_y = (int32_t) gy - bounds.y;
    int32_t mapped_y = bounds.h > 1
                     ? (int64_t) rel_y * (dex_h - 1) / (bounds.h - 1)
                     : 0;

    sc_mouse_capture_set_active(&im->screen->mc, true);
    im->screen->dex_bridge_active = true;
    im->screen->dex_bridge_virtual_x = entry_x;
    im->screen->dex_bridge_virtual_y = mapped_y;
    im->screen->dex_bridge_last_switch_ms = now;

    // Anchor the Android pointer to the left/top, then restore proportional Y.
    // Android clamps the large relative move at the physical display edge.
    dex_bridge_send_relative(im, -32767, -32767);
    dex_bridge_send_relative(im, entry_x, mapped_y);
    LOGI("DeX Bridge: WINDOWS -> DEX at y=%d", (int) mapped_y);
}

static void
sc_input_manager_process_mouse_motion(struct sc_input_manager *im,
                                      const SDL_MouseMotionEvent *event) {
'''
if needle not in s:
    raise SystemExit("mouse motion hook not found")
s = s.replace(needle, helper, 1)

needle = """    struct sc_mouse_motion_event evt = {
        .position = sc_input_manager_get_position(im, event->x, event->y),
"""
insert = """    if (im->mp->relative_mode && im->screen->dex_bridge_active) {
        int32_t dex_w = dex_bridge_env_dimension("DEX_BRIDGE_WIDTH", 1080);
        int32_t dex_h = dex_bridge_env_dimension("DEX_BRIDGE_HEIGHT", 1920);
        int64_t nx = (int64_t) im->screen->dex_bridge_virtual_x + event->xrel;
        int64_t ny = (int64_t) im->screen->dex_bridge_virtual_y + event->yrel;
        if (nx < 0) nx = 0;
        if (nx >= dex_w) nx = dex_w - 1;
        if (ny < 0) ny = 0;
        if (ny >= dex_h) ny = dex_h - 1;
        im->screen->dex_bridge_virtual_x = (int32_t) nx;
        im->screen->dex_bridge_virtual_y = (int32_t) ny;

        uint64_t now = SDL_GetTicks();
        if (nx == 0 && event->xrel < 0
                && now - im->screen->dex_bridge_last_switch_ms >= 350) {
            SDL_DisplayID display = SDL_GetPrimaryDisplay();
            SDL_Rect bounds;
            if (display && SDL_GetDisplayBounds(display, &bounds)) {
                int32_t wy = dex_h > 1
                           ? (int64_t) ny * (bounds.h - 1) / (dex_h - 1)
                           : 0;
                sc_mouse_capture_set_active(&im->screen->mc, false);
                im->screen->dex_bridge_active = false;
                im->screen->dex_bridge_last_switch_ms = now;
                SDL_WarpMouseGlobal(bounds.x + bounds.w - 3, bounds.y + wy);
                LOGI("DeX Bridge: DEX -> WINDOWS at y=%d", (int) wy);
                return;
            }
        }
    }

    struct sc_mouse_motion_event evt = {
        .position = sc_input_manager_get_position(im, event->x, event->y),
"""
if needle not in s:
    raise SystemExit("relative interception point not found")
s = s.replace(needle, insert, 1)
input_c.write_text(s, encoding="utf-8")

print("DeX Bridge automatic WINDOWS <-> DEX state machine applied")
