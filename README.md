# OSCPad

A TouchOSC-style OSC control surface for Android phones and tablets: native Kotlin and Jetpack Compose, no third-party OSC libraries.

## Features

- **Controls:** Button (momentary), Toggle, Fader (vertical or horizontal), Knob, XY pad, Radio (segmented selector), Label, and Color wheel (hue/saturation wheel plus brightness bar).
- **Multitouch:** each control tracks its own finger, so you can move several faders at once.
- **Bidirectional:** sends to `host:sendPort` and listens on `receivePort`. Incoming messages move the matching controls (motorized-fader style). Labels show incoming text.
- **Layout editor:** tap ✎, then add controls, drag to move, drag the corner handle to resize, and double-tap to edit a control's name, OSC address, range, value type (Float, Int or Bool), color and number of radio segments.
  - **Guide lines:** while you drag, edges and centers snap to other controls' edges and centers (and the page center), and a magenta dashed line shows each alignment.
  - **Grid:** the toolbar's Grid menu sets the snap grid: 48 (fine), 24, 16, 12, 8, or Off (guides only). Coarser grids line up with the fine one.
- **Learn:** select a control in edit mode and tap **Learn**, then move the parameter in your OSC software. The control takes the address of the first message received. Its value type comes from the message, and its min/max are rounded out to a conventional range (for example 0.42 → 0–1, int 64 → 0–127, −0.3 → −1…1). Keep moving the parameter through its full range to refine the range, then tap **Done**. Addresses that were already streaming during the 1.5 s before you tapped Learn (meters, clocks) are ignored.
- **Network discovery:** Settings → **Find…** lists OSC apps that announce themselves via Bonjour / Zeroconf (`_osc._udp`), with their IP and port. Tap one to fill in the host and send port. The app also announces itself (toggle in Settings), so other OSC software can find it.
- **Undo / redo:** in edit mode, every change (move, resize, add, delete, properties, Learn, page edits) can be undone and redone. History starts when you enter edit mode and holds up to 200 steps.
- **Appearance styles** (Settings → Appearance → Style):
  - Standard: rounded.
  - Minimal: sharp edges and flat fills, TouchOSC-like.
  - Retro terminal: monospace with a phosphor glow, segmented bars, LED rings, inverse video and scanlines.
  - Evangelion: framed panels with corner brackets and label tabs, condensed type, slatted bars, green readouts, hexagon radios, a hazard-striped header, and a honeycomb background with crosshairs.

  Retro terminal and Evangelion also switch to their matching colors.
- **Color palettes** (Settings → Appearance → Colors), all very dark:
  - Midnight
  - OLED Black
  - Deep Blue
  - Night Red: crimson to orange-red, preserves night vision
  - Amber
  - Phosphor
  - NERV

  The night palettes pull every control color toward their hue but keep some range, so different controls stay distinguishable.
- **Overlap-safe editing:** dragged or resized controls stop flush against their neighbours instead of overlapping them. Free spots past a neighbour are still reachable. Controls that already overlap can be pulled apart, and Add/Duplicate place new controls in the nearest free spot. Switch it off in the Grid menu ("Prevent overlap").
- **Layout names:** a layout is named after its file. Exporting as `My Show.json` renames the current layout to "My Show", and importing a file uses that file's name.
- **Page order:** in edit mode, long-press a page tab and drag it, or use Page… → Move left/right.
- **Colors:** quick swatches plus a full HSV color wheel with a brightness bar and hex input. Tap the rainbow swatch in Properties.
- **Number fields** (ports, min/max, segments) have ▲/▼ buttons. They step by 1 for integers and 0.1 for floats.
- **OSC monitor:** a color-coded table with time, direction, address, type tags and values. It stays pinned to the newest message. Scroll down to read and it holds still until you tap "↑ Newest". You can filter by In or Out, pause, and clear.
- **Rotation lock:** in Settings, choose Auto-rotate, Landscape, Portrait or Lock current. On tablets this relies on the Android 16 compatibility opt-out in the manifest (see below).
- **Pages:** tabs across the top. You can add, rename and delete pages.
- **Scales to any screen:** positions are stored as fractions of the page, so one layout works on both phone and tablet, in either orientation.
- **Import/export:** layouts are JSON files (Settings → Import/Export). They auto-save on the device.
- **TX/RX activity LEDs**, keep-screen-on, and fullscreen.

## Build & install

Toolchain: Gradle 9.8.1 (wrapper included), Android Gradle Plugin 9.4.1 with built-in Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37 and targetSdk 36. The app runs on Android 8.0 (API 26) and later.

1. In Android Studio, choose **File → Open** and select this folder. Gradle sync downloads everything it needs.
2. Plug in your phone or tablet with USB debugging enabled and press **Run ▶**.
   Alternatively, use **Build → Build APK(s)** and copy `app/build/outputs/apk/debug/app-debug.apk` to the device.

**For performance, use the release build on stage.** Compose runs much slower in debug builds. The release build is optimized with R8 and signed with the debug key, so it installs over the debug build without losing your layouts:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

From the command line, using Android Studio's bundled JDK:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug testDebugUnitTest
```

### Testing with the emulator

The emulator reaches your PC at `10.0.2.2`, so set that as the host. To let the PC send feedback into the emulator, forward the receive port with `adb emu redir add udp:9000:9000`. Then send to `127.0.0.1:9000` on the PC.

## Connecting

1. Put the device and the computer on the same network.
2. Open Settings (⚙) in the app:
   - **Host IP:** the computer's IP address (e.g. `192.168.1.20`). Use `x.x.x.255` to broadcast.
   - **Send port:** the port your software listens on (e.g. 8000).
   - **Receive port:** the port the app listens on for feedback (e.g. 9000).
3. In your software (Ableton via a plugin, Reaper, QLab, Resolume, TouchDesigner, Max/MSP, Pure Data and others), set its OSC output to the device IP shown in Settings, on the receive port.

The app sends from its receive port, so hosts that reply to the sender's address also work.

## What each control sends

| Control | Message |
|---|---|
| Button | `max` on press and `min` on release |
| Toggle | `max` when on and `min` when off |
| Fader / Knob | value scaled to `min…max` |
| XY pad | two args: `x y`, each scaled to `min…max` |
| Radio | selected index as int (`0…segments-1`) |
| Label | nothing (it displays incoming args) |
| Color wheel | the color, by default as an OSC color (type tag `r`, RGBA bytes, alpha FF), as Resolume and TouchOSC expect. Alternatives: 3 floats 0–1, 3 ints 0–255, or a hex string. Feedback accepts any of these, and Learn detects which one your software uses. |

Values are floats by default. Choose Int or Bool per control in Properties, or let Learn pick the type. A Bool control sends `T` (true) above the midpoint and `F` below.

## Layout file format

```json
{
  "format": "oscpad-layout",
  "version": 1,
  "name": "Demo",
  "pages": [
    {
      "name": "Mixer",
      "controls": [
        { "type": "fader", "name": "Ch 1", "address": "/1/fader1",
          "x": 0.02, "y": 0.03, "w": 0.1, "h": 0.72,
          "color": "#FFFF9F1C", "min": 0, "max": 1, "valueType": "float" }
      ]
    }
  ]
}
```

`x/y/w/h` are fractions of the page (0–1). `type` is one of `button`, `toggle`, `fader`, `knob`, `xy`, `radio` (with `steps`) or `label`. `valueType` is `float`, `int` or `bool`. Color wheels (`type: "color"`) also have `colorFormat`: `rgba`, `floats`, `ints` or `hex`. Older files that use `"int": true` still load.

## Tablets and Android 16

Apps that target API 36 normally have portrait/landscape requests ignored on large screens. The manifest sets `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` so the rotation lock also works on tablets. Google is removing that opt-out for apps that target API 37, so before raising `targetSdk` to 37, check how rotation lock behaves on tablets.

## Performance notes

- Control values are read only while drawing. A value change, from touch or incoming OSC, redraws only that control's own layer. Nothing recomposes or re-lays out.
- Incoming messages are queued on the network thread and handled in one batch per UI-thread turn. Floods beyond 4096 queued messages are dropped and counted in the monitor.
- The message history is a fixed ring buffer of 500 entries. Text is formatted only for rows the monitor actually shows, and only while it is open.
- There are no ripples or other decorative animations. When nothing changes, the app draws no frames (measured: 0 frames and about 4% CPU over 6 seconds idle on the emulator).

## Code map

- `osc/OscCodec.kt`: OSC 1.0 encode and decode (i f s b h d T F N c, nested bundles).
- `osc/OscConnection.kt`: UDP socket, send queue and receive loop.
- `model/Layout.kt`: layout model, JSON, grid sizes, and the demo layout.
- `model/Snapping.kt`: edit-mode snapping and guide lines.
- `model/Learn.kt`: value-type and range inference for Learn.
- `model/History.kt`: undo/redo stacks. `model/NumberStep.kt`: exact ▲/▼ stepping.
- `osc/Discovery.kt`: Bonjour browse, resolve and announce. `osc/MessageLog.kt`: the monitor's ring buffer.
- `ui/Theme.kt`: color palettes. `ui/Style.kt`: appearance styles (fonts, shapes).
- `ui/Skin.kt`: control drawing for Standard and Minimal, plus shared helpers. `ui/SkinTerminal.kt` and `ui/SkinNerv.kt`: the other two styles.
- `ui/ColorPicker.kt`: the color picker dialog. `ui/Monitor.kt`: the monitor table. `model/ColorArgs.kt`: color ⇄ OSC arguments.
- `MainViewModel.kt`: state, OSC routing, editing, persistence.
- `ui/Controls.kt`: control rendering and multitouch handling.
- `ui/PadScreen.kt`: page canvas, edit gestures, toolbars.
- `ui/Dialogs.kt`: settings, properties, monitor.
