package com.oscpad.osc

/** One logged OSC message. Formatting is deferred until the monitor actually displays it. */
class LogEntry(
    val seq: Long,
    val time: Long,
    val outgoing: Boolean,
    val address: String,
    val args: List<Any?>,
)

/**
 * Fixed-size ring buffer of recent messages. Adding is O(1) with no copying, so logging every
 * message costs almost nothing; the monitor polls [version] and copies only while it is open.
 */
class MessageLog(val capacity: Int = 500) {
    private val items = arrayOfNulls<LogEntry>(capacity)
    private var next = 0
    private var count = 0
    private var seq = 0L

    @Volatile var version = 0L
        private set
    var incoming = 0L
        private set
    var outgoing = 0L
        private set

    @Synchronized
    fun add(outgoing: Boolean, address: String, args: List<Any?>, time: Long = System.currentTimeMillis()) {
        items[next] = LogEntry(seq++, time, outgoing, address, args)
        next = (next + 1) % capacity
        if (count < capacity) count++
        if (outgoing) this.outgoing++ else incoming++
        version++
    }

    @Synchronized
    fun newestFirst(): List<LogEntry> = List(count) { i -> items[(next - 1 - i + capacity) % capacity]!! }

    @Synchronized
    fun clear() {
        items.fill(null)
        next = 0
        count = 0
        incoming = 0
        outgoing = 0
        version++
    }
}
