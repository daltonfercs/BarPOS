package com.daltonfercs.barpos.ui.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daltonfercs.barpos.data.BarRepository
import com.daltonfercs.barpos.model.PedidoItem
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KitchenViewModel : ViewModel() {
    private val _pedidos = MutableStateFlow<List<PedidoItem>>(emptyList())
    val pedidos: StateFlow<List<PedidoItem>> = _pedidos.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Observar repositorio reactivamente
        viewModelScope.launch {
            BarRepository.orders.collect { allOrders ->
                _pedidos.value = allOrders.filter { it.estadoCocina == "Pendiente" }
            }
        }
        cargarPedidosCocina()
    }

    /**
     * Consulta el endpoint GET /exec?tipo=pedidos&estado=Pendiente
     */
    fun cargarPedidosCocina() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            val orders = BarRepository.fetchKitchenOrders()
            _pedidos.value = orders.filter { it.estadoCocina == "Pendiente" }
            _isRefreshing.value = false
        }
    }

    /**
     * Lanza corrutina que hace POST con la acción "marcar_listo" y el id_pedido respectivo.
     */
    fun marcarListo(idPedido: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Actualización optimista inmediata
            _pedidos.value = _pedidos.value.filter { it.idPedido != idPedido }
            BarRepository.marcarPedidoListo(idPedido)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(viewModel: KitchenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val pedidos by viewModel.pedidos.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pedidosAgrupados = pedidos.groupBy { it.idPedido }

    // Refresco automático cada 10 segundos
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.cargarPedidosCocina()
            delay(10000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cola de Cocina (KDS)", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("${pedidosAgrupados.size} pendientes", color = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.cargarPedidosCocina() }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar Cola")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (pedidosAgrupados.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👨‍🍳", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "¡Cola de cocina limpia!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "No hay pedidos pendientes por despachar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(pedidosAgrupados.keys.toList(), key = { it }) { idPedido ->
                    val itemsDelPedido = pedidosAgrupados[idPedido] ?: emptyList()
                    TicketCocinaCard(
                        idPedido = idPedido,
                        items = itemsDelPedido,
                        onMarcarListo = { viewModel.marcarListo(idPedido) }
                    )
                }
            }
        }
    }
}

@Composable
fun TicketCocinaCard(idPedido: String, items: List<PedidoItem>, onMarcarListo: () -> Unit) {
    val primerItem = items.firstOrNull() ?: PedidoItem()
    val inicio = remember(idPedido, primerItem.fechaHora) {
        BarRepository.getOrderStartTimeMillis(idPedido, primerItem.fechaHora)
    }
    var tiempoTranscurrido by remember(idPedido) {
        mutableStateOf((System.currentTimeMillis() - inicio).coerceAtLeast(0L))
    }

    LaunchedEffect(idPedido, inicio) {
        while (true) {
            tiempoTranscurrido = (System.currentTimeMillis() - inicio).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    val quinceMinutosMs = 15 * 60 * 1000L
    val estaAtrasado = tiempoTranscurrido > quinceMinutosMs

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabecera: ID Pedido, Cronómetro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "#$idPedido",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = primerItem.direccionOCliente,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Modalidad: ${primerItem.tipoOrden}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Temporizador
                Box(
                    modifier = Modifier
                        .background(
                            color = if (estaAtrasado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val minutos = (tiempoTranscurrido / 1000) / 60
                    val segundos = (tiempoTranscurrido / 1000) % 60
                    Text(
                        text = String.format("%02d:%02d", minutos, segundos),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Spacer(modifier = Modifier.height(12.dp))

            // Lista de productos a preparar (excluyendo el recargo de empaque ya que va listo)
            val productosAPreparar = items.filter { it.categoria != "Empaque" }

            productosAPreparar.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "${item.cantidad}x",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = item.producto,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = item.categoria,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón destacado "Marcar Listo"
            Button(
                onClick = onMarcarListo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Listo", modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Marcar Listo", color = Color.White, fontWeight = FontWeight.Bold)
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
