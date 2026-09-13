package com.hondasports.hideck

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.concurrent.Executor

/** Bluetooth HID Device transport. Android 9+ exposes this profile to apps. */
class BluetoothHidTransport(private val context: Context) : HidTransport {
    private val stateLock = Any()
    override val name: String = "Bluetooth"
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var connected = false
    private var appRegistered = false
    private var registrationPending = false
    private var connectionPending = false

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            var connectAfterRegistration = false
            synchronized(stateLock) {
                appRegistered = registered
                registrationPending = false
                if (registered) {
                    host = pluggedDevice ?: host
                    connectAfterRegistration = connectionPending
                } else {
                    connected = false
                }
            }
            Log.i(TAG, "Bluetooth HID app registered=$registered host=${pluggedDevice?.address ?: "none"}")
            if (connectAfterRegistration) requestHostConnection()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            synchronized(stateLock) {
                if (device != null && (host == null || host?.address == device.address)) {
                    host = device
                    connected = state == BluetoothProfile.STATE_CONNECTED
                    if (connected) connectionPending = false
                }
            }
            Log.i(TAG, "Bluetooth HID connection state=$state device=${device?.address ?: "none"}")
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                var shouldRegister = false
                var connectAfterRegistration = false
                synchronized(stateLock) {
                    hid = proxy as? BluetoothHidDevice
                    if (hid != null && !appRegistered && !registrationPending) {
                        registrationPending = true
                        shouldRegister = true
                    } else {
                        connectAfterRegistration = hid != null && appRegistered && connectionPending
                    }
                }
                Log.i(TAG, "Bluetooth HID profile connected proxy=${hid != null}")
                if (shouldRegister) registerApp()
                if (connectAfterRegistration) requestHostConnection()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                synchronized(stateLock) {
                    hid = null
                    connected = false
                    appRegistered = false
                    registrationPending = false
                }
                Log.i(TAG, "Bluetooth HID profile disconnected")
            }
        }
    }

    override fun isReady(): Boolean = synchronized(stateLock) {
        connected && appRegistered && hid != null && host != null
    }

    override fun connect(): Result<Unit> {
        if (!hasConnectPermission()) return Result.failure(SecurityException("Bluetooth permission is not granted"))
        val bt = adapter ?: return Result.failure(IllegalStateException("Bluetooth adapter unavailable"))
        if (!bt.isEnabled) return Result.failure(IllegalStateException("Bluetooth is disabled"))
        val profile = synchronized(stateLock) { hid }
        if (profile == null) {
            synchronized(stateLock) { connectionPending = true }
            @Suppress("DEPRECATION")
            bt.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
            Log.i(TAG, "Bluetooth HID profile requested")
            return Result.success(Unit)
        }
        val registered = synchronized(stateLock) { appRegistered }
        if (!registered) {
            synchronized(stateLock) {
                connectionPending = true
                if (!registrationPending) registrationPending = true
            }
            registerApp()
            return Result.success(Unit)
        }
        return requestHostConnection()
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val profile = synchronized(stateLock) { hid } ?: return
        if (!hasConnectPermission()) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "HIDeck",
            "Android keyboard, mouse, and macro pad",
            "HIDeck",
            // Advertise the combined keyboard + mouse subclass so Windows
            // selects its standard HID device host path.
            BluetoothHidDevice.SUBCLASS1_COMBO,
            BLUETOOTH_REPORT_DESCRIPTOR
        )
        @Suppress("DEPRECATION")
        // Let the stack negotiate the host defaults. Some Windows stacks
        // reject a device-supplied QoS tuple during the initial L2CAP setup.
        val accepted = profile.registerApp(sdp, null, null, DIRECT_EXECUTOR, callback)
        if (!accepted) {
            synchronized(stateLock) { registrationPending = false }
            Log.w(TAG, "Bluetooth HID registerApp request rejected")
        } else {
            Log.i(TAG, "Bluetooth HID registerApp request accepted")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestHostConnection(): Result<Unit> {
        val profile = synchronized(stateLock) { hid } ?:
            return Result.failure(IllegalStateException("Bluetooth HID profile unavailable"))
        val selected = synchronized(stateLock) { host } ?: chooseHost()
            ?: return Result.failure(IllegalStateException("Pair a computer with this phone first"))
        synchronized(stateLock) {
            host = selected
            connectionPending = true
        }
        return try {
            @Suppress("DEPRECATION")
            if (profile.connect(selected)) {
                Log.i(TAG, "Bluetooth HID connection requested host=${selected.address}")
                Result.success(Unit)
            } else {
                synchronized(stateLock) { connectionPending = false }
                Result.failure(IllegalStateException("Bluetooth HID connection request was rejected"))
            }
        } catch (e: SecurityException) {
            synchronized(stateLock) { connectionPending = false }
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun chooseHost(): BluetoothDevice? {
        if (!hasConnectPermission()) return null
        @Suppress("DEPRECATION")
        return adapter?.bondedDevices?.firstOrNull { device ->
            @Suppress("DEPRECATION")
            device.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.COMPUTER
        } ?: adapter?.bondedDevices?.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        if (!hasConnectPermission()) return
        val (profile, selected, wasRegistered) = synchronized(stateLock) {
            Triple(hid, host, appRegistered)
        }
        if (selected != null) {
            try { profile?.disconnect(selected) } catch (_: Exception) { }
        }
        if (wasRegistered) {
            try { profile?.unregisterApp() } catch (_: Exception) { }
        }
        synchronized(stateLock) {
            connected = false
            appRegistered = false
            registrationPending = false
            connectionPending = false
            host = null
        }
        Log.i(TAG, "Bluetooth HID disconnected and app unregistered")
    }

    override fun sendKeyboard(report: ByteArray): Result<Unit> = send(1, report)

    override fun sendMouse(report: ByteArray): Result<Unit> = send(2, report)

    private fun send(reportId: Int, report: ByteArray): Result<Unit> {
        if (!isReady()) return Result.failure(IllegalStateException("Bluetooth HID is not connected"))
        return try {
            @Suppress("DEPRECATION")
            if (hid?.sendReport(host, reportId, report) == true) Result.success(Unit)
            else Result.failure(IllegalStateException("Bluetooth HID report rejected"))
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "HIDeck"
        private val DIRECT_EXECUTOR = Executor { it.run() }

        private val BLUETOOTH_REPORT_DESCRIPTOR = descriptor(
            // Keyboard report ID 1
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00,
            0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81.toByte(), 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07,
            0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00, 0xC0.toByte(),
            // Mouse report ID 2
            0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02,
            0x09, 0x01, 0xA1.toByte(), 0x00, 0x05, 0x09, 0x19, 0x01, 0x29, 0x03,
            0x15, 0x00, 0x25, 0x01, 0x95, 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
            0x95, 0x01, 0x75, 0x05, 0x81.toByte(), 0x01, 0x05, 0x01, 0x09, 0x30,
            0x09, 0x31, 0x09, 0x38, 0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08,
            0x95, 0x03, 0x81.toByte(), 0x06, 0xC0.toByte(), 0xC0.toByte()
        )

        private fun descriptor(vararg values: Any): ByteArray = values.map {
            when (it) {
                is Byte -> it
                is Int -> it.toByte()
                else -> 0
            }
        }.toByteArray()
    }
}
