package com.hondasports.hideck

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

/** Small, synchronous root bridge. Every command is explicit and bounded by a timeout. */
object RootShell {
    private const val TIMEOUT_SECONDS = 8L

    fun exec(command: String, timeoutSeconds: Long = TIMEOUT_SECONDS): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = Thread { process.inputStream.copyTo(stdout) }
            val errThread = Thread { process.errorStream.copyTo(stderr) }
            outThread.start()
            errThread.start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ShellResult(-1, stdout.toString(), "timeout")
            }
            outThread.join(200)
            errThread.join(200)
            ShellResult(process.exitValue(), stdout.toString(), stderr.toString())
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
        }
    }

    fun hasRoot(): Boolean = exec("id").let { it.isSuccess && it.stdout.contains("uid=0") }

    /** Writes a short HID report through a root-owned file descriptor. */
    fun writeBytes(path: String, bytes: ByteArray): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", "cat > ${quote(path)}")
                .redirectErrorStream(false)
                .start()
            process.outputStream.use { it.write(bytes) }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                ShellResult(-1, "", "timeout")
            } else {
                val error = process.errorStream.bufferedReader().use { it.readText() }
                ShellResult(process.exitValue(), "", error)
            }
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
        }
    }

    fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun read(path: String): String = try {
        File(path).readText()
    } catch (_: Exception) {
        ""
    }
}
