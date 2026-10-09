package com.example.barpos.ui.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.example.barpos.model.PedidoItem
import com.example.barpos.ui.theme.BarPOSTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Mock ViewModel
class KitchenViewModel : ViewModel() {
    private val _pedidos = MutableStateFlow<List<PedidoItem>>(emptyList())
    val pedidos: StateFlow<List<PedidoItem>> = _pedidos.asStateFlow()

    init {
        // Formato ISO simplificado para mock
        val ahora = System.currentTimeMillis()
        val hace10min = ahora - (10 * 60 * 1000)
        val hace16min = ahora - (16 * 60 * 1000)

        _pedidos.value = listOf(
            PedidoItem("T-001", hace10min.toString(), "Comida", "Hamburguesa Clásica", 2, 25.0, 50.0, "En Mesa 1", "Yape", "Pendiente"),
            PedidoItem("T-001", hace10min.toString(), "Bebidas", "Pisco Sour", 1, 20.0, 20.0, "En Mesa 1", "Yape", "Pendiente"),
            PedidoItem("T-002", hace16min.toString(), "Comida", "Alitas BBQ", 1, 22.0, 22.0, "Para Llevar", "Efectivo", "Pendiente")
        )
    }

    fun marcarComoListo(idPedido: String) {
        _pedidos.value = _pedidos.value.filter { it.idPedido != idPedido }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(viewModel: KitchenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val pedidos by viewModel.pedidos.collectAsState()
    val pedidosAgrupados = pedidos.groupBy { it.idPedido }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KDS - Cocina") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(pedidosAgrupados.keys.toList()) { idPedido ->
                val itemsDelPedido = pedidosAgrupados[idPedido] ?: emptyList()
                TicketCocina(
                    idPedido = idPedido,
                    items = itemsDelPedido,
                    onDespachar = { viewModel.marcarComoListo(idPedido) }
                )
            }
        }
    }
}

@Composable
fun TicketCocina(idPedido: String, items: List<PedidoItem>, onDespachar: () -> Unit) {
    val primerItem = items.first()
    val tiempoInicial = primerItem.fechaHora.toLongOrNull() ?: System.currentTimeMillis()
    var tiempoTranscurrido by remember { mutableStateOf(System.currentTimeMillis() - tiempoInicial) }
    
    val quinceMinutosMs = 15 * 60 * 1000L
    val estaAtrasado = tiempoTranscurrido > quinceMinutosMs

    LaunchedEffect(key1 = tiempoInicial) {
        while (true) {
            delay(1000L)
            tiempoTranscurrido = System.currentTimeMillis() - tiempoInicial
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#$idPedido - ${primerItem.tipoOrden}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                // Temporizador
                Box(
                    modifier = Modifier
                        .background(
                            color = if (estaAtrasado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val minutos = (tiempoTranscurrido / 1000) / 60
                    val segundos = (tiempoTranscurrido / 1000) % 60
                    Text(
                        text = String.format("%02d:%02d", minutos, segundos),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Spacer(modifier = Modifier.height(8.dp))
            
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${item.cantidad}x ${item.producto}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDespachar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Marcar como Listo", color = Color.White)
            }
        }
    }
}

@Preview
@Composable
fun KitchenScreenPreview() {
    BarPOSTheme {
        KitchenScreen()
    }
}
