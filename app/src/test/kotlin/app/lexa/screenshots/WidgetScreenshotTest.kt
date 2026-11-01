package app.lexa.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lexa.domain.training.DailyProgress
import app.lexa.widget.WidgetState
import app.lexa.widget.widgetViews
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home-screen widget, drawn from the same RemoteViews the launcher gets: light and dark, 3×2 cells. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WidgetScreenshotTest {

    private fun snap(name: String, state: WidgetState, night: Boolean) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val density = context.resources.displayMetrics.density
        val host = FrameLayout(context)
        val view = widgetViews(context, state).apply(context, host)
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.measure(
            View.MeasureSpec.makeMeasureSpec((180 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((110 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        val bitmap = Bitmap.createBitmap(host.measuredWidth, host.measuredHeight, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bitmap))
        bitmap.captureRoboImage("src/test/snapshots/${name}_${if (night) "dark" else "light"}.png")
    }

    private val working = WidgetState(due = 12, daily = DailyProgress(done = 12, goal = 20))
    private val done = WidgetState(due = 0, daily = DailyProgress(done = 20, goal = 20))

    @Test fun widgetLight() = snap("widget", working, night = false)
    @Test fun widgetDark() = snap("widget", working, night = true)
    @Test fun widgetDoneLight() = snap("widget_done", done, night = false)
}
