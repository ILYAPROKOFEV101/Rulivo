package com.ilya.rulivo.ar

import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Bounded, best-effort JSONL writer. Disk access and encoding never run on the render thread. */
internal class DiagnosticJournal(
    directory: File,
    metadata: Map<String, Any?>,
    private val maxBytes: Long = 8L * 1024 * 1024,
    queueCapacity: Int = 256,
    private val retainedSessions: Int = 5,
    private val onFailure: (Exception) -> Unit = {},
) {
    val sessionId: String = UUID.randomUUID().toString()
    private val queue = ArrayBlockingQueue<Map<String, Any?>>(queueCapacity)
    private val finished = CountDownLatch(1)
    @Volatile private var closing = false
    @Volatile private var disabled = false
    private var sequence = 0L
    private var dropped = 0L

    init {
        require(maxBytes > 0 && retainedSessions > 0)
        val header = envelope("session", metadata)
        Thread({
            try {
                check(directory.isDirectory || directory.mkdirs()) { "Cannot create diagnostics directory" }
                directory.listFiles { file -> file.name.startsWith("accuracy-") && file.extension == "jsonl" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(retainedSessions - 1)?.forEach { it.delete() }
                File(directory, "accuracy-$sessionId.jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
                    var bytes = 0L
                    fun write(record: Map<String, Any?>) {
                        val line = DiagnosticJson.encode(record) + "\n"
                        writer.write(line)
                        writer.flush()
                        bytes += line.toByteArray(Charsets.UTF_8).size
                    }
                    write(header)
                    var limited = false
                    while (!closing || queue.isNotEmpty()) {
                        val record = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                        // The final marker may exceed the cap slightly; never leave silent truncation.
                        val size = DiagnosticJson.encode(record).toByteArray(Charsets.UTF_8).size + 1
                        if (bytes + size > maxBytes) {
                            synchronized(this) { disabled = true }
                            limited = true
                            queue.clear()
                            break
                        }
                        write(record)
                    }
                    write(envelope("journal_end", mapOf(
                        "reason" to if (limited) "size_limit" else "closed",
                        "dropped_events" to synchronized(this) { dropped },
                    )))
                }
            } catch (exception: Exception) {
                disabled = true
                queue.clear()
                onFailure(exception)
            } finally {
                finished.countDown()
            }
        }, "Rulivo-diagnostics").apply { isDaemon = true }.start()
    }

    /** Values must be detached immutable snapshots: never pass an ARCore object or mutable buffer. */
    @Synchronized fun record(type: String, fields: Map<String, Any?> = emptyMap()): Boolean {
        if (closing || disabled) return false
        val accepted = queue.offer(envelope(type, fields))
        if (!accepted) dropped++
        return accepted
    }

    @Synchronized private fun envelope(type: String, fields: Map<String, Any?>): Map<String, Any?> =
        fields + mapOf("schema_version" to 1, "session_id" to sessionId, "sequence" to sequence++,
            "event" to type, "wall_time_ms" to System.currentTimeMillis(), "dropped_events" to dropped)

    /** Non-blocking, including when called from Activity.onDestroy. */
    @Synchronized fun close() { closing = true }

    /** Test/export synchronization only; never call from the UI or GL thread. */
    fun awaitClosed(timeoutMillis: Long): Boolean = finished.await(timeoutMillis, TimeUnit.MILLISECONDS)
}

/** Small JSON encoder for diagnostic snapshots; non-finite sensor values become JSON null. */
internal object DiagnosticJson {
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character < ' ' || character.isSurrogate()) {
                        append("\\u"); append(character.code.toString(16).padStart(4, '0'))
                    } else append(character)
                }
            }
            append('"')
        }
        is Boolean -> value.toString()
        is Number -> if (value.toDouble().isFinite()) value.toString() else "null"
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") {
            require(it.key is String)
            encode(it.key) + ":" + encode(it.value)
        }
        is Iterable<*> -> value.joinToString(",", "[", "]") { encode(it) }
        else -> error("Unsupported diagnostic value: ${value.javaClass.simpleName}")
    }
}
