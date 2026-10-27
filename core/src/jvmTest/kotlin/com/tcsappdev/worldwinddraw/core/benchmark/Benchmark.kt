package com.tcsappdev.worldwinddraw.core.benchmark

import java.io.File

/**
 * A small measuring helper for the benchmarks: runs a block a few times (the first runs warm the JVM up and are dropped), keeps the
 * median, and writes every result to a report file so the numbers of a run can be read, compared and pasted into the plan.
 *
 * The benchmarks are tests so they run with the others and catch a real regression with a generous upper bound; the numbers
 * themselves are for people (see `build/reports/benchmarks-<name>.txt`).
 */
class Benchmark(private val name: String) {
    private val lines = ArrayList<String>()

    /** Runs [block] [warmup] + [runs] times and returns the median of the timed runs, in milliseconds. */
    fun measure(label: String, warmup: Int = 2, runs: Int = 5, block: () -> Unit): Double {
        repeat(warmup) { block() }
        val times = DoubleArray(runs) {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1e6
        }.sorted()
        val median = times[times.size / 2]
        record("%-62s %10.2f ms (min %.2f, max %.2f)".format(label, median, times.first(), times.last()))
        return median
    }

    /** Memory still held after [block] returns its result, in megabytes (the heap before and after a full collection; rough, JVM only). */
    fun retainedMegabytes(label: String, block: () -> Any): Double {
        gc()
        val before = usedHeap()
        val kept = block()
        gc()
        val megabytes = (usedHeap() - before) / 1e6
        record("%-62s %10.2f MB".format(label, megabytes))
        if (kept.hashCode() == Int.MIN_VALUE) println() // keeps the result reachable until the second reading
        return megabytes
    }

    fun record(line: String) {
        lines += line
        println("[benchmark:$name] $line")
    }

    fun write() {
        val dir = File("build/reports").apply { mkdirs() }
        File(dir, "benchmarks-$name.txt").appendText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun gc() {
        repeat(3) { System.gc(); Thread.sleep(30) }
    }

    private fun usedHeap() = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
}
