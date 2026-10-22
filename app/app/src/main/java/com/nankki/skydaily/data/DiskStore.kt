package com.nankki.skydaily.data

import android.content.Context
import java.io.File

/** 窄文件存储接口：单测用内存实现，生产落在 filesDir/journal。 */
interface DiskStore {
    fun read(name: String): String?
    fun write(name: String, text: String)
    fun remove(name: String)
    fun removeAll(prefix: String)
}

class DirectoryDiskStore(context: Context) : DiskStore {
    private val root = File(context.filesDir, "journal").apply { mkdirs() }

    private fun file(name: String) = File(root, name.replace('/', '_'))

    override fun read(name: String): String? = file(name).takeIf { it.isFile }?.readText()

    override fun write(name: String, text: String) {
        val target = file(name)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    override fun remove(name: String) {
        file(name).delete()
    }

    override fun removeAll(prefix: String) {
        root.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix.replace('/', '_'))) file.delete()
        }
    }
}
