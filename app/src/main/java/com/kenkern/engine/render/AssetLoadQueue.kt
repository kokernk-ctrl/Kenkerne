package com.kenkern.engine.render

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

internal sealed interface AssetUpload<out D, out R> {
    data class Ready<R>(val value: R) : AssetUpload<Nothing, R>
    data class DecodeFallback<D>(val decode: () -> D) : AssetUpload<D, Nothing>
}

/** GL-owned state machine. Only the FutureTask body runs on the worker. */
internal class AssetLoadQueue<K, D, R>(
    private val submit: (FutureTask<D>) -> Boolean,
    private val remove: (FutureTask<D>) -> Unit,
    private val decode: (K) -> D,
    private val maxPending: Int = 32
) {
    private class Request<D>(var frameSeen: Long, val fallback: (() -> D)? = null)
    private val pending = LinkedHashMap<K, Request<D>>()
    private val resident = HashMap<K, R>()
    private val failed = HashSet<K>()
    private var inFlight: Pair<K, FutureTask<D>>? = null
    private var frame = 0L

    fun request(key: K): R? {
        resident[key]?.let { return it }
        if (key in failed) return null
        pending[key]?.let { it.frameSeen = frame; return null }
        if (pending.size < maxPending) pending[key] = Request(frame)
        return null
    }

    fun invalidate(predicate: (K) -> Boolean, dispose: (K, R) -> Unit) {
        prunePending(predicate)
        failed.removeAll(predicate)
        val entries = resident.iterator()
        while (entries.hasNext()) {
            val (key, value) = entries.next()
            if (predicate(key)) {
                entries.remove()
                dispose(key, value)
            }
        }
    }

    private fun prunePending(predicate: (K) -> Boolean) {
        inFlight?.let { (key, task) ->
            if (predicate(key)) {
                task.cancel(true)
                remove(task)
                inFlight = null
            }
        }
        pending.keys.removeAll(predicate)
    }

    /** One completed asset (or failed attempt) max per frame; never wait for a worker. */
    fun tick(upload: (K, D) -> AssetUpload<D, R>, onFailure: (K, Throwable) -> Unit) {
        prunePending { checkNotNull(pending[it]).frameSeen < frame }
        frame++
        val active = inFlight
        if (active != null && active.second.isDone) {
            val (key, task) = active
            inFlight = null
            val request = checkNotNull(pending.remove(key))
            try {
                when (val result = upload(key, task.get())) {
                    is AssetUpload.Ready -> resident[key] = result.value
                    is AssetUpload.DecodeFallback -> {
                        check(request.fallback == null) { "fallback de upload também foi recusado" }
                        pending[key] = Request(request.frameSeen, result.decode)
                    }
                }
            } catch (e: Exception) {
                failed.add(key)
                onFailure(key, if (e is ExecutionException) e.cause ?: e else e)
            }
        }
        if (inFlight == null) {
            val next = pending.entries.firstOrNull { it.value.fallback != null }
                ?: pending.entries.firstOrNull() ?: return
            val key = next.key
            val workerDecode = next.value.fallback ?: { decode(key) }
            val task = FutureTask(workerDecode)
            if (submit(task)) inFlight = key to task
        }
    }
}
