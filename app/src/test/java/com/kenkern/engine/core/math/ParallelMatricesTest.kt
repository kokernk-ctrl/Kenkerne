package com.kenkern.engine.core.math

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ParallelMatricesTest {
    @Test fun cenasIndependentesNaoCompartilhamTemporariosMesmoComAliasing() {
        val threads = Executors.newFixedThreadPool(2)
        val start = CyclicBarrier(2)
        try {
            val tasks = listOf(-1f, 1f).map { sign -> Callable {
                val a = Mat4(); val b = Mat4().setIdentity().apply { m[12] = 11f * sign }
                start.await(5, TimeUnit.SECONDS)
                repeat(10_000) {
                    a.setIdentity(); a.m[12] = it * sign
                    a.multiply(a, b)
                    assertEquals((it + 11) * sign, a.m[12], 0f)
                }
            } }
            threads.invokeAll(tasks).forEach { it.get() }
        } finally { threads.shutdownNow() }
    }
}
