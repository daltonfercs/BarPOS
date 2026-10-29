package com.daltonfercs.barpos.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daltonfercs.barpos.data.BarRepository
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToActiveOrders: () -> Unit = {},
    onNavigateToTables: () -> Unit = {},
    onNavigateToCatalog: () -> Unit,
    onNavigateToKitchen: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToAbout: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("es", "ES"))
    val today = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }

    val tables by BarRepository.tables.collectAsState()
    val orders by BarRepository.orders.collectAsState()

    val libres = tables.count { it.estado == "Libre" }
    val ocupadas = tables.count { it.estado == "Ocupada" }
    val pendientesCocina = orders.filter { it.estadoCocina == "Pendiente" }.map { it.idPedido }.distinct().size
    val pendientesCobro = orders.filter { it.estadoPago == "Pendiente" && it.idPedido.isNotBlank() }.map { it.idPedido }.distinct().size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BarPOS — Gestión Rápida", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(today, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Room Local + Cloud", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = onNavigateToAbout) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Acerca de",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                HeroCard(
                    title = "Salón & Mesas",
                    subtitle = "$libres Libres / $ocupadas Ocupadas",
                    icon = Icons.Filled.TableRestaurant,
                    color = Color(0xFF4CAF50),
                    badgeCount = ocupadas,
                    onClick = onNavigateToTables
                )
            }
            item {
                HeroCard(
                    title = "Envíos / Delivery",
                    subtitle = "Toma de pedidos y despacho",
                    icon = Icons.Filled.LocalShipping,
                    color = Color(0xFFFF9800),
                    onClick = onNavigateToCatalog
                )
            }
            item {
                HeroCard(
                    title = "Órdenes Stand By",
                    subtitle = "$pendientesCobro pendientes de cobro",
                    icon = Icons.Filled.ReceiptLong,
                    color = Color(0xFF00BCD4),
                    badgeCount = pendientesCobro,
                    onClick = onNavigateToActiveOrders
                )
            }
            item {
                HeroCard(
                    title = "Cola de Cocina",
                    subtitle = "$pendientesCocina pedidos pendientes",
                    icon = Icons.Filled.Warning,
                    color = Color(0xFFE53935),
                    badgeCount = pendientesCocina,
                    onClick = onNavigateToKitchen
                )
            }
            item {
                HeroCard(
                    title = "Gestión Productos",
                    subtitle = "Stock, Menú y Precios",
                    icon = Icons.Filled.Menu,
                    color = Color(0xFF2196F3),
                    onClick = onNavigateToProducts
                )
            }
            item {
                HeroCard(
                    title = "Cierre de Caja",
                    subtitle = "Reportes y Cuadre",
                    icon = Icons.Filled.DateRange,
                    color = Color(0xFF9C27B0),
                    onClick = onNavigateToReports
                )
            }
            item {
                HeroCard(
                    title = "Acerca de",
                    subtitle = "Dalton CELADITA • Info",
                    icon = Icons.Filled.Info,
                    color = Color(0xFF3F51B5),
                    onClick = onNavigateToAbout
                )
            }
        }
    }
}

@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Icon(icon, contentDescription = title, modifier = Modifier.size(44.dp), tint = color)
                if (badgeCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text("$badgeCount", color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    BarPOSTheme {
        HomeScreen({}, {}, {}, {}, {}, {})
    }
}
