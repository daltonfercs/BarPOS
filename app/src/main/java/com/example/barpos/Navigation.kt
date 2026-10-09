package com.example.barpos

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.barpos.ui.catalog.CatalogScreen
import com.example.barpos.ui.kitchen.KitchenScreen
import com.example.barpos.ui.reports.ReportsScreen
import com.example.barpos.ui.roles.RoleSelectionScreen
import com.example.barpos.ui.summary.SummaryScreen

@Composable
fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "roles") {
        composable("roles") {
            RoleSelectionScreen(
                onNavigateToCatalog = { navController.navigate("catalog") },
                onNavigateToKitchen = { navController.navigate("kitchen") },
                onNavigateToReports = { navController.navigate("reports") }
            )
        }
        composable("catalog") {
            CatalogScreen(
                onNavigateToCheckout = { navController.navigate("summary") }
            )
        }
        composable("summary") {
            SummaryScreen(
                onConfirm = { 
                    // Regresar al catálogo después de pagar y limpiar (mock)
                    navController.popBackStack("catalog", inclusive = false)
                }
            )
        }
        composable("kitchen") {
            KitchenScreen()
        }
        composable("reports") {
            ReportsScreen()
        }
    }
}
