package app.lexa.widget

/** Asks the home-screen widget to redraw: after an answer, at the end of a session, on a new day. */
interface WidgetRefresher {
    fun refresh()
}
