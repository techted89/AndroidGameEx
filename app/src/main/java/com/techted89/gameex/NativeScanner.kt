package com.techted89.gameex

object NativeScanner {
    const val FUZZY_CHANGED = 0
    const val FUZZY_UNCHANGED = 1
    const val FUZZY_INCREASED = 2
    const val FUZZY_DECREASED = 3

    const val TYPE_BYTE = 1
    const val TYPE_WORD = 2
    const val TYPE_DWORD = 4
    const val TYPE_XOR = 8
    const val TYPE_FLOAT = 16
    const val TYPE_QWORD = 32
    const val TYPE_DOUBLE = 64

    init {
        System.loadLibrary("native-scanner")
    }

    // Supported Data Types (Legacy/Duplicates commented out)
    /* [LEGACY/UNUSED]
    const val TYPE_BYTE = 1
    const val TYPE_WORD = 2
    const val TYPE_DWORD = 4
    const val TYPE_QWORD = 8
    const val TYPE_FLOAT = 16
    const val TYPE_DOUBLE = 32
    const val TYPE_AUTO = 64
    const val TYPE_XOR = 128

    // Fuzzy Scan Modes
    const val FUZZY_CHANGED = 0
    const val FUZZY_UNCHANGED = 1
    const val FUZZY_INCREASED = 2
    const val FUZZY_DECREASED = 3
    */
    const val TYPE_AUTO = 128 // Added to avoid conflicts with existing TYPE_* constants

    /**
 * Read a sequence of bytes from the memory of a target process.
 *
 * @param pid The target process identifier (PID).
 * @param address The starting absolute memory address in the target process to read from.
 * @param size The number of bytes to read.
 * @return A byte array containing the bytes read; its length will be equal to `size` when the read succeeds.
 */
    external fun readMemory(pid: Int, address: Long, size: Int): ByteArray

    /**
 * Searches the target process's memory for occurrences of a 4-byte integer value.
 *
 * @param pid The target process ID.
 * @param value The 4-byte integer value to search for.
 * @return The number of matches found.
 */
    external fun searchMemory(pid: Int, value: Int): Int

    /**
     * Searches memory using a query string (e.g., "100~200" for range, "100X8" for XOR).
     */
    external fun searchMemoryString(pid: Int, query: String): Int

    /**
     * Searches memory with a specific data type (Byte, Word, Dword, Float, Double).
     * @param pid Target Process ID
     * @param valueStr Value to search (e.g. "100.5", "10~20")
     * @param type Data Type constant (TYPE_FLOAT, etc.)
     */
    external fun searchMemory(pid: Int, valueStr: String, type: Int): Int

    /**
     * Starts a fuzzy scan by snapshotting relevant memory regions.
     */
    external fun startFuzzyScan(pid: Int)

    /**
     * Filters the fuzzy snapshot against current memory based on the mode.
     * @param pid Target Process ID
     * @param mode Comparison mode (FUZZY_CHANGED, etc.)
     * @return Number of results found.
     */
    external fun filterFuzzy(pid: Int, mode: Int): Int

    /* [LEGACY/UNUSED]
    external fun readMemory(pid: Int, address: Long, size: Int): ByteArray
    external fun searchMemory(pid: Int, query: String, type: Int): Int
    external fun filterMemory(pid: Int, query: String, type: Int): Int
    external fun startFuzzyScan(pid: Int, type: Int)
    external fun filterFuzzy(pid: Int, mode: Int, type: Int): Int
    external fun filterMemory(pid: Int, valueStr: String, type: Int): Int
    external fun filterMemory(pid: Int, valueStr: String, type: Int): Int
    */

    /**
     * Retrieves a list of loaded modules (libraries) in the target process.
     *
     * @param pid The target process ID.
     * @return An array of strings representing loaded module names/paths.
     */
    external fun getLoadedModules(pid: Int): Array<String>

    /**
     * Enables stealth mode features (hiding process, randomized name).
     */
    external fun enableStealthMode()

    /**
     * Disassembles a Lua script (binary or source) to an assembly listing.
     * @param inPath Input file path.
     * @param outPath Output file path (.asm).
     */
    external fun disassembleScript(inPath: String, outPath: String)

    /**
     * Assembles an assembly listing back into a Lua binary chunk.
     * @param inPath Input assembly file path.
     * @param outPath Output binary file path.
     */
    external fun assembleScript(inPath: String, outPath: String)

    /**
     * Dumps memory regions to files on disk.
     */
    external fun dumpMemory(pid: Int, from: Long, to: Long, path: String): Boolean

    /**
     * Installs a hook at the target address.
     *
     * @param pid Target process ID.
     * @param targetAddress Address to hook.
     * @param replacementAddress Address to jump to.
     * @return True if successful.
     */
    external fun installHook(pid: Int, targetAddress: Long, replacementAddress: Long): Boolean

    /**
     * Removes a previously installed hook.
     *
     * @param pid Target process ID.
     * @param targetAddress Address of the hook.
     * @return True if successful.
     */
    external fun removeHook(pid: Int, targetAddress: Long): Boolean

    /**
     * Sets the speed multiplier for the speedhack engine.
     */
    external fun setSpeed(speed: Double): Boolean

    /**
     * Retrieves up to the specified number of addresses discovered by the native scanner.
     *
     * @param limit Maximum number of addresses to return.
     * @return A LongArray of found memory addresses; the array length will be less than or equal to `limit`.
     */
    external fun getResults(limit: Int): LongArray
}
