package com.hondasports.hideck

import android.content.Context
import android.util.Log
import java.io.File

/** ConfigFS USB composite gadget (keyboard + mouse + dedicated mass-storage image). */
class UsbGadgetController(context: Context) {
    private val prefs = context.getSharedPreferences("usb-gadget", Context.MODE_PRIVATE)
    private val storage = StorageCoordinator(context)
    private var enabled = false
    private var imageFile: File? = null

    fun isEnabled(): Boolean = enabled

    fun enable(): Result<Unit> {
        if (enabled) return Result.success(Unit)
        if (!RootShell.hasRoot()) return Result.failure(IllegalStateException("root/Magisk is required"))
        val image = try {
            storage.ensureImage()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val exportLock = storage.acquireExport()
        if (exportLock.isFailure) return exportLock
        val currentConfig = RootShell.exec("getprop sys.usb.config").stdout.trim()
        val persistentConfig = RootShell.exec("getprop persist.sys.usb.config").stdout.trim()
        val udc = RootShell.exec("ls /sys/class/udc 2>/dev/null | head -n 1").stdout.trim()
        if (udc.isBlank()) {
            storage.releaseExport()
            return Result.failure(IllegalStateException("USB UDC is unavailable on this kernel"))
        }

        val previousConfig = when {
            currentConfig.isBlank() || currentConfig == "none" -> persistentConfig.ifBlank { "adb" }
            else -> currentConfig
        }
        prefs.edit().putString("previous_usb_config", previousConfig).apply()
        val commands = listOf(
            "setprop sys.usb.config none",
            "sleep 1",
            "G=/config/usb_gadget/hideck; mkdir -p \$G/strings/0x409 \$G/configs/c.1/strings/0x409 \$G/functions",
            // The stock Lineage gadget (usually g1) owns the UDC even after
            // sys.usb.config is set to none. Detach every other gadget before
            // binding HIDeck so the kernel accepts the new UDC assignment.
            "for U in /config/usb_gadget/*/UDC; do [ \"\$U\" = \"\$G/UDC\" ] || echo \"\" > \"\$U\" 2>/dev/null || true; done",
            "echo 0x1209 > \$G/idVendor",
            "echo 0xD001 > \$G/idProduct",
            "echo HIDeck > \$G/strings/0x409/serialnumber",
            "echo HIDeck > \$G/strings/0x409/manufacturer",
            "echo HIDeck USB HID + storage > \$G/strings/0x409/product",
            "mkdir -p \$G/functions/hid.usb0 \$G/functions/hid.usb1 \$G/functions/mass_storage.0/lun.0",
            "echo 1 > \$G/functions/hid.usb0/protocol; echo 1 > \$G/functions/hid.usb0/subclass; echo 8 > \$G/functions/hid.usb0/report_length",
            "echo 2 > \$G/functions/hid.usb1/protocol; echo 1 > \$G/functions/hid.usb1/subclass; echo 4 > \$G/functions/hid.usb1/report_length",
            "echo ${RootShell.quote(image.absolutePath)} > \$G/functions/mass_storage.0/lun.0/file",
            "echo 0 > \$G/functions/mass_storage.0/lun.0/ro",
            "echo 0 > \$G/functions/mass_storage.0/lun.0/removable",
            // ConfigFS requires report_desc to be written before a function
            // is linked into a configuration; links and UDC binding follow
            // after both descriptors have been uploaded.
            "echo \$UDC > \$G/UDC"
        )
        // Descriptor writes are binary; keep the shell setup separate from them.
        val setup = commands.dropLast(1).joinToString("; ")
        val setupResult = RootShell.exec("UDC=$udc; $setup")
        Log.d("HIDeck", "USB setup exit=${setupResult.exitCode} stdout=${setupResult.stdout} stderr=${setupResult.stderr}")
        if (!setupResult.isSuccess) {
            disable()
            return Result.failure(RuntimeException(setupResult.stderr.ifBlank { setupResult.stdout }))
        }
        val descriptorResult = RootShell.writeBytes("$GADGET/functions/hid.usb0/report_desc", KeyboardReportEncoder.USB_REPORT_DESCRIPTOR)
        Log.d("HIDeck", "USB keyboard descriptor exit=${descriptorResult.exitCode} stderr=${descriptorResult.stderr}")
        if (!descriptorResult.isSuccess) {
            disable()
            return Result.failure(RuntimeException(descriptorResult.stderr))
        }
        val mouseDescriptor = RootShell.writeBytes("$GADGET/functions/hid.usb1/report_desc", MouseReportEncoder.USB_REPORT_DESCRIPTOR)
        Log.d("HIDeck", "USB mouse descriptor exit=${mouseDescriptor.exitCode} stderr=${mouseDescriptor.stderr}")
        if (!mouseDescriptor.isSuccess) {
            disable()
            return Result.failure(RuntimeException(mouseDescriptor.stderr))
        }
        // ConfigFS rejects toybox ln -f because it tries to unlink the
        // destination first. The destination is new on every enable.
        val linkResult = RootShell.exec(
            "G=$GADGET; ln -s \$G/functions/hid.usb0 \$G/configs/c.1/; " +
                "ln -s \$G/functions/hid.usb1 \$G/configs/c.1/; " +
                "ln -s \$G/functions/mass_storage.0 \$G/configs/c.1/"
        )
        Log.d("HIDeck", "USB function links exit=${linkResult.exitCode} stdout=${linkResult.stdout} stderr=${linkResult.stderr}")
        if (!linkResult.isSuccess) {
            disable()
            return Result.failure(RuntimeException(linkResult.stderr.ifBlank { linkResult.stdout }))
        }
        // The vendor USB HAL can re-bind its stock gadget while descriptors
        // are being uploaded. Detach competing gadgets immediately before
        // the bind and retry briefly to cover that race.
        val detachCommand =
            "G=$GADGET; for U in /config/usb_gadget/*/UDC; do [ \"\$U\" = \"\$G/UDC\" ] || echo \"\" > \"\$U\" 2>/dev/null || true; done"
        var udcResult = RootShell.exec("false")
        for (attempt in 0 until 5) {
            RootShell.exec(detachCommand)
            udcResult = RootShell.exec("echo $udc > $GADGET/UDC")
            if (udcResult.isSuccess) break
            Thread.sleep(150)
        }
        Log.d("HIDeck", "USB bind exit=${udcResult.exitCode} stdout=${udcResult.stdout} stderr=${udcResult.stderr}")
        if (!udcResult.isSuccess) {
            disable()
            return Result.failure(RuntimeException(udcResult.stderr))
        }
        enabled = true
        imageFile = image
        return Result.success(Unit)
    }

    fun disable(): Result<Unit> {
        if (!RootShell.hasRoot()) return Result.failure(IllegalStateException("root/Magisk is required"))
        val previous = prefs.getString("previous_usb_config", "adb") ?: "adb"
        val cleanup = listOf(
            "G=$GADGET",
            "echo \"\" > \$G/UDC 2>/dev/null || true",
            // Unlink ConfigFS function links before removing the functions.
            "rm -f \$G/configs/c.1/hid.usb0 \$G/configs/c.1/hid.usb1 \$G/configs/c.1/mass_storage.0 2>/dev/null || true",
            // The mass-storage function owns a file attribute that must be
            // cleared before its LUN directory can be removed.
            "echo \"\" > \$G/functions/mass_storage.0/lun.0/file 2>/dev/null || true",
            "rmdir \$G/functions/mass_storage.0/lun.0 2>/dev/null || true",
            "rmdir \$G/functions/mass_storage.0 2>/dev/null || true",
            "rmdir \$G/functions/hid.usb0 \$G/functions/hid.usb1 2>/dev/null || true",
            "rmdir \$G/configs/c.1/strings/0x409 \$G/configs/c.1/strings \$G/configs/c.1 2>/dev/null || true",
            "rmdir \$G/strings/0x409 \$G/strings 2>/dev/null || true",
            "rmdir \$G/functions \$G/configs \$G/os_desc \$G 2>/dev/null || true",
            "setprop sys.usb.config ${RootShell.quote(previous)}"
        ).joinToString("; ")
        val result = RootShell.exec(cleanup)
        Log.d("HIDeck", "USB cleanup exit=${result.exitCode} stdout=${result.stdout} stderr=${result.stderr}")
        enabled = false
        imageFile = null
        storage.releaseExport()
        return if (result.isSuccess) Result.success(Unit) else Result.failure(RuntimeException(result.stderr))
    }

    /**
     * The ConfigFS `dev` attribute only reports the major/minor pair; it is
     * not a writable report endpoint. The HID function creates the matching
     * character devices under /dev, which are the files that accept reports.
     */
    fun keyboardPath(): String = "/dev/hidg0"
    fun mousePath(): String = "/dev/hidg1"

    companion object {
        private const val GADGET = "/config/usb_gadget/hideck"
    }
}
