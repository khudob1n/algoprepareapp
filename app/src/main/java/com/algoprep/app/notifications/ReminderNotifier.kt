package com.algoprep.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.algoprep.app.MainActivity
import com.algoprep.app.R
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.reminders.ReminderContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatter: ReminderTextFormatter,
) {
    /** False when the user denied the permission or turned notifications off for the app. */
    fun canPost(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(kind: ReminderKind, content: ReminderContent) {
        post(
            id = NOTIFICATION_ID_BASE + kind.ordinal,
            channel = NotificationChannels.idFor(kind),
            title = formatter.title(content),
            text = formatter.text(content),
        )
    }

    fun showTest() {
        post(
            id = NOTIFICATION_ID_TEST,
            channel = NotificationChannels.PLAN,
            title = context.getString(R.string.app_name),
            text = context.getString(R.string.reminder_test_text),
        )
    }

    @SuppressLint("MissingPermission") // guarded by canPost()
    private fun post(id: Int, channel: String, title: String, text: String) {
        if (!canPost()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val NOTIFICATION_ID_BASE = 1000
        const val NOTIFICATION_ID_TEST = 999
    }
}
