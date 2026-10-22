package com.nankki.skydaily

import com.nankki.skydaily.core.KeyValueStore
import com.nankki.skydaily.data.DiskStore
import com.nankki.skydaily.data.Fetch
import com.nankki.skydaily.data.HttpTransport

class MemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

class MemoryDiskStore : DiskStore {
    val files = mutableMapOf<String, String>()

    override fun read(name: String): String? = files[name]

    override fun write(name: String, text: String) {
        files[name] = text
    }

    override fun remove(name: String) {
        files.remove(name)
    }

    override fun removeAll(prefix: String) {
        files.keys.removeAll { it.startsWith(prefix) }
    }
}

class FakeTransport(val responses: MutableList<Fetch>) : HttpTransport {
    val requests = mutableListOf<Pair<String, String?>>()

    override fun get(path: String, etag: String?): Fetch {
        requests.add(path to etag)
        return if (responses.isEmpty()) Fetch.Failed("no response queued") else responses.removeAt(0)
    }
}

fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/$name")) { "missing fixture $name" }
        .bufferedReader()
        .use { it.readText() }
