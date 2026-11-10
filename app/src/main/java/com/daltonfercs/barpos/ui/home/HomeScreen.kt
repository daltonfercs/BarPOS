package com.daltonfercs.barpos.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daltonfercs.barpos.data.BarRepository
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import com.daltonfercs.barpos.ui.theme.SuccessGreen
import com.daltonfercs.barpos.ui.theme.WarningRed
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
    val products by BarRepository.products.collectAsState()

    val libres = tables.count { it.estado == "Libre" }
    val ocupadas = tables.count { it.estado == "Ocupada" }
    val pendientesCocina = orders.filter { it.estadoCocina == "Pendiente" }.map { it.idPedido }.distinct().size
    val pendientesCobro = orders.filter { it.estadoPago == "Pendiente" && it.idPedido.isNotBlank() }.map { it.idPedido }.distinct().size
    val productosDisponibles = products.count { it.disponible && it.stock > 0 }
    val productosAgotados = products.count { !it.disponible || it.stock <= 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "BarPOS — Gestión Rápida",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            today,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    title = "Productos",
                    subtitle = "${products.size} ítems ($productosDisponibles disp.)",
                    icon = Icons.Filled.Inventory2,
                    color = Color(0xFF2196F3),
                    badgeCount = productosAgotados,
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
            item(span = { GridItemSpan(2) }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAbout() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Acerca de",
                                tint = Color(0xFF3F51B5),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Acerca de BarPOS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Desarrollador: Dalton CELADITA • DALTONFERCS.DEV@GMAIL.COM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Ver más",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
            .heightIn(min = 138.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Icon(icon, contentDescription = title, modifier = Modifier.size(40.dp), tint = color)
                if (badgeCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text("$badgeCount", color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
