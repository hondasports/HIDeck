package com.hondasports.hideck

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UsbHidTransport(context: Context) : HidTransport {
    override val name: String = "USB"
    private val gadget = UsbGadgetController(context)
    private val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hideck-usb-io").apply { isDaemon = true }
    }
    private val mouseQueueLock = Any()
    private val pendingMouseReports = ArrayDeque<ByteArray>()
    private var mouseWorkerScheduled = false

    override fun isReady(): Boolean = gadget.isEnabled()

    override fun connect(): Result<Unit> = gadget.enable()

    override fun disconnect() {
        gadget.disable()
        synchronized(mouseQueueLock) {
            pendingMouseReports.clear()
        }
    }

    override fun sendKeyboard(report: ByteArray): Result<Unit> = send(gadget.keyboardPath(), report)

    override fun sendMouse(report: ByteArray): Result<Unit> {
        if (!gadget.isEnabled()) return Result.failure(IllegalStateException("USB gadget is not enabled"))
        var schedulingError: RuntimeException? = null
        synchronized(mouseQueueLock) {
            val copy = report.copyOf()
            val tail = pendingMouseReports.peekLast()
            if (tail != null && isMovement(tail) && isMovement(copy)) {
                mergeMovement(tail, copy)
            } else {
                pendingMouseReports.addLast(copy)
            }
            if (!mouseWorkerScheduled) {
                mouseWorkerScheduled = true
                try {
                    io.execute(::drainMouseReports)
                } catch (e: RuntimeException) {
                    mouseWorkerScheduled = false
                    pendingMouseReports.clear()
                    schedulingError = e
                }
            }
        }
        return schedulingError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private fun send(path: String, report: ByteArray): Result<Unit> {
        if (!gadget.isEnabled()) return Result.failure(IllegalStateException("USB gadget is not enabled"))
        return try {
            io.execute {
                // A disconnect can race with a queued report. Do not write
                // after the gadget has been torn down.
                if (!gadget.isEnabled()) return@execute
                writeReport(path, report)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun drainMouseReports() {
        while (true) {
            val report = synchronized(mouseQueueLock) {
                if (pendingMouseReports.isEmpty()) {
                    mouseWorkerScheduled = false
                    null
                } else {
                    pendingMouseReports.removeFirst()
                }
            } ?: return

            if (!gadget.isEnabled()) {
                synchronized(mouseQueueLock) {
                    pendingMouseReports.clear()
                    mouseWorkerScheduled = false
                }
                return
            }
            writeReport(gadget.mousePath(), report)
        }
    }

    private fun writeReport(path: String, report: ByteArray) {
        try {
            // A stale regular file can be left behind if a previous bind
            // failed. Only attempt the direct write for an existing device
            // node; RootShell has a character-device guard for its fallback.
            val endpoint = File(path)
            if (endpoint.exists() && !endpoint.isFile) {
                FileOutputStream(path).use { it.write(report) }
                return
            }
            throw IllegalStateException("HID endpoint is not a character device")
        } catch (direct: Exception) {
            val root = RootShell.writeBytes(path, report)
            if (!root.isSuccess) {
                Log.w(TAG, "USB report write failed path=$path direct=${direct.message} root=${root.stderr}")
            }
        }
    }

    private fun isMovement(report: ByteArray): Boolean {
        return report.size >= 4 &&
            report[0].toInt() == 0 &&
            report[3].toInt() == 0 &&
            (report[1].toInt() != 0 || report[2].toInt() != 0)
    }

    private fun mergeMovement(target: ByteArray, next: ByteArray) {
        target[1] = (target[1].toInt() + next[1].toInt()).coerceIn(-127, 127).toByte()
        target[2] = (target[2].toInt() + next[2].toInt()).coerceIn(-127, 127).toByte()
    }

    fun close() {
        synchronized(mouseQueueLock) {
            pendingMouseReports.clear()
        }
        io.shutdownNow()
    }

    companion object {
        private const val TAG = "HIDeck"
    }
}
