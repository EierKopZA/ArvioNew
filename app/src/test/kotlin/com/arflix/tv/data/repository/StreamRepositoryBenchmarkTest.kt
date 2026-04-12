package com.arflix.tv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.system.measureTimeMillis

class StreamRepositoryBenchmarkTest {

    @Test
    fun benchmarkSequentialVsParallelDiscovery() = runBlocking {
        val candidates = listOf("http://127.0.0.1:8090", "http://localhost:8090", "http://remote:8090")
        val paths = listOf("/stream", "/torrent/play")
        val combinations = candidates.flatMap { c -> paths.map { p -> c + p } }

        // Simulating network delay, assuming the last element is the successful one
        suspend fun simulateNetworkCall(url: String): String? {
            delay(50) // 50ms latency
            if (url == "http://remote:8090/torrent/play") {
                return "SUCCESS"
            }
            return null
        }

        var seqResult: String? = null
        val sequentialTime = measureTimeMillis {
            for (url in combinations) {
                val res = simulateNetworkCall(url)
                if (res != null) {
                    seqResult = res
                    break
                }
            }
        }

        println("Sequential discovery time: ${sequentialTime}ms, result: $seqResult")

        var parResult: String? = null
        val parallelTime = measureTimeMillis {
            parResult = withContext(Dispatchers.IO.limitedParallelism(4)) {
                coroutineScope {
                    val deferreds = combinations.map { url ->
                        async { simulateNetworkCall(url) }
                    }

                    // We need a custom select loop since standard select doesn't natively do "first non-null"
                    // without waiting for all if they are null.
                    // But in our actual implementation we'll need to handle the "all failed" case.
                    // For the benchmark, let's just do a simple check.
                    try {
                        var result: String? = null
                        val activeJobs = deferreds.toMutableList()
                        while (activeJobs.isNotEmpty() && result == null) {
                            val (job, value) = select<Pair<kotlinx.coroutines.Deferred<String?>, String?>> {
                                activeJobs.forEach { d ->
                                    d.onAwait { v -> Pair(d, v) }
                                }
                            }
                            activeJobs.remove(job)
                            if (value != null) {
                                result = value
                                break
                            }
                        }

                        deferreds.forEach { it.cancel() }
                        result
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }

        println("Parallel discovery time: ${parallelTime}ms, result: $parResult")
        assert(sequentialTime > parallelTime)
    }
}
