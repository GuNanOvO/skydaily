package com.nankki.skydaily.core.update

import java.io.File

/**
 * 更新文件只落在应用私有的 `updates/` 目录，文件名由版本号生成。
 * 这里只做纯路径判断，便于在 JVM 单测中覆盖目录穿越与扩展名限制。
 */
object UpdatePaths {
    const val DIRECTORY = "updates"
    private val APK_NAME = Regex("^[0-9A-Za-z][0-9A-Za-z._-]*\\.apk$")

    fun directory(filesDir: File): File = File(filesDir, DIRECTORY)

    fun part(filesDir: File, versionCode: Int): File = File(directory(filesDir), "$versionCode.part")

    fun apk(filesDir: File, versionCode: Int): File = File(directory(filesDir), "$versionCode.apk")

    /**
     * 只接受目录下的直接子文件，且必须是 `.apk`。
     * `..`、子目录、绝对路径与其它扩展名一律拒绝。
     */
    fun resolve(root: File, name: String): File? {
        if (!APK_NAME.matches(name)) return null
        val directory = root.canonicalFile
        val file = File(directory, name).canonicalFile
        return if (file.parentFile == directory) file else null
    }

    /** 判断一个已有文件是否正好位于更新目录内。 */
    fun contains(root: File, file: File): Boolean {
        if (!APK_NAME.matches(file.name)) return false
        return file.canonicalFile.parentFile == root.canonicalFile
    }

    /** 只保留当前候选的临时文件。 */
    fun clearOtherParts(filesDir: File, versionCode: Int) {
        val keep = part(filesDir, versionCode).name
        for (file in directory(filesDir).listFiles().orEmpty()) {
            if (file.name.endsWith(".part") && file.name != keep) file.delete()
        }
    }

    /** 清理旧版本安装包，目录里最多保留正在使用的候选。 */
    fun clearOtherApks(filesDir: File, versionCode: Int) {
        val keep = apk(filesDir, versionCode).name
        for (file in directory(filesDir).listFiles().orEmpty()) {
            if (file.name.endsWith(".apk") && file.name != keep) file.delete()
        }
    }

    /**
     * 进程重启后的清理：未完成的 `.part` 不提供续传，一律删除；
     * 没有内存中发布信息的 `.apk` 也无法再展示或校验，同样删除。
     * 其它文件不属于更新流程，保持不动。
     */
    fun clearStale(filesDir: File) {
        for (file in directory(filesDir).listFiles().orEmpty()) {
            if (file.name.endsWith(".part") || file.name.endsWith(".apk")) file.delete()
        }
    }
}
