package com.nankki.skydaily.data

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * 窄文件存储接口：单测用内存实现，生产落在 filesDir/journal。
 *
 * 实现约定：读取失败返回 null；写入是尽力而为的缓存，不得因存储错误向界面抛异常。
 */
interface DiskStore {
    fun read(name: String): String?
    fun write(name: String, text: String)
    fun remove(name: String)
    fun removeAll(prefix: String)
}

class DirectoryDiskStore internal constructor(private val root: File) : DiskStore {
    constructor(context: Context) : this(File(context.filesDir, "journal"))

    init {
        root.mkdirs()
    }

    private fun file(name: String) = File(root, name.replace('/', '_'))

    override fun read(name: String): String? = try {
        file(name).takeIf { it.isFile }?.readText()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    override fun write(name: String, text: String) {
        val target = file(name)
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        } catch (_: IOException) {
            temp.delete()
        } catch (_: SecurityException) {
            temp.delete()
        }
    }

    override fun remove(name: String) {
        file(name).delete()
    }

    override fun removeAll(prefix: String) {
        val key = prefix.replace('/', '_')
        try {
            root.listFiles()?.forEach { file ->
                if (file.name.startsWith(key)) file.delete()
            }
        } catch (_: SecurityException) {
            // 缓存清理失败不影响界面继续使用已有数据。
        }
    }
}
