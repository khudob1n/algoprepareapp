package com.algoprep.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.algoprep.app.R

/** "2h 15m" / "2ч 15мин" using string resources (no hard-coded units). */
@Composable
fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> stringResource(R.string.duration_h_m, h, m)
        h > 0 -> stringResource(R.string.duration_h, h)
        else -> stringResource(R.string.duration_m, m)
    }
}
