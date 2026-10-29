package com.daltonfercs.barpos

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daltonfercs.barpos.data.BarRepository
import com.daltonfercs.barpos.ui.catalog.CatalogScreen
import com.daltonfercs.barpos.ui.home.HomeScreen
import com.daltonfercs.barpos.ui.kitchen.KitchenScreen
import com.daltonfercs.barpos.ui.products.ProductsScreen
import com.daltonfercs.barpos.ui.reports.ReportsScreen
import com.daltonfercs.barpos.ui.summary.SummaryScreen
import com.daltonfercs.barpos.ui.tables.TablesScreen

import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TableRestaurant
import com.daltonfercs.barpos.ui.about.AboutScreen
import com.daltonfercs.barpos.ui.orders.ActiveOrdersScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Filled.Home)
    object Tables : Screen("tables", "Mesas", Icons.Filled.TableRestaurant)
    object Catalog : Screen("catalog", "Envíos", Icons.Filled.LocalShipping)
    object ActiveOrders : Screen("standby", "Stand By", Icons.Filled.ReceiptLong)
    object Kitchen : Screen("kitchen", "Cocina", Icons.Filled.Warning)
    object Products : Screen("products", "Stock", Icons.Filled.Menu)
    object Reports : Screen("reports", "Caja", Icons.Filled.DateRange)
    object About : Screen("about", "Acerca de", Icons.Filled.Info)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Tables,
    Screen.Catalog,
    Screen.ActiveOrders,
    Screen.Kitchen,
    Screen.Reports
)

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val orders by BarRepository.orders.collectAsState()
    val tables by BarRepository.tables.collectAsState()
    val mesasOcupadas = tables.count { it.estado == "Ocupada" }
    val pendientesCocina = orders.filter { it.estadoCocina == "Pendiente" }.map { it.idPedido }.distinct().size
    val pendientesCobro = orders.filter { it.estadoPago == "Pendiente" && it.idPedido.isNotBlank() }.map { it.idPedido }.distinct().size

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            NavigationBar {
                bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route ||
                            (screen == Screen.Catalog && currentRoute?.startsWith(Screen.Catalog.route) == true)

                    NavigationBarItem(
                        icon = {
                            when {
                                screen.route == "tables" && mesasOcupadas > 0 -> {
                                    BadgedBox(badge = { Badge { Text("$mesasOcupadas") } }) {
                                        Icon(screen.icon, contentDescription = screen.title)
                                    }
                                }
                                screen.route == "kitchen" && pendientesCocina > 0 -> {
                                    BadgedBox(badge = { Badge { Text("$pendientesCocina") } }) {
                                        Icon(screen.icon, contentDescription = screen.title)
                                    }
                                }
                                screen.route == "standby" && pendientesCobro > 0 -> {
                                    BadgedBox(badge = { Badge { Text("$pendientesCobro") } }) {
                                        Icon(screen.icon, contentDescription = screen.title)
                                    }
                                }
                                else -> {
                                    Icon(screen.icon, contentDescription = screen.title)
                                }
                            }
                        },
                        label = { Text(screen.title) },
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToActiveOrders = {
                        navController.navigate(Screen.ActiveOrders.route) { launchSingleTop = true }
                    },
                    onNavigateToTables = {
                        navController.navigate(Screen.Tables.route) { launchSingleTop = true }
                    },
                    onNavigateToCatalog = {
                        navController.navigate(Screen.Catalog.route) { launchSingleTop = true }
                    },
                    onNavigateToKitchen = {
                        navController.navigate(Screen.Kitchen.route) { launchSingleTop = true }
                    },
                    onNavigateToProducts = {
                        navController.navigate(Screen.Products.route) { launchSingleTop = true }
                    },
                    onNavigateToReports = {
                        navController.navigate(Screen.Reports.route) { launchSingleTop = true }
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.About.route) {
                AboutScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ActiveOrders.route) {
                ActiveOrdersScreen()
            }
            composable(Screen.Tables.route) {
                TablesScreen(
                    onTableSelected = { mesa ->
                        navController.navigate("${Screen.Catalog.route}?idMesa=${mesa.idMesa}")
                    }
                )
            }
            composable(
                route = "${Screen.Catalog.route}?idMesa={idMesa}",
                arguments = listOf(androidx.navigation.navArgument("idMesa") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val idMesa = backStackEntry.arguments?.getString("idMesa")
                CatalogScreen(
                    idMesaInicial = idMesa,
                    onNavigateToCheckout = {
                        navController.navigate(Screen.ActiveOrders.route) { launchSingleTop = true }
                    }
                )
            }
            composable("summary") {
                SummaryScreen(
                    onConfirm = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Kitchen.route) {
                KitchenScreen()
            }
            composable(Screen.Products.route) {
                ProductsScreen()
            }
            composable(Screen.Reports.route) {
                ReportsScreen()
            }
        }
    }
}
