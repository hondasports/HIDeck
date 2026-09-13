package com.hondasports.hideck

import android.content.Context
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UsbHidTransport(context: Context) : HidTransport {
    override val name: String = "USB"
    private val gadget = UsbGadgetController(context)
    private val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hideck-usb-io").apply { isDaemon = true }
    }

    override fun isReady(): Boolean = gadget.isEnabled()

    override fun connect(): Result<Unit> = gadget.enable()

    override fun disconnect() {
        gadget.disable()
    }

    override fun sendKeyboard(report: ByteArray): Result<Unit> = send(gadget.keyboardPath(), report)

    override fun sendMouse(report: ByteArray): Result<Unit> = send(gadget.mousePath(), report)

    private fun send(path: String, report: ByteArray): Result<Unit> {
        if (!gadget.isEnabled()) return Result.failure(IllegalStateException("USB gadget is not enabled"))
        return try {
            io.execute {
                try {
                    FileOutputStream(path).use { it.write(report) }
                } catch (_: Exception) {
                    RootShell.writeBytes(path, report)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        io.shutdownNow()
    }
}
