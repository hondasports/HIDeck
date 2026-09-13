# Pixel 3 and Magisk procedure

This procedure is deliberately split into audit, patch, and flash. Pixel 3 is an A/B device and the `boot` partition is slot-specific, so a boot image from another LineageOS date can soft-brick the phone.

## 1. Audit first

The audit accepts a wireless ADB endpoint, so the phone stays controllable
while its USB port is later taken over by HIDeck.

```powershell
.\scripts\pixel3-device-audit.ps1 -Serial 192.168.10.121:5555
```

Keep the generated audit directory. It includes `getprop.txt`, the current slot, and fastboot variables. If the script reports `unauthorized`, unlock the phone and accept the USB debugging RSA prompt before retrying.

The image must match all of these values:

* `ro.product.device=blueline`
* installed LineageOS branch and build date
* current firmware requirements for that LineageOS branch
* active A/B slot (`_a` or `_b`)

Official LineageOS download pages publish `boot.img` alongside each signed build. Download the exact build shown by the audit, then verify its SHA-256. Do not use a recovery image from a different date.

## 2. Patch with Magisk

Install the official Magisk APK on the phone and copy the exact `boot.img` to `/sdcard/Download/`. In Magisk, choose **Install → Select and Patch a File**, select that file, and wait for the patched image. Pull the resulting `magisk_patched*.img` back to this computer:

```powershell
adb -s 192.168.10.121:5555 pull /sdcard/Download/magisk_patched*.img .\work\
```

Record the SHA-256 of both the original and patched images. Keep the unmodified original available for recovery.

## 3. Flash only after checking fastboot state

Reboot to bootloader, verify the serial and read the current slot again:

```powershell
adb -s 192.168.10.121:5555 reboot bootloader
fastboot devices
fastboot getvar product 2>&1
fastboot getvar current-slot 2>&1
fastboot getvar unlocked 2>&1
```

The product must be `blueline`, the bootloader must be unlocked, and the slot recorded in the audit must still be active. Then use the guarded helper:

```powershell
.\scripts\magisk-pixel3.ps1 `
  -Serial 192.168.10.121:5555 `
  -PatchedBoot .\work\magisk_patched.img `
  -OriginalBoot .\work\lineage-exact-boot.img `
  -ConfirmFlash
```

The helper refuses a wrong product, missing unlock state, mismatched slot, or a missing original image. After reboot, reconnect ADB over Wi-Fi (`adb connect 192.168.10.121:5555`), check `adb shell su -c id`, open Magisk, and run the HIDeck root check. If anything fails, boot the saved unmodified image into the matching slot through fastboot before experimenting further.
