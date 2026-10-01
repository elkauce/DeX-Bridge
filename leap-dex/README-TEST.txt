DeX Bridge - Deskflow/leap-scrcpy prototype

Prerequisites already configured on Manuel's PC:
- Deskflow server running
- screen named Android placed to the right
- TLS client-certificate requirement disabled
- Samsung connected by ADB wireless
- physical DeX monitor active

Run START-DEX-BRIDGE.cmd.
Then move the Windows pointer through the right edge.
Expected: Deskflow enters Android and the UHID pointer controls Samsung DeX.
Move back through the left edge to return to Windows.

This prototype intentionally uses Node.js + the Android-side leap server instead of a custom Windows EXE, avoiding the unsigned scrcpy.exe path that Smart App Control blocked.
