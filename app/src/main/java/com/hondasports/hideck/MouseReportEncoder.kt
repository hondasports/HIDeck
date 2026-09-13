package com.hondasports.hideck

/** Boot mouse report: buttons, signed relative X/Y, and signed wheel. */
object MouseReportEncoder {
    /** Three-button relative mouse with a wheel. */
    val USB_REPORT_DESCRIPTOR = descriptor(
        0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01,
        0x09, 0x01, 0xA1.toByte(), 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
        0x95, 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
        0x95, 0x01, 0x75, 0x05, 0x81.toByte(), 0x01,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
        0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81.toByte(), 0x06,
        0xC0.toByte(), 0xC0.toByte()
    )

    private fun descriptor(vararg values: Any): ByteArray = values.map {
        when (it) {
            is Byte -> it
            is Int -> it.toByte()
            else -> 0
        }
    }.toByteArray()

    fun report(buttons: Int, dx: Int, dy: Int, wheel: Int = 0): ByteArray = byteArrayOf(
        buttons.coerceIn(0, 7).toByte(),
        dx.coerceIn(-127, 127).toByte(),
        dy.coerceIn(-127, 127).toByte(),
        wheel.coerceIn(-127, 127).toByte()
    )

    const val LEFT = 1
    const val RIGHT = 2
    const val MIDDLE = 4
}
