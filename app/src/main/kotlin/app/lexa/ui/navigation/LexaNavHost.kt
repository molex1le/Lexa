package app.lexa.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lexa.ui.history.HistoryRoute as HistoryScreenRoute
import app.lexa.ui.home.HomeRoute as HomeScreenRoute
import app.lexa.ui.lists.ListsRoute as ListsScreenRoute
import app.lexa.ui.settings.SettingsRoute as SettingsScreenRoute
import app.lexa.ui.stats.StatsRoute as StatsScreenRoute
import app.lexa.ui.theme.LexaTheme
import app.lexa.ui.translator.TranslatorRoute as TranslatorScreenRoute
import app.lexa.ui.translator.TranslatorViewModel
import app.lexa.ui.word.WordRoute as WordScreenRoute

private fun NavDestination.topLevel(): TopLevel? = hierarchy.firstNotNullOfOrNull { dest ->
    when {
        dest.hasRoute(HomeRoute::class) -> TopLevel.TRAINING
        dest.hasRoute(TranslatorRoute::class) -> TopLevel.TRANSLATOR
        dest.hasRoute(ListsRoute::class) -> TopLevel.WORDS
        dest.hasRoute(StatsRoute::class) -> TopLevel.STATS
        else -> null
    }
}

private fun TopLevel.route(): Any = when (this) {
    TopLevel.TRAINING -> HomeRoute
    TopLevel.TRANSLATOR -> TranslatorRoute()
    TopLevel.WORDS -> ListsRoute()
    TopLevel.STATS -> StatsRoute
}

private fun NavHostController.navigateTopLevel(item: TopLevel) {
    navigate(item.route()) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun LexaNavHost(navController: NavHostController = rememberNavController()) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.topLevel()
    Scaffold(
        containerColor = LexaTheme.colors.paper,
        bottomBar = {
            if (current != null) {
                LexaBottomBar(current = current, onSelect = navController::navigateTopLevel)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
        ) {
            composable<HomeRoute> {
                HomeScreenRoute(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onTranslate = { navController.navigateTopLevel(TopLevel.TRANSLATOR) },
                )
            }
            composable<TranslatorRoute> {
                TranslatorScreenRoute(onOpenHistory = { navController.navigate(HistoryRoute) })
            }
            composable<ListsRoute> {
                ListsScreenRoute(
                    onOpenWord = { navController.navigate(WordRoute(it)) },
                    onTranslate = { navController.navigateTopLevel(TopLevel.TRANSLATOR) },
                )
            }
            composable<StatsRoute> { StatsScreenRoute() }
            composable<WordRoute> { WordScreenRoute(onBack = navController::popBackStack) }
            composable<HistoryRoute> {
                HistoryScreenRoute(
                    onBack = navController::popBackStack,
                    onTranslate = { query ->
                        // The translator below picks the query up from its SavedStateHandle and opens it (from cache).
                        if (query != null) {
                            navController.previousBackStackEntry?.savedStateHandle?.set(TranslatorViewModel.PICKED_QUERY, query)
                        }
                        navController.popBackStack()
                    },
                )
            }
            composable<SettingsRoute> { SettingsScreenRoute(onBack = navController::popBackStack) }
        }
    }
}
