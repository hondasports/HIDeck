package com.hondasports.hideck

/** Common wire-level contract for USB gadget and Bluetooth HID transports. */
interface HidTransport {
    val name: String
    fun isReady(): Boolean
    fun connect(): Result<Unit>
    fun disconnect()
    fun sendKeyboard(report: ByteArray): Result<Unit>
    fun sendMouse(report: ByteArray): Result<Unit>
}
