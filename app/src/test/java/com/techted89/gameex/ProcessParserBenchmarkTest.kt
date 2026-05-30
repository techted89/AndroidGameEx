package com.techted89.gameex

import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import kotlin.system.measureTimeMillis

class ProcessParserBenchmarkTest {

    @Test
    fun benchmarkRegexParsing() {
        println("Starting Benchmark...")
        // 1. Generate a large PS output sample
        val psOutputLine = "u0_a123 12345 678 1234 5678 9012 3456 S com.example.app"
        val lines = List(50000) { psOutputLine }
        val hugeString = lines.joinToString("\n")

        // Warm up
        // RootUtils.parsePsOutput(BufferedReader(StringReader(psOutputLine)))
        // We can't easily warm up RootUtils without affecting static state (none here)
        // But let's warm up JVM a bit with dummy loops
        repeat(100) {
            @Suppress("UNUSED_VARIABLE") val parts = psOutputLine.trim().split("\\s+".toRegex())
        }

        // 2. Measure Slow Implementation (simulated)
        val timeSlow = measureTimeMillis {
             val reader = BufferedReader(StringReader(hugeString))
             var line: String? = reader.readLine()
             while (line != null) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 9) {
                     val pidStr = parts[1]
                     val name = parts.last()
                     try {
                         val pid = pidStr.toInt()
                         // Construct ProcessInfo to be fair, assuming it's cheap
                         ProcessInfo(pid, name, name, null, true)
                     } catch (_: NumberFormatException) {
                         // Ignore
                     }
                }
                line = reader.readLine()
             }
        }
        println("Slow parsing took: $timeSlow ms")

        // 3. Measure Optimized Implementation (Actual Code)
        val timeFast = measureTimeMillis {
            val reader = BufferedReader(StringReader(hugeString))
            RootUtils.parsePsOutput(reader)
        }
        println("Fast parsing took: $timeFast ms")

        val improvement = timeSlow - timeFast
        val percentage = improvement.toDouble() / timeSlow.toDouble() * 100
        println("Improvement: $improvement ms ($percentage%)")

        // Assert improvement
        assert(timeFast < timeSlow) { "Optimization failed to improve performance" }
    }
}
