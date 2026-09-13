package com.hondasports.hideck

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HidDeckController(context: Context) {
    enum class Mode { USB, BLUETOOTH }

    private val usb = UsbHidTransport(context)
    private val bluetooth = BluetoothHidTransport(context)
    private val work: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hideck-report-queue").apply { isDaemon = true }
    }
    @Volatile private var mode = Mode.USB
    @Volatile private var transport: HidTransport = usb

    fun currentMode(): Mode = mode
    fun transportName(): String = transport.name
    fun isReady(): Boolean = transport.isReady()

    fun connect(selected: Mode, callback: (Result<Unit>) -> Unit) {
        work.execute {
            // Tear down the previous transport even while it is still registering.
            // Bluetooth HID registration is a system-wide singleton on Android.
            transport.disconnect()
            mode = selected
            transport = if (selected == Mode.USB) usb else bluetooth
            val result = transport.connect()
            callback(result)
        }
    }

    fun disconnect(callback: (() -> Unit)? = null) {
        work.execute {
            transport.disconnect()
            callback?.invoke()
        }
    }

    fun typeText(value: String) {
        val snapshot = value.toList()
        work.execute {
            snapshot.forEach { char ->
                KeyboardReportEncoder.asciiStroke(char)?.let { stroke ->
                    transport.sendKeyboard(KeyboardReportEncoder.report(stroke))
                    Thread.sleep(8)
                    transport.sendKeyboard(KeyboardReportEncoder.release())
                    Thread.sleep(8)
                }
            }
        }
    }

    fun tapKey(stroke: KeyboardReportEncoder.KeyStroke) {
        work.execute {
            transport.sendKeyboard(KeyboardReportEncoder.report(stroke))
            Thread.sleep(12)
            transport.sendKeyboard(KeyboardReportEncoder.release())
        }
    }

    fun mouse(buttons: Int, dx: Int = 0, dy: Int = 0, wheel: Int = 0) {
        transport.sendMouse(MouseReportEncoder.report(buttons, dx, dy, wheel))
    }

    fun close() {
        transport.disconnect()
        usb.close()
        work.shutdownNow()
    }
}
