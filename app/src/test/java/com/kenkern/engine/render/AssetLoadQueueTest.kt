package com.kenkern.engine.render

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.FutureTask

class AssetLoadQueueTest {
    private class Harness(capacity: Int = 2) {
        val jobs = ArrayDeque<FutureTask<String>>()
        val decoded = mutableListOf<String>()
        val uploaded = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var reject = false
        var broken: String? = null
        var fallback: String? = null
        val queue = AssetLoadQueue<String, String, String>(
            { !reject && jobs.add(it) }, { jobs.remove(it) }, { key ->
                decoded += key
                check(key != broken) { "missing $key" }
                key
            }, capacity
        )
        fun tick() = queue.tick({ _, data ->
            uploaded += data
            if (data == fallback) AssetUpload.DecodeFallback { "$data/rgba" }
            else AssetUpload.Ready(data)
        }) { key, _ -> failures += key }
        fun work() = jobs.removeFirst().run()
        fun see(vararg keys: String) { keys.forEach { queue.request(it) } }
    }

    @Test fun `dedupe and bounded admission without decoding on frame thread`() = with(Harness()) {
        see("mesh", "mesh", "tex", "overflow")
        tick()
        assertEquals(1, jobs.size)
        assertTrue(decoded.isEmpty())
        see("mesh", "tex", "overflow")
        tick() // unfinished task must never block or upload
        assertTrue(uploaded.isEmpty())
        see("mesh", "tex", "overflow")
        work()
        tick()
        assertEquals(listOf("mesh"), uploaded)
        assertEquals("mesh", queue.request("mesh"))
        see("tex", "overflow")
        work()
        tick()
        assertEquals(listOf("mesh", "tex"), uploaded)
        work()
        assertEquals(listOf("mesh", "tex", "overflow"), decoded)
    }

    @Test fun `discard decoded result before stale upload and release only matching residents`() = with(Harness()) {
        see("keep", "replace")
        tick(); work()
        see("keep", "replace")
        tick(); work()
        queue.invalidate({ it == "replace" }) { _, _ -> fail("result had never uploaded") }
        tick()
        assertEquals(listOf("keep"), uploaded)
        see("replace"); tick()
        val old = jobs.first()
        queue.invalidate({ it == "replace" }) { _, _ -> fail() }
        assertTrue(old.isCancelled)
        assertTrue(jobs.isEmpty())
        old.run()
        assertEquals("keep", queue.request("keep"))
        see("replace"); tick(); work()
        see("replace"); tick()
        assertEquals(listOf("keep", "replace"), uploaded)
        val released = mutableListOf<String>()
        queue.invalidate({ it == "replace" }) { _, value -> released += value }
        assertEquals(listOf("replace"), released)
        assertEquals("keep", queue.request("keep"))
    }

    @Test fun `backpressure retries and decode failure retries only after invalidation`() = with(Harness()) {
        reject = true
        broken = "tex"
        see("tex"); tick()
        assertTrue(jobs.isEmpty())
        reject = false
        see("tex"); tick(); work()
        see("tex"); tick()
        repeat(4) { see("tex"); tick() }
        assertEquals(listOf("tex"), decoded)
        assertEquals(listOf("tex"), failures)
        queue.invalidate({ it == "tex" }) { _, _ -> fail() }
        broken = null
        see("tex"); tick(); work()
        see("tex"); tick()
        assertEquals("tex", queue.request("tex"))
    }

    @Test fun `fallback is deferred and invisible unfinished requests can be cancelled`() = with(Harness()) {
        fallback = "etc2"
        see("etc2", "gone"); tick(); work()
        see("etc2"); tick()
        assertEquals(listOf("etc2"), uploaded)
        assertNull(queue.request("etc2"))
        work(); tick()
        assertEquals(listOf("etc2", "etc2/rgba"), uploaded)
        assertEquals("etc2/rgba", queue.request("etc2"))
        assertTrue(jobs.isEmpty())
    }
}
