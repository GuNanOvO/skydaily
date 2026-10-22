package com.nankki.skydaily.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nankki.skydaily.data.Envelope
import com.nankki.skydaily.data.HistoryRecordLoad
import com.nankki.skydaily.ui.journal.JournalPage
import com.nankki.skydaily.ui.journal.JournalUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArchivePage(date: String, todayState: JournalUiState, onToggleTask: (Int, Boolean) -> Unit) {
    val repository = rememberHistoryRepository()
    var envelope by remember(date) { mutableStateOf<Envelope?>(null) }
    var loading by remember(date) { mutableStateOf(true) }
    var error by remember(date) { mutableStateOf<String?>(null) }
    var offline by remember(date) { mutableStateOf(false) }
    var reload by remember(date) { mutableIntStateOf(0) }
    LaunchedEffect(date, reload) {
        loading = true
        envelope = withContext(Dispatchers.IO) { repository.cachedDate(date)?.envelope }
        when (val result = withContext(Dispatchers.IO) { repository.loadDate(date) }) {
            is HistoryRecordLoad.Success -> {
                envelope = result.record
                offline = false
                error = null
            }
            is HistoryRecordLoad.Offline -> {
                envelope = result.record
                offline = true
                error = result.reason
            }
            is HistoryRecordLoad.Failure -> error = result.reason
        }
        loading = false
    }
    Box(Modifier.fillMaxSize()) {
        JournalPage(
            state = JournalUiState(
                envelope = envelope,
                today = todayState.today,
                done = if (date == todayState.today) todayState.done else emptySet(),
                offline = offline,
                loading = loading,
                message = error,
            ),
            onRefresh = { reload++ },
            onToggleTask = onToggleTask,
            archived = true,
            headerDate = date,
        )
    }
}
