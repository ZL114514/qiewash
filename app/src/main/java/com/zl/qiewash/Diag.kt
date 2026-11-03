package com.zl.qiewash

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ring-buffered step log for field diagnosis (read with `adb pull .../diag.log`). */
object Diag {
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    fun attach(dir: File?) {
        if (dir == null) return
        dir.mkdirs()
        file = File(dir, "diag.log").also {
            if (it.exists() && it.length() > 256 * 1024) it.delete()
        }
    }

    fun filePath(): String = file?.absolutePath.orEmpty()

    fun log(msg: String) {
        val line = "${fmt.format(Date())} $msg"
        Log.i("QieWash", line)
        runCatching { file?.appendText(line + "\n") }
    }
}
