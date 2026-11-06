# AirCons

AirCons turns the AirPods already connected to an Android phone into a game controller.
No dongle, no extra hardware, no account. The interface is light-only by design.

- **Headline** — wear both pods and aim with your head.
- **Sidearm** — hold one pod like a pistol; the stem is the trigger.
- **Wheel** — hold a pod in each hand and steer with the bank.

Motion becomes normal Android accessibility gestures (swipes and taps), so games see
ordinary touch input. A tiny optional HUD draws a reticle, steering bank, battery and
packet rate over the game.

## How it works

AirPods expose a private Apple Accessory Protocol (AAP) channel over Bluetooth L2CAP
PSM `4097`. AirCons opens that channel with the hidden `BluetoothSocket` constructor,
runs the AAP handshake, subscribes to notifications, requests head tracking, and parses
the 70-byte head-motion packets. Stem presses arrive as AAP notification `0x19`.

The motion is calibrated from a short neutral sample, smoothed, mapped into a
`ControlFrame`, and injected by an `AccessibilityService` through `dispatchGesture`.
A foreground service keeps the session alive while the game is in front.

## Dynamic source

The mapping layer is not baked into the APK. AirCons can fetch the latest
`plugins/manifest.json` and JavaScript profiles from this repository, cache them on the
device, and run them inside the control loop with Rhino. No reinstall is needed to tune
or replace the mapping. The same screen contains a local editor for trying a script
immediately.

A plugin defines:

```js
function map(input) {
  // input: mode, yaw, pitch, roll, accelH, accelV, trigger
  return { lookX: 0, lookY: 0, steer: 0, throttle: 0, fire: input.trigger };
}
```

## Build

The APK is built by GitHub Actions and published as a rolling release tagged `latest`.
Every push to `main` produces a release APK and a new version number. Builds use a
persistent signing key stored in GitHub Actions secrets, so in-app updates install over
the previous version instead of requiring an uninstall. The app checks the latest
GitHub release on launch and offers an in-app download/install.

Local build:

```bash
./gradlew assembleRelease
```

Requirements: JDK 17 and Android SDK 35. For a signed local release, export
`AIRCONS_STORE_FILE`, `AIRCONS_STORE_PASSWORD`, `AIRCONS_KEY_ALIAS` and
`AIRCONS_KEY_PASSWORD` first. Without them, `assembleDebug` falls back to the local
Android debug key.

## Permissions

- **Nearby devices / Bluetooth** — discover and open the AAP link.
- **Notifications** — foreground session notification.
- **Accessibility** — inject the configured gestures and draw the optional HUD.
- **Install unknown apps** — open downloaded updates (only when you tap install).
- **Internet** — pull scripts and updates from GitHub.

## Limitations

- AirPods must be paired in Android settings first.
- Head tracking works best on AirPods Pro, AirPods 3, and newer; older models may not
  emit motion packets.
- Stem customization changes media-key behavior during a session.
- Some games ignore synthetic swipes; tune anchors and injection rate in Settings.
- Apple firmware changes can alter AAP behavior. This is unofficial software.

## License and attribution

AirCons is licensed under **GPL-3.0-or-later**. Protocol research builds on the public
[LibrePods](https://github.com/librepods-org/librepods) project (GPLv3) and the wider
reverse-engineering work around Apple's Accessory Protocol. The hidden-API bridge uses
[AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) (Apache-2.0).
AirPods is a trademark of Apple Inc. AirCons is not affiliated with or endorsed by Apple.
