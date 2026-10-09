package com.example.barpos.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.barpos.model.PedidoItem
import com.example.barpos.ui.theme.BarPOSTheme
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Mock ViewModel
class ReportsViewModel : ViewModel() {
    private val _ventas = MutableStateFlow<List<PedidoItem>>(emptyList())
    val ventas: StateFlow<List<PedidoItem>> = _ventas.asStateFlow()

    init {
        // Mock data
        _ventas.value = listOf(
            PedidoItem("T-001", "123", "Comida", "Hamburguesa", 2, 25.0, 50.0, "Mesa", "Yape", "Listo"),
            PedidoItem("T-001", "123", "Bebidas", "Pisco", 1, 20.0, 20.0, "Mesa", "Yape", "Listo"),
            PedidoItem("T-002", "124", "Comida", "Alitas", 1, 22.0, 22.0, "Llevar", "Efectivo", "Listo")
        )
    }
    
    fun sync() {
        // En un caso real, llamaría a GoogleSheetsApi.obtenerVentasHoy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ventas by viewModel.ventas.collectAsState()
    
    val totalVentas = ventas.sumOf { it.totalItem }
    val totalComidas = ventas.filter { it.categoria == "Comida" }.sumOf { it.totalItem }
    val totalBebidas = ventas.filter { it.categoria == "Bebidas" }.sumOf { it.totalItem }
    
    val totalYape = ventas.filter { it.metodoPago == "Yape" }.sumOf { it.totalItem }
    val totalEfectivo = ventas.filter { it.metodoPago == "Efectivo" }.sumOf { it.totalItem }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte Diario") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { viewModel.sync() }) {
                        Text("Sync", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ReportCard(title = "Total Ventas del Día", amount = totalVentas, isMain = true)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReportCard(title = "Comidas", amount = totalComidas, modifier = Modifier.weight(1f))
                    ReportCard(title = "Bebidas", amount = totalBebidas, modifier = Modifier.weight(1f))
                }
            }
            item {
                Text("Desglose por Pago", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReportCard(title = "Yape", amount = totalYape, modifier = Modifier.weight(1f))
                    ReportCard(title = "Efectivo", amount = totalEfectivo, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ReportCard(title: String, amount: Double, modifier: Modifier = Modifier, isMain: Boolean = false) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = if (isMain) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "S/ ${"%.2f".format(amount)}",
                style = if (isMain) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview
@Composable
fun ReportsScreenPreview() {
    BarPOSTheme {
        ReportsScreen()
    }
}
