package com.algoprep.app.ui.screens.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.algoprep.app.R
import com.algoprep.app.ui.screens.importer.ImportTab

private enum class TasksTab(val labelRes: Int) {
    BANK(R.string.tasks_tab_bank),
    IMPORT(R.string.tasks_tab_import),
    DATA(R.string.tasks_tab_data),
}

/** Interview Task Bank: browse the bank, import material, and analyse the imported set. */
@Composable
fun TasksScreen(onOpenTask: (Long) -> Unit, onOpenReview: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            TasksTab.entries.forEachIndexed { index, t ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(t.labelRes)) })
            }
        }
        when (TasksTab.entries[tab]) {
            TasksTab.BANK -> BankTab(onOpenTask = onOpenTask, onGoToImport = { tab = TasksTab.IMPORT.ordinal })
            TasksTab.IMPORT -> ImportTab(onOpenReview = onOpenReview)
            TasksTab.DATA -> InterviewDataTab(onOpenTask = onOpenTask, onGoToImport = { tab = TasksTab.IMPORT.ordinal })
        }
    }
}
