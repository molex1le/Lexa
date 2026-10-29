package app.lexa.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.lexa.MainActivity
import app.lexa.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/** Posts the review reminder; the channel is created at app start. */
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Russian plural rules whatever the device language, like `ui.components.pluralStringResource`. */
    fun text(dueCount: Int): String {
        val russian = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
        return context.createConfigurationContext(russian).resources
            .getQuantityString(R.plurals.reminder_due, dueCount, dueCount)
    }

    fun show(dueCount: Int) {
        val manager = NotificationManagerCompat.from(context)
        // Permission revoked or notifications blocked in system settings: nothing to post.
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            MainActivity.openTrainingIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text(dueCount))
            .setContentText(context.getString(R.string.reminder_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the post.
        }
    }

    companion object {
        const val CHANNEL_ID = "review-reminder"
        const val NOTIFICATION_ID = 1

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

}
