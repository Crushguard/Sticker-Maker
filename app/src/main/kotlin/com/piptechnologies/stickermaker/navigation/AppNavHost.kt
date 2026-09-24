package com.piptechnologies.stickermaker.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Route constants for every screen in the design. Later phases register the
 * real destinations; P0 ships a single placeholder on [Routes.HOME].
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val CUSTOMIZE = "customize"
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

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            // Placeholder — real screens land in P3.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Love Stickers",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
