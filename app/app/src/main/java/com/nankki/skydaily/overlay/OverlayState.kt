package com.nankki.skydaily.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 悬浮球服务的可见状态，供主界面实时显示。 */
object OverlayState {
    var running by mutableStateOf(false)
        internal set
}
