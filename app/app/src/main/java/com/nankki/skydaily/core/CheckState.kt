package com.nankki.skydaily.core

/**
 * 每日勾选：键为「北京日期 + 任务号」，只在当日写入。
 * 跨天从零开始；历史日期不属于本应用的展示范围。
 */
class CheckState(private val store: KeyValueStore) {
    private fun key(date: String, number: Int) = "check:$date:$number"

    fun isDone(date: String, number: Int): Boolean = store.getString(key(date, number)) == "1"

    fun setDone(date: String, number: Int, done: Boolean) {
        if (done) {
            store.putString(key(date, number), "1")
            rememberDate(date)
        } else {
            store.remove(key(date, number))
        }
    }

    fun done(date: String): Set<Int> =
        (1..MAX_TASKS).filterTo(mutableSetOf()) { isDone(date, it) }

    fun completed(date: String): Int = done(date).size

    /** 丢弃窗口外的日期，避免存储无限增长。 */
    fun prune(keepDates: Collection<String>) {
        if (keepDates.isEmpty()) return
        val keep = keepDates.toSet()
        val remaining = mutableListOf<String>()
        for (date in knownDates()) {
            if (date in keep) {
                remaining.add(date)
            } else {
                (1..MAX_TASKS).forEach { store.remove(key(date, it)) }
            }
        }
        store.putString(KNOWN_KEY, remaining.joinToString(","))
    }

    private fun knownDates(): List<String> =
        (store.getString(KNOWN_KEY) ?: "").split(',').filter { it.isNotBlank() }

    private fun rememberDate(date: String) {
        val known = knownDates().toMutableList()
        if (date !in known) {
            known.add(date)
            store.putString(KNOWN_KEY, known.joinToString(","))
        }
    }

    private companion object {
        const val MAX_TASKS = 4
        const val KNOWN_KEY = "check:dates"
    }
}
