from pathlib import Path
import sys

root = Path(sys.argv[1])
p = root / 'server/app/src/main/java/leap/scrcpy/server/Main.kt'
s = p.read_text(encoding='utf-8')

s = s.replace('private fun getDisplayInfo(): DisplayInfoMessage {\n        val displayInfo = displayManagerGlobal.call("getDisplayInfo", 0)', '''private var targetDisplayId = 0

    private fun chooseTargetDisplayId(): Int {
        // Samsung DeX is a secondary public display. Prefer the largest active
        // non-default display; fall back to display 0 when DeX is not active.
        return try {
            val dm = FakeContext.instance.getSystemService(DisplayManager::class.java)!!
            dm.displays
                .filter { it.displayId != 0 && it.state != android.view.Display.STATE_OFF }
                .maxByOrNull { it.mode.physicalWidth.toLong() * it.mode.physicalHeight.toLong() }
                ?.displayId ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun getDisplayInfo(): DisplayInfoMessage {
        targetDisplayId = chooseTargetDisplayId()
        val displayInfo = displayManagerGlobal.call("getDisplayInfo", targetDisplayId)''')

s = s.replace('if (displayId != 0) {\n                            return\n                        }', 'if (displayId != targetDisplayId) {\n                            return\n                        }')
s = s.replace('if (displayId != 0) {\n                        return\n                    }', 'if (displayId != targetDisplayId) {\n                        return\n                    }')

p.write_text(s, encoding='utf-8')
print('Patched leap-scrcpy for Samsung DeX secondary-display geometry')
