package app.lexa.ui.navigation

import kotlinx.serialization.Serializable

/** Tabs of the «Слова» screen. */
@Serializable
enum class ListTab { NEW, FAVORITES, LEARNED }

/** «Тренировка» tab: what is due today, and the session entry (phase 3). */
@Serializable data object HomeRoute

@Serializable data class TranslatorRoute(val query: String? = null)

@Serializable data class ListsRoute(val tab: ListTab = ListTab.NEW)

@Serializable data object StatsRoute

@Serializable data class WordRoute(val wordId: Long)

@Serializable data object HistoryRoute

@Serializable data object SettingsRoute
