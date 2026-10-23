package com.nankki.skydaily.core.update

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * 只向系统安装器临时提供 `<filesDir>/updates/` 内的 APK。
 * 不导出、不接受目录穿越、只读打开。
 */
class UpdateFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = resolve(uri) ?: return null
        val columns = projection?.toList()?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns, 1)
        cursor.addRow(
            columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            },
        )
        return cursor
    }

    override fun getType(uri: Uri): String = APK_MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("更新安装包只读")
        val file = resolve(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("更新安装包只读")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("更新安装包只读")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("更新安装包只读")

    private fun resolve(uri: Uri): File? {
        if (uri.authority != AUTHORITY) return null
        val filesDir = context?.filesDir ?: return null
        val segments = uri.pathSegments
        if (segments.size != 1) return null
        val file = UpdatePaths.resolve(UpdatePaths.directory(filesDir), segments.single()) ?: return null
        return if (file.isFile) file else null
    }

    companion object {
        const val AUTHORITY = "com.nankki.skydaily.updates"
        const val APK_MIME = "application/vnd.android.package-archive"

        fun uriFor(fileName: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(fileName)
            .build()
    }
}
