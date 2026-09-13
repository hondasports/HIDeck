package com.hondasports.hideck

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/** Device-local lock that prevents the Android side from mounting the gadget image while exported. */
class StorageCoordinator(context: Context) {
    private val lockFile = File(context.filesDir, "hideck-storage.lock")
    // The kernel's mass-storage function opens this path outside the app
    // sandbox. An app-private file is readable by the app but is rejected by
    // SELinux when the USB gadget kernel code opens it.
    private val imageFile = File("/data/adb/hideck-storage.img")
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun ensureImage(sizeBytes: Long = 128L * 1024L * 1024L): File {
        val path = RootShell.quote(imageFile.absolutePath)
        val result = RootShell.exec(
            "mkdir -p /data/adb; truncate -s $sizeBytes $path; chmod 600 $path"
        )
        if (!result.isSuccess) {
            throw IllegalStateException(result.stderr.ifBlank { result.stdout.ifBlank { "cannot create storage image" } })
        }
        return imageFile
    }

    @Synchronized
    fun acquireAndroidMount(): Result<Unit> {
        return acquireLock()
    }

    /** Hold the same lock while the image is exported through USB. */
    @Synchronized
    fun acquireExport(): Result<Unit> {
        return acquireLock()
    }

    @Synchronized
    private fun acquireLock(): Result<Unit> {
        if (lock != null) return Result.success(Unit)
        return try {
            lockFile.parentFile?.mkdirs()
            channel = RandomAccessFile(lockFile, "rw").channel
            lock = channel?.tryLock()
            if (lock == null) {
                channel?.close()
                channel = null
                Result.failure(IllegalStateException("storage image is exported to the host"))
            } else Result.success(Unit)
        } catch (e: Exception) {
            channel?.close()
            channel = null
            Result.failure(e)
        }
    }

    @Synchronized
    fun releaseAndroidMount() {
        try { lock?.release() } catch (_: Exception) { }
        try { channel?.close() } catch (_: Exception) { }
        lock = null
        channel = null
    }

    @Synchronized
    fun releaseExport() {
        releaseAndroidMount()
    }

    @Synchronized
    fun isAndroidMountHeld(): Boolean = lock != null
}
