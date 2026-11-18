package com.mouajmd.IceBox

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootShell {
    private var process: Process? = null
    private var os: DataOutputStream? = null
    private var reader: BufferedReader? = null

    @Synchronized
    private fun ensureSession(): Boolean {
        if (process != null && os != null) return true
        return try {
            val p = Runtime.getRuntime().exec("su")
            process = p
            os = DataOutputStream(p.outputStream)
            reader = BufferedReader(InputStreamReader(p.inputStream))
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    @Synchronized
    fun setAppEnabled(packageName: String, enabled: Boolean): Boolean {
        if (!ensureSession()) return false
        val cmd = if (enabled) "pm enable $packageName" else "pm disable $packageName"
        return try {
            os?.writeBytes("$cmd >/dev/null 2>&1; echo \$?\n")
            os?.flush()
            reader?.readLine()?.trim() == "0"
        } catch (e: Exception) {
            close()
            false
        }
    }

    @Synchronized
    fun freezeBatch(packageNames: List<String>): Boolean {
        if (packageNames.isEmpty()) return true
        if (!ensureSession()) return false
        val joined = packageNames.joinToString("; ") { "pm disable $it >/dev/null 2>&1" }
        return try {
            os?.writeBytes("$joined; echo 0\n")
            os?.flush()
            reader?.readLine()?.trim() == "0"
        } catch (e: Exception) {
            close()
            false
        }
    }

    @Synchronized
    fun close() {
        try {
            os?.writeBytes("exit\n")
            os?.flush()
            os?.close()
            reader?.close()
            process?.destroy()
        } catch (_: Exception) {
        } finally {
            os = null; reader = null; process = null
        }
    }
}
