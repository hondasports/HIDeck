# HIDeck

HIDeck turns a rooted Android phone into a small, switchable input deck. The current target is a Pixel 3 (`blueline`) running LineageOS.

The app has two independent transports:

* **USB**: Linux ConfigFS composite gadget with a boot keyboard, a relative mouse, and a dedicated 128 MiB mass-storage image.
* **Bluetooth**: Android's public `BluetoothHidDevice` profile with keyboard and mouse report IDs.

The UI provides an IME-backed text field, touchpad gestures, mouse buttons and wheel, special keys, and a saved macro. USB gadget setup is root-only and is deliberately opt-in because taking over the Android USB gadget also takes ADB off the cable until the gadget is released.

## Build

The repository contains a Gradle wrapper. With Android SDK Platform 36 and Build Tools 36 installed:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

When the phone and this PC are on the same Wi-Fi, keep development ADB off the
USB cable so USB gadget testing does not take away the debug channel:

```powershell
adb tcpip 5555
adb connect <phone-ip>:5555
adb disconnect <usb-serial>
```

## Install and first run

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For Bluetooth mode, open HIDeck first and grant the Bluetooth permissions it requests. Remove any old phone pairing on the computer, then pair the computer with the phone while HIDeck is registered. Windows normally initiates the HID connection from its Bluetooth settings; the app keeps the HID Device profile registered and accepts the bonded computer. USB mode requires a working `su` provider (Magisk) and a kernel exposing `/config/usb_gadget` and a UDC.

USB mode exports the root-owned `/data/adb/hideck-storage.img`. The image is intentionally separate from Android's storage. The first time it appears on the host, format it there. Do not mount or modify it from Android while USB mode is active; HIDeck holds an exclusive lock for the duration of the export.

## Pixel 3 / LineageOS setup

Run the read-only audit before changing boot partitions:

```powershell
.\scripts\pixel3-device-audit.ps1 -Serial 192.168.10.121:5555
```

The script records the codename, LineageOS build, active slot, verified-boot state, bootloader variables, and a copy of `getprop`. It refuses to continue unless ADB is authorized and the device identifies as `blueline`.

The Magisk workflow is documented in [docs/pixel3-magisk.md](docs/pixel3-magisk.md) and is implemented by `scripts/magisk-pixel3.ps1`. It requires a boot image that matches the exact installed LineageOS build. The script never chooses a random image and never flashes without `-ConfirmFlash`.

## Limitations

* USB HID and ConfigFS support depends on the LineageOS kernel. A device can be rooted and still lack `hidg` or a usable UDC.
* Boot keyboard reports cover the ASCII/US layout and common control keys. Unicode text needs a future Unicode input strategy.
* Android's Bluetooth HID Device profile is asynchronous. Windows is the HID host and may require removing an old pairing and pairing again after HIDeck has registered its SDP record. A failed host-initiated attempt can leave the device paired but disconnected; tap **切断**, close/reopen HIDeck, and retry from the computer's Bluetooth settings.
* Replacing the Android USB gadget disables ADB for the duration of USB mode. Tap **切断** to restore the prior `sys.usb.config`; if the app is force-stopped while active, unplug/replug or reboot to restore Android's normal gadget.

## License

Apache-2.0. See [LICENSE](LICENSE).
