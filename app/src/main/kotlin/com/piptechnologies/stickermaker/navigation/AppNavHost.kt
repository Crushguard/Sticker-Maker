package com.piptechnologies.stickermaker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piptechnologies.stickermaker.feature.contact.ContactScreen
import com.piptechnologies.stickermaker.feature.create.details.CreatePackDetailsScreen
import com.piptechnologies.stickermaker.feature.create.editor.CreateEditorScreen
import com.piptechnologies.stickermaker.feature.create.import_.CreateImportScreen
import com.piptechnologies.stickermaker.feature.customize.CustomizationScreen
import com.piptechnologies.stickermaker.feature.detail.PackDetailScreen
import com.piptechnologies.stickermaker.feature.home.HomeScreen
import com.piptechnologies.stickermaker.feature.language.LanguageScreen
import com.piptechnologies.stickermaker.feature.mypacks.MyPacksScreen
import com.piptechnologies.stickermaker.feature.onboarding.OnboardingScreen
import com.piptechnologies.stickermaker.feature.saved.SavedScreen
import com.piptechnologies.stickermaker.feature.settings.SettingsScreen
import com.piptechnologies.stickermaker.feature.splash.SplashScreen

/** Route constants for every screen in the design. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val CUSTOMIZE = "customize"
    const val CUSTOMIZE_EDIT = "customizeEdit"
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

/**
 * The app's navigation graph, mirroring the prototype's stack semantics:
 * splash decides between onboarding and home; first-run customization
 * replaces the launch stack; a finished create flow collapses into My Packs.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val openPack: (String) -> Unit = { id -> navController.navigate(Routes.detail(id)) }

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
                onDone = {
                    navController.navigate(Routes.CUSTOMIZE) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CUSTOMIZE) {
            CustomizationScreen(
                isEdit = false,
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CUSTOMIZE) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CUSTOMIZE_EDIT) {
            CustomizationScreen(
                isEdit = true,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
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
                onContact = { navController.navigate(Routes.CONTACT) },
                onEditThemes = { navController.navigate(Routes.CUSTOMIZE_EDIT) }
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
