package com.hondasports.hideck

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var controller: HidDeckController
    private lateinit var status: TextView
    private lateinit var modeSpinner: Spinner
    private lateinit var textInput: EditText
    private lateinit var macroInput: EditText
    private val mainHandler = Handler(Looper.getMainLooper())
    private val statusTicker = object : Runnable {
        override fun run() {
            if (::controller.isInitialized) updateStatus()
            mainHandler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = HidDeckController(this)
        requestBluetoothPermissionsIfNeeded()
        setContentView(buildUi())
        mainHandler.post(statusTicker)
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(Color.rgb(16, 20, 24))
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "HIDeck"
            textSize = 30f
            setTextColor(Color.rgb(242, 245, 247))
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Androidをキーボード・マウス・ストレージへ切り替える"
            textSize = 14f
            setTextColor(Color.LTGRAY)
        })

        status = TextView(this).apply {
            text = "初期化中…"
            textSize = 14f
            setTextColor(Color.rgb(102, 217, 239))
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(status)

        modeSpinner = Spinner(this)
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf(
            "USB · HID + Mass Storage (root)",
            "Bluetooth · HID keyboard + mouse"
        ))
        root.addView(modeSpinner, matchWrap())
        root.addView(TextView(this).apply {
            text = "BluetoothはHIDeckを開いたまま、Windows側から接続・再ペアリングしてな。"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        })

        val connectionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        connectionRow.addView(button("接続") { connectSelected() }, weight(1f))
        connectionRow.addView(button("切断") { controller.disconnect { runOnUiThread { updateStatus() } } }, weight(1f))
        root.addView(connectionRow)

        root.addView(sectionLabel("Touchpad"))
        root.addView(TouchpadView(this, controller), LinearLayout.LayoutParams(-1, dp(220)).apply {
            topMargin = dp(4)
            bottomMargin = dp(6)
        })
        val mouseButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mouseButtons.addView(button("左クリック") { controller.mouse(MouseReportEncoder.LEFT); controller.mouse(0) }, weight(1f))
        mouseButtons.addView(button("右クリック") { controller.mouse(MouseReportEncoder.RIGHT); controller.mouse(0) }, weight(1f))
        mouseButtons.addView(button("ホイール↑") { controller.mouse(0, wheel = 1) }, weight(1f))
        mouseButtons.addView(button("ホイール↓") { controller.mouse(0, wheel = -1) }, weight(1f))
        root.addView(mouseButtons)

        root.addView(sectionLabel("IME入力"))
        textInput = EditText(this).apply {
            hint = "キーボードで入力して送信（ASCII）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(false)
            minLines = 2
        }
        root.addView(textInput, matchWrap())
        root.addView(button("入力内容を送信") { controller.typeText(textInput.text.toString()); textInput.text.clear() })

        root.addView(sectionLabel("特殊キー"))
        val specialRows = listOf(
            listOf("ESC", "TAB", "ENTER", "BACKSPACE", "DELETE"),
            listOf("LEFT", "RIGHT", "UP", "DOWN", "HOME", "END"),
            listOf("CTRL+C", "CTRL+V", "CTRL+X", "CTRL+ALT+T")
        )
        specialRows.forEach { labels ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            labels.forEach { label ->
                row.addView(button(label) { KeyboardReportEncoder.specialStroke(label)?.let(controller::tapKey) }, weight(1f))
            }
            root.addView(row)
        }

        root.addView(sectionLabel("IME切替"))
        root.addView(TextView(this).apply {
            text = "WindowsはAlt+`、MacはCtrl+Spaceを送るで。ホスト側でショートカットを変更している場合は設定に合わせてな。"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        })
        val imeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        imeRow.addView(button("Windows IME") {
            KeyboardReportEncoder.specialStroke("Windows IME")?.let(controller::tapKey)
        }, weight(1f))
        imeRow.addView(button("Mac IME") {
            KeyboardReportEncoder.specialStroke("Mac IME")?.let(controller::tapKey)
        }, weight(1f))
        root.addView(imeRow)

        root.addView(sectionLabel("マクロ"))
        macroInput = EditText(this).apply {
            hint = "よく使う文字列を保存"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(getPreferences(MODE_PRIVATE).getString("macro", ""))
        }
        root.addView(macroInput, matchWrap())
        val macroRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        macroRow.addView(button("保存") {
            getPreferences(MODE_PRIVATE).edit().putString("macro", macroInput.text.toString()).apply()
            toast("マクロを保存したで")
        }, weight(1f))
        macroRow.addView(button("送信") { controller.typeText(macroInput.text.toString()) }, weight(1f))
        root.addView(macroRow)

        root.addView(sectionLabel("ストレージ"))
        root.addView(TextView(this).apply {
            text = "USB接続中は専用 hideck-storage.img をPCへ公開。Android側で同時マウントせんよう排他制御する。初回はPC側でフォーマットしてな。"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        })
        root.addView(button("USBストレージの状態を確認") { toast("USBモード接続時だけPCへ公開されるで") })
        return scroll
    }

    private fun connectSelected() {
        val selected = if (modeSpinner.selectedItemPosition == 0) HidDeckController.Mode.USB else HidDeckController.Mode.BLUETOOTH
        controller.connect(selected) { result ->
            runOnUiThread {
                if (result.isFailure) {
                    val message = result.exceptionOrNull()?.message ?: "接続に失敗したで"
                    Log.e("HIDeck", "connect failed: $message")
                    toast(message)
                    status.text = "接続エラー · $message"
                } else updateStatus()
            }
        }
    }

    private fun updateStatus() {
        val mode = controller.currentMode()
        val ready = controller.isReady()
        val bt = if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } else false
        status.text = "${mode.name} · ${if (ready) "接続済み" else "未接続"} · Bluetooth=${if (bt) "ON" else "OFF"}"
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            val missing = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 42)
        }
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        setTextColor(Color.rgb(102, 217, 239))
        setPadding(0, dp(18), 0, dp(5))
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
        isAllCaps = false
        minHeight = dp(44)
        setTextColor(Color.WHITE)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(4)
    }

    private fun weight(value: Float) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, value).apply {
        marginEnd = dp(3)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        mainHandler.removeCallbacks(statusTicker)
        controller.close()
        super.onDestroy()
    }
}
