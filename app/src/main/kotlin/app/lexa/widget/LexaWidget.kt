package app.lexa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.lexa.MainActivity
import app.lexa.R
import app.lexa.di.ApplicationScope
import app.lexa.domain.repository.SettingsRepository
import app.lexa.domain.repository.WordRepository
import app.lexa.domain.time.TimeProvider
import app.lexa.domain.training.DailyProgress
import app.lexa.domain.training.DailyProgressRepository
import app.lexa.domain.training.progressOn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the widget shows: how many words wait, and today's answers against the goal. */
data class WidgetState(val due: Int, val daily: DailyProgress)

/** The widget's views for [state]. A pure function of its input, so a test can draw it. */
fun widgetViews(context: Context, state: WidgetState): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_lexa)
    views.setTextViewText(R.id.widget_due, state.due.toString())
    views.setViewVisibility(R.id.widget_marker, if (state.due > 0) View.VISIBLE else View.INVISIBLE)
    // One short line: the full bar already says the goal is reached.
    val goal = context.getString(R.string.goal_today, state.daily.done, state.daily.goal)
    views.setTextViewText(R.id.widget_goal, goal)
    views.setProgressBar(R.id.widget_bar, 100, (state.daily.fraction * 100).toInt(), false)
    views.setContentDescription(R.id.widget_root, context.getString(R.string.widget_description_a11y, state.due, goal))
    views.setOnClickPendingIntent(
        R.id.widget_root,
        PendingIntent.getActivity(
            context, 0, MainActivity.openTrainingIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )
    return views
}

/** The home-screen widget: «Повторить сегодня 12» and the day's goal. Tap opens the training tab. */
@AndroidEntryPoint
class LexaWidgetProvider : AppWidgetProvider() {
    @Inject lateinit var words: WordRepository
    @Inject lateinit var progress: DailyProgressRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                val state = WidgetState(
                    due = words.observeDueCount(time.nowMillis()).first(),
                    daily = progress.count.first().progressOn(time.today().toEpochDay(), settings.settings.first().dailyGoal),
                )
                ids.forEach { manager.updateAppWidget(it, widgetViews(context, state)) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Redraws the widget by sending it the system's update broadcast; does nothing when none is placed. */
class AppWidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) : WidgetRefresher {
    override fun refresh() {
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LexaWidgetProvider::class.java))
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setClass(context, LexaWidgetProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }
}
