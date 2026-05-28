package com.techted89.gameex.scripting

import android.content.Context
import com.techted89.gameex.NativeScanner
import com.techted89.gameex.MemoryResult
import com.techted89.gameex.utils.ProcessUtils
import com.techted89.gameex.utils.SystemUtils
import java.lang.ref.WeakReference

object GameGuardianAPI {
    private var contextRef: WeakReference<Context>? = null
    private var targetPid: Int = 0

    fun init(context: Context) {
        contextRef = WeakReference(context)
    }

    fun setTargetPid(pid: Int) {
        targetPid = pid
    }

    // Constants
    const val TYPE_AUTO = 127
    const val TYPE_BYTE = 1
    const val TYPE_WORD = 2
    const val TYPE_DWORD = 4
    const val TYPE_XOR = 8
    const val TYPE_FLOAT = 16
    const val TYPE_QWORD = 32
    const val TYPE_DOUBLE = 64

    const val SIGN_EQUAL = 0
    const val SIGN_NOT_EQUAL = 1
    const val SIGN_LESS_OR_EQUAL = 2
    const val SIGN_GREATER_OR_EQUAL = 3

    /**
     * Searches for a value with the specified type and flags.
     * Mapped to NativeScanner.searchMemoryString.
     */
    @Suppress("UNUSED_PARAMETER")
    fun searchNumber(text: String, type: Int, encrypted: Boolean, sign: Int, memoryFrom: Long, memoryTo: Long) {
        // Construct query string based on parameters if needed
        // For now, we pass the raw text which might contain ranges etc.
        NativeScanner.searchMemoryString(targetPid, text)
    }

    /**
     * Refines the search results.
     */
    @Suppress("UNUSED_PARAMETER")
    fun refineNumber(text: String, type: Int) {
        val intVal = text.toIntOrNull() ?: 0
        NativeScanner.filterMemory(targetPid, intVal)
    }

    /**
     * Returns the list of results.
     */
    fun getResults(count: Int): List<MemoryResult> {
        val addresses = NativeScanner.getResults(count)
        return addresses.map { MemoryResult(it, "Unknown") }
    }

    /**
     * Edits all found results to the specified value.
     */
    @Suppress("UNUSED_PARAMETER")
    fun editAll(text: String, type: Int) {
        // NativeScanner.writeMemoryLoop(...)
    }

    @Suppress("UNUSED_PARAMETER")
    fun dumpMemory(from: Long, to: Long, dir: String, flags: Int? = null): Boolean {
        return NativeScanner.dumpMemory(targetPid, from, to, dir)
    }

    @Suppress("UNUSED_PARAMETER")
    fun copyText(text: String, fixLocale: Boolean = true) {
        contextRef?.get()?.let { SystemUtils.copyText(it, text) }
    }

    fun isPackageInstalled(pkg: String): Boolean {
        return contextRef?.get()?.let { SystemUtils.isPackageInstalled(it, pkg) } ?: false
    }

    fun getTargetPackage(): String? {
        return SystemUtils.getTargetPackage(targetPid)
    }

    @Suppress("UNUSED_PARAMETER")
    fun searchPointer(maxOffset: Int, memoryFrom: Long = 0, memoryTo: Long = -1, limit: Long = 0) {
        // NativeScanner.searchPointer(...)
    }

    fun setSpeed(speed: Double): Boolean {
        return NativeScanner.setSpeed(speed)
    }

    fun clearResults() {
        // Clear global results
    }

    @Suppress("UNUSED_PARAMETER")
    fun toast(text: String, fast: Boolean = false) {
        // Callback to UI needed
    }

    @Suppress("UNUSED_PARAMETER")
    fun alert(text: String, positive: String = "ok", negative: String? = null, neutral: String? = null): Int {
        // Blocking dialog logic stub
        return 1
    }

    fun sleep(milliseconds: Int) {
        try {
            Thread.sleep(milliseconds.toLong())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun isVisible(): Boolean {
        return true // Mock state
    }

    @Suppress("UNUSED_PARAMETER")
    fun setVisible(visible: Boolean) {
        // UI toggle callback needed
    }

    fun processPause(): Boolean {
        ProcessUtils.pauseProcess(targetPid)
        return true
    }

    fun processResume(): Boolean {
        ProcessUtils.resumeProcess(targetPid)
        return true
    }

    /**
     * Mock function to execute a script string.
     * In a real app, this would use LuaJ to parse and run the script.
     */
    fun executeScript(script: String): String {
        // Parsing logic stub
        if (script.contains("gg.searchNumber")) {
             return "Script executed: Search started."
        }
        return "Script executed: No operation."
    }
}
