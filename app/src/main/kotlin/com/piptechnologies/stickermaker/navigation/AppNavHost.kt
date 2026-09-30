package com.piptechnologies.stickermaker.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import com.piptechnologies.stickermaker.feature.contact.ContactScreen
import com.piptechnologies.stickermaker.feature.create.details.CreatePackDetailsScreen
import com.piptechnologies.stickermaker.feature.create.editor.CreateEditorScreen
import com.piptechnologies.stickermaker.feature.create.import_.CreateImportScreen
import com.piptechnologies.stickermaker.feature.detail.PackDetailScreen
import com.piptechnologies.stickermaker.feature.home.HomeScreen
import com.piptechnologies.stickermaker.feature.language.LanguageScreen
import com.piptechnologies.stickermaker.feature.mypacks.MyPacksScreen
import com.piptechnologies.stickermaker.feature.namepack.NamePackScreen
import com.piptechnologies.stickermaker.feature.namepack.blockTaps
import com.piptechnologies.stickermaker.feature.onboarding.OnboardingScreen
import com.piptechnologies.stickermaker.feature.rating.RatingPromptHost
import com.piptechnologies.stickermaker.feature.saved.SavedScreen
import com.piptechnologies.stickermaker.feature.settings.SettingsScreen
import com.piptechnologies.stickermaker.feature.splash.SplashScreen

/** Route constants for every screen in the design. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val NAME_PACK = "namePack"
    const val HOME = "home"
    const val DETAIL = "detail/{packId}"
    const val CREATE = "create"
    const val EDITOR = "editor"
    const val PACK_DETAILS = "packDetails"
    const val MY_PACKS = "myPacks"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val LANGUAGE = "language"
    const val CONTACT = "contact"

    const val ARG_PACK_ID = "packId"

    /** Concrete path for navigating to [DETAIL]. */
    fun detail(packId: String): String = "detail/$packId"
}

/** Screens where a finished add leaves the user browsing: the rating prompt's natural pauses. */
private val RATING_PAUSES = setOf(Routes.HOME, Routes.DETAIL, Routes.SAVED, Routes.MY_PACKS)

/**
 * The app's navigation graph, mirroring the prototype's stack semantics:
 * splash decides between onboarding and home; the intro hands over to the
 * Custom Stickers flow, which ends on Home; a finished create flow collapses
 * into My Packs.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val openPack: (String) -> Unit = { id -> navController.navigate(Routes.detail(id)) }

    // One screen_view per destination (the route pattern, never a pack id).
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let(AppAnalytics::logScreen)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // The rating prompt rises after a pack WhatsApp confirmed, once the user is browsing again.
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    RatingPromptHost(atNaturalPause = route in RATING_PAUSES)

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = { onboarded ->
                    navController.navigate(if (onboarded) Routes.HOME else Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                // The intro stays underneath: Back on "Your name" returns to it.
                onDone = { navController.navigate(Routes.NAME_PACK) { launchSingleTop = true } }
            )
        }

        composable(Routes.NAME_PACK) {
            // The route fades in over the intro, whose Skip sits where the name steps' Skip does:
            // taps during the fade were aimed at the intro.
            Box(Modifier.fillMaxSize().blockTaps(transition.currentState != transition.targetState)) {
                NamePackScreen(
                    // First run is over: Home becomes the only screen on the stack.
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    onLeave = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenPack = openPack,
                onCreate = { navController.navigate(Routes.CREATE) },
                onMyPacks = { navController.navigate(Routes.MY_PACKS) { launchSingleTop = true } },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_PACK_ID) { type = NavType.StringType })
        ) { entry ->
            PackDetailScreen(
                packId = entry.arguments?.getString(Routes.ARG_PACK_ID).orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MY_PACKS) {
            MyPacksScreen(
                onBack = { navController.popBackStack() },
                onOpenPack = openPack,
                onCreate = { navController.navigate(Routes.CREATE) },
                onOpenSaved = { navController.navigate(Routes.SAVED) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SAVED) {
            SavedScreen(
                onBack = { navController.popBackStack() },
                onOpenPack = openPack
            )
        }

        composable(Routes.CREATE) {
            CreateImportScreen(
                onBack = { navController.popBackStack() },
                onPicked = { navController.navigate(Routes.EDITOR) }
            )
        }

        composable(Routes.EDITOR) {
            CreateEditorScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.navigate(Routes.PACK_DETAILS) }
            )
        }

        composable(Routes.PACK_DETAILS) {
            CreatePackDetailsScreen(
                onBack = { navController.popBackStack() },
                onExported = {
                    // Prototype resets the create stack into My Packs.
                    navController.navigate(Routes.MY_PACKS) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLanguage = { navController.navigate(Routes.LANGUAGE) },
                onContact = { navController.navigate(Routes.CONTACT) }
            )
        }

        composable(Routes.LANGUAGE) {
            LanguageScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CONTACT) {
            ContactScreen(onBack = { navController.popBackStack() })
        }
    }
}
