package br.com.boddenb.comidinhas.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.ui.screen.details.DetailsScreen
import br.com.boddenb.comidinhas.ui.screen.home.HomeScreen
import br.com.boddenb.comidinhas.ui.screen.home.HomeViewModel
import br.com.boddenb.comidinhas.ui.screen.eatout.ComerForaRoute
import br.com.boddenb.comidinhas.ui.screen.eatout.EatOutDetailsRoute
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNav(
    openAiClient: OpenAiClient
) {
    val nav = rememberAnimatedNavController()
    val vm: AppViewModel = viewModel()

    val homeViewModel: HomeViewModel = hiltViewModel()

    // Animações suaves de transição
    val enterTransition = slideInHorizontally(
        initialOffsetX = { 300 },
        animationSpec = tween(300)
    ) + fadeIn(animationSpec = tween(300))

    val exitTransition = slideOutHorizontally(
        targetOffsetX = { -300 },
        animationSpec = tween(300)
    ) + fadeOut(animationSpec = tween(300))

    val popEnterTransition = slideInHorizontally(
        initialOffsetX = { -300 },
        animationSpec = tween(300)
    ) + fadeIn(animationSpec = tween(300))

    val popExitTransition = slideOutHorizontally(
        targetOffsetX = { 300 },
        animationSpec = tween(300)
    ) + fadeOut(animationSpec = tween(300))

    AnimatedNavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = homeViewModel,
                onRecipeClick = {
                    vm.onRecipeSelected(it)
                    nav.navigate("details")
                },
                onOpenEatOut = { foodQuery: String ->
                    if (foodQuery.isNotBlank()) {
                        val encoded = java.net.URLEncoder.encode(foodQuery, "UTF-8")
                        nav.navigate("eatOut?foodQuery=$encoded")
                    } else {
                        nav.navigate("eatOut")
                    }
                }
            )
        }
        composable("details") {
            DetailsScreen(
                recipe = vm.selectedRecipe,
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = "eatOut?foodQuery={foodQuery}",
            arguments = listOf(navArgument("foodQuery") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val initialFoodQuery = backStackEntry.arguments?.getString("foodQuery") ?: ""
            ComerForaRoute(
                onBack = { nav.popBackStack() },
                onOpenDetails = { id -> nav.navigate("eatOutDetails/$id") },
                initialFoodQuery = initialFoodQuery
            )
        }
        composable(
            route = "eatOutDetails/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()

            val parentEntry = remember(backStackEntry) {
                nav.getBackStackEntry("eatOut")
            }
            EatOutDetailsRoute(
                id = id,
                onBack = { nav.popBackStack() },
                vm = hiltViewModel(parentEntry)
            )
        }
    }
}
