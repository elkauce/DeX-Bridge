from pathlib import Path

root = Path("scrcpy")
screen_c = root / "app/src/screen.c"
screen_h = root / "app/src/screen.h"
input_c = root / "app/src/input_manager.c"
input_h = root / "app/src/input_manager.h"

# Phase 1: keep UHID alive but do not capture at startup.
s = screen_c.read_text(encoding="utf-8")
needle = "        sc_mouse_capture_set_active(&screen->mc, true);"
if needle not in s:
    raise SystemExit("scrcpy 4.1 startup capture hook not found")
s = s.replace(needle,
'''        // DeX Bridge: UHID stays connected, but Windows keeps ownership
        // until the user deliberately crosses the right edge.
        // Capture is activated by the bridge state machine.
''', 1)
screen_c.write_text(s, encoding="utf-8")

# Add state directly to sc_screen so capture and SDL window share lifecycle.
h = screen_h.read_text(encoding="utf-8")
needle = "    struct sc_mouse_capture mc; // only used in mouse relative mode\n"
insert = needle + '''\n    // DeX Bridge WINDOWS <-> DEX state.\n    bool dex_bridge_active;\n    int32_t dex_bridge_virtual_x;\n    int32_t dex_bridge_virtual_y;\n    uint64_t dex_bridge_last_switch_ms;\n'''
if needle not in h:
    raise SystemExit("screen state insertion point not found")
h = h.replace(needle, insert, 1)
screen_h.write_text(h, encoding="utf-8")

# Input manager gets a public periodic tick. This is intentionally separated
# from mouse events: while Windows owns the pointer, the SDL scrcpy window is
# not guaranteed to receive motion events.
h = input_h.read_text(encoding="utf-8")
needle = "void\nsc_input_manager_handle_event(struct sc_input_manager *im,\n                              const SDL_Event *event);\n"
insert = needle + '''\n// DeX Bridge: poll the global Windows pointer while UHID is not captured.\nvoid\nsc_input_manager_dex_bridge_tick(struct sc_input_manager *im);\n'''
if needle not in h:
    raise SystemExit("input manager declaration insertion point not found")
h = h.replace(needle, insert, 1)
input_h.write_text(h, encoding="utf-8")

# Mark the source so CI proves that the relative-motion interception point is
# still present in upstream 4.1 before later commits wire virtual X/Y into it.
s = input_c.read_text(encoding="utf-8")
needle = "event->xrel"
if needle not in s:
    raise SystemExit("relative mouse motion hook not found")
input_c.write_text(s, encoding="utf-8")

print("DeX Bridge phase-1 patch applied to scrcpy 4.1")
