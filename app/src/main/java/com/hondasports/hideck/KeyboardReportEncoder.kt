package com.hondasports.hideck

/** USB boot keyboard report: modifier, reserved, and six simultaneous usages. */
object KeyboardReportEncoder {
    const val REPORT_SIZE = 8

    /** Standard boot keyboard descriptor, kept separate so ConfigFS can consume it. */
    val USB_REPORT_DESCRIPTOR = descriptor(
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01,
        0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00,
        0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81.toByte(), 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
        // Include LANG5 (0x94), used by the Japanese Zenkaku/Hankaku key.
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x94.toByte(), 0x05, 0x07,
        0x19, 0x00, 0x29, 0x94.toByte(), 0x81.toByte(), 0x00, 0xC0.toByte()
    )

    private fun descriptor(vararg values: Any): ByteArray = values.map {
        when (it) {
            is Byte -> it
            is Int -> it.toByte()
            else -> 0
        }
    }.toByteArray()

    data class KeyStroke(val usage: Int, val modifier: Int = 0)

    fun report(stroke: KeyStroke): ByteArray = byteArrayOf(
        stroke.modifier.toByte(),
        0,
        stroke.usage.toByte(),
        0, 0, 0, 0, 0
    )

    fun release(): ByteArray = ByteArray(REPORT_SIZE)

    fun asciiStroke(value: Char): KeyStroke? {
        if (value in 'a'..'z') return KeyStroke(0x04 + (value.code - 'a'.code))
        if (value in 'A'..'Z') return KeyStroke(0x04 + (value.code - 'A'.code), MOD_LEFT_SHIFT)
        if (value in '1'..'9') return KeyStroke(0x1E + (value.code - '1'.code))
        if (value == '0') return KeyStroke(0x27)
        return when (value) {
            '\n', '\r' -> KeyStroke(KEY_ENTER)
            '\t' -> KeyStroke(KEY_TAB)
            ' ' -> KeyStroke(KEY_SPACE)
            '-' -> KeyStroke(0x2D)
            '_' -> KeyStroke(0x2D, MOD_LEFT_SHIFT)
            '=' -> KeyStroke(0x2E)
            '+' -> KeyStroke(0x2E, MOD_LEFT_SHIFT)
            '[' -> KeyStroke(0x2F)
            '{' -> KeyStroke(0x2F, MOD_LEFT_SHIFT)
            ']' -> KeyStroke(0x30)
            '}' -> KeyStroke(0x30, MOD_LEFT_SHIFT)
            '\\' -> KeyStroke(0x31)
            '|' -> KeyStroke(0x31, MOD_LEFT_SHIFT)
            ';' -> KeyStroke(0x33)
            ':' -> KeyStroke(0x33, MOD_LEFT_SHIFT)
            '\'' -> KeyStroke(0x34)
            '"' -> KeyStroke(0x34, MOD_LEFT_SHIFT)
            '`' -> KeyStroke(0x35)
            '~' -> KeyStroke(0x35, MOD_LEFT_SHIFT)
            ',' -> KeyStroke(0x36)
            '<' -> KeyStroke(0x36, MOD_LEFT_SHIFT)
            '.' -> KeyStroke(0x37)
            '>' -> KeyStroke(0x37, MOD_LEFT_SHIFT)
            '/' -> KeyStroke(0x38)
            '?' -> KeyStroke(0x38, MOD_LEFT_SHIFT)
            '!' -> KeyStroke(0x1E, MOD_LEFT_SHIFT)
            '@' -> KeyStroke(0x1F, MOD_LEFT_SHIFT)
            '#' -> KeyStroke(0x20, MOD_LEFT_SHIFT)
            '$' -> KeyStroke(0x21, MOD_LEFT_SHIFT)
            '%' -> KeyStroke(0x22, MOD_LEFT_SHIFT)
            '^' -> KeyStroke(0x23, MOD_LEFT_SHIFT)
            '&' -> KeyStroke(0x24, MOD_LEFT_SHIFT)
            '*' -> KeyStroke(0x25, MOD_LEFT_SHIFT)
            '(' -> KeyStroke(0x26, MOD_LEFT_SHIFT)
            ')' -> KeyStroke(0x27, MOD_LEFT_SHIFT)
            else -> null
        }
    }

    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04
    const val MOD_LEFT_GUI = 0x08
    const val MOD_RIGHT_CTRL = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT = 0x40
    const val MOD_RIGHT_GUI = 0x80

    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_ARROW_RIGHT = 0x4F
    const val KEY_ARROW_LEFT = 0x50
    const val KEY_ARROW_DOWN = 0x51
    const val KEY_ARROW_UP = 0x52
    const val KEY_DELETE = 0x4C
    const val KEY_HOME = 0x4A
    const val KEY_END = 0x4D
    const val KEY_PAGE_UP = 0x4B
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_BACKQUOTE = 0x35

    /** USB HID LANG5: the Japanese Zenkaku/Hankaku (半角/全角) key. */
    const val KEY_ZENKAKU_HANKAKU = 0x94

    fun specialStroke(label: String): KeyStroke? = when (label.uppercase()) {
        "ENTER" -> KeyStroke(KEY_ENTER)
        "ESC" -> KeyStroke(KEY_ESC)
        "BACKSPACE" -> KeyStroke(KEY_BACKSPACE)
        "TAB" -> KeyStroke(KEY_TAB)
        "SPACE" -> KeyStroke(KEY_SPACE)
        "LEFT" -> KeyStroke(KEY_ARROW_LEFT)
        "RIGHT" -> KeyStroke(KEY_ARROW_RIGHT)
        "UP" -> KeyStroke(KEY_ARROW_UP)
        "DOWN" -> KeyStroke(KEY_ARROW_DOWN)
        "DELETE" -> KeyStroke(KEY_DELETE)
        "HOME" -> KeyStroke(KEY_HOME)
        "END" -> KeyStroke(KEY_END)
        "PGUP" -> KeyStroke(KEY_PAGE_UP)
        "PGDN" -> KeyStroke(KEY_PAGE_DOWN)
        "CTRL+C" -> KeyStroke(0x06, MOD_LEFT_CTRL)
        "CTRL+V" -> KeyStroke(0x19, MOD_LEFT_CTRL)
        "CTRL+X" -> KeyStroke(0x1B, MOD_LEFT_CTRL)
        "CTRL+ALT+T" -> KeyStroke(0x17, MOD_LEFT_CTRL or MOD_LEFT_ALT)
        // Windows' 101/102-key layout (which hosts commonly assign to a
        // generic HID keyboard) uses Alt+Backquote to toggle Japanese IME.
        "WINDOWS IME", "WIN IME" -> KeyStroke(KEY_BACKQUOTE, MOD_LEFT_ALT)
        // Keep the dedicated Japanese hardware key available for hosts that
        // expose it directly instead of using the 101/102-key shortcut.
        "WINDOWS JP IME", "WIN JP IME", "半角/全角" -> KeyStroke(KEY_ZENKAKU_HANKAKU)
        // macOS uses Control-Space to cycle between input sources by default.
        "MAC IME", "MAC INPUT", "MAC INPUT SOURCE" -> KeyStroke(KEY_SPACE, MOD_LEFT_CTRL)
        else -> null
    }
}
