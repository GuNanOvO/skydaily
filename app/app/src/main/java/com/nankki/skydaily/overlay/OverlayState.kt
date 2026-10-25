package com.nankki.skydaily.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 悬浮球服务的运行状态；自动隐藏窗口时仍保持运行。 */
object OverlayState {
    var running by mutableStateOf(false)
        internal set
}
