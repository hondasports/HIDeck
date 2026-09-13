# Verification checklist

## Host-side checks

* `assembleDebug` succeeds with the pinned Gradle/Kotlin/AGP versions.
* APK installs on an API 28+ arm64 device.
* USB mode reports a keyboard and mouse HID interface and a separate mass-storage disk.
* Bluetooth mode registers the HID Device profile; after the host refreshes pairing, the host reports keyboard and mouse input.

For a phone whose USB port is being used as the gadget, use wireless ADB during
the test (`adb tcpip 5555`, `adb connect <phone-ip>:5555`, then disconnect the
USB serial). This keeps the control channel available while ConfigFS owns the
physical USB connection.

## On-device checks

* `su -c id` returns uid 0.
* `/config/usb_gadget` and `/sys/class/udc` exist.
* `/dev/hidg0` and `/dev/hidg1` become writable only while USB mode is active.
* Disconnect restores the previous `sys.usb.config` and ADB becomes available again.
* The app refuses USB mode if no root or no UDC is present.
* The app does not expose the image while an Android-side storage lock is held.

## Manual input checks

1. Send `Hello, HIDeck!` from the IME field.
2. Tap each special-key row and confirm the host action.
3. Tap **Windows IME** on a Japanese Windows host and confirm Alt+` toggles Japanese input.
4. Tap **Mac IME** on macOS with multiple input sources and confirm Control-Space selects the previous source.
5. Drag and tap the touchpad; test both mouse buttons and the wheel.
6. Save a macro, disconnect, relaunch, and send it again.
