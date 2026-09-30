package com.algoprep.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.algoprep.app.R

/** "2h 15m" / "2ч 15мин" using string resources (no hard-coded units). */
fun Context.durationText(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> getString(R.string.duration_h_m, h, m)
        h > 0 -> getString(R.string.duration_h, h)
        else -> getString(R.string.duration_m, m)
    }
}

@Composable
fun formatDuration(totalMinutes: Int): String = LocalContext.current.durationText(totalMinutes)
