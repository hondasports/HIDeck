# HIDeck architecture

```text
MainActivity
   |
HidDeckController -------- KeyboardReportEncoder / MouseReportEncoder
   |                                  |
HidTransport                         report bytes
   |------------------|
UsbHidTransport   BluetoothHidTransport
   |                  |
UsbGadgetController  BluetoothHidDevice
   |
ConfigFS: hid.usb0 + hid.usb1 + mass_storage.0
   |
StorageCoordinator (/data/adb image + app lock file)
```

`HidTransport` is intentionally small. The controller serializes keyboard text and key presses on a single queue, while mouse reports can be sent immediately so the touchpad remains responsive.

The USB transport creates a separate ConfigFS gadget named `hideck`. It sets a keyboard report descriptor on `hid.usb0`, a mouse descriptor on `hid.usb1`, and points `mass_storage.0/lun.0/file` at the root-owned `/data/adb/hideck-storage.img`. It saves the previous `sys.usb.config`, detaches the Android gadget while active, and restores the saved value when disconnected. A lock file in the app's private directory serializes image access; the lock is held until the gadget is torn down.

`StorageCoordinator` owns an advisory lock beside the image. Any future Android-side file browser or mount feature must acquire that lock before touching the image. The host side is the only writer while the mass-storage function is linked.

Bluetooth uses report ID 1 for the keyboard and report ID 2 for the mouse. HIDeck registers the public `BluetoothHidDevice` profile as a combined keyboard/mouse SDP record. The host must be paired in system settings; on Windows the host normally initiates the HID connection after the pairing is refreshed while HIDeck is open.
