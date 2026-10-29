package com.daltonfercs.barpos.ui.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daltonfercs.barpos.data.BarRepository
import com.daltonfercs.barpos.model.PedidoItem
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import com.daltonfercs.barpos.ui.theme.SuccessGreen
import com.daltonfercs.barpos.ui.theme.WarningRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class StandByOrderGroup(
    val idPedido: String,
    val fechaHora: String,
    val cliente: String,
    val tipoOrden: String,
    val estadoCocina: String,
    val items: List<PedidoItem>,
    val cantidadTapers: Int = 0,
    val subtotalSinTapers: Double = 0.0,
    val totalAcumulado: Double
)

class ActiveOrdersViewModel : ViewModel() {
    val allOrders: StateFlow<List<PedidoItem>> = BarRepository.orders

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        refreshStandByOrders()
        // Polling periódico cada 12 segundos para mantener sincronizadas las órdenes en Stand By
        viewModelScope.launch {
            while (true) {
                delay(12_000L)
                BarRepository.fetchAllOrders()
            }
        }
    }

    fun refreshStandByOrders() {
        _isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            BarRepository.fetchAllOrders()
            _isRefreshing.value = false
        }
    }

    fun actualizarTapers(idPedido: String, nuevaCantidad: Int) {
        BarRepository.actualizarTapersOrden(idPedido, nuevaCantidad)
    }

    fun cobrarOrden(
        idPedido: String,
        metodoPago: String,
        montoEfectivo: Double,
        montoYape: Double,
        cantidadTapers: Int? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            BarRepository.cobrarOrden(
                idPedido = idPedido,
                metodoPago = metodoPago,
                montoEfectivo = montoEfectivo,
                montoYape = montoYape,
                cantidadTapers = cantidadTapers
            )
            _feedbackMessage.value = "✅ Pedido #$idPedido cobrado con éxito ($metodoPago)"
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveOrdersScreen(
    viewModel: ActiveOrdersViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val rawOrders by viewModel.allOrders.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedOrderIdForPayment by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    // Filtrar localmente aquellos con Estado_Pago == "Pendiente" y agrupar por ID_Pedido
    val standByGroups = remember(rawOrders) {
        rawOrders
            .filter { it.estadoPago.equals("Pendiente", ignoreCase = true) && it.idPedido.isNotBlank() }
            .groupBy { it.idPedido }
            .map { (idPedido, items) ->
                val first = items.first()
                val todosListos = items.all { it.estadoCocina.equals("Listo", ignoreCase = true) }
                val rawClient = first.cliente.ifBlank { first.idMesa }
                // Si no es un pedido de mesa explícito, mostrar siempre "Delivery" en vez de códigos de mesa
                val esDelivery = !first.tipoOrden.equals("En Mesa", ignoreCase = true)
                val clienteLimpio = if (esDelivery && (rawClient.startsWith("M-", ignoreCase = true) || rawClient.startsWith("Mesa", ignoreCase = true) || rawClient.isBlank())) {
                    "Delivery"
                } else {
                    rawClient.ifBlank { "Delivery" }
                }
                val tipoLabel = if (esDelivery) "Delivery" else "En Mesa"

                val tapersActuales = items.filter { BarRepository.isTaperItem(it) }.sumOf { it.cantidad }
                val subtotalProductos = items.filterNot { BarRepository.isTaperItem(it) }.sumOf { it.totalItem }

                StandByOrderGroup(
                    idPedido = idPedido,
                    fechaHora = first.fechaHora,
                    cliente = clienteLimpio,
                    tipoOrden = tipoLabel,
                    estadoCocina = if (todosListos) "Listo" else "En Cocina",
                    items = items,
                    cantidadTapers = tapersActuales,
                    subtotalSinTapers = subtotalProductos,
                    totalAcumulado = items.sumOf { it.totalItem }
                )
            }
            .sortedByDescending { it.fechaHora }
    }

    val selectedOrderForPayment = remember(standByGroups, selectedOrderIdForPayment) {
        standByGroups.find { it.idPedido == selectedOrderIdForPayment }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Órdenes en Stand By (${standByGroups.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Deliverys y envíos pendientes de cobro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshStandByOrders() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (standByGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Al día",
                        tint = SuccessGreen,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No hay órdenes pendientes de cobro",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Todos los envíos han sido liquidados en caja",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(standByGroups, key = { it.idPedido }) { orderGroup ->
                    StandByOrderCard(
                        orderGroup = orderGroup,
                        onUpdateTapers = { nuevaCant ->
                            viewModel.actualizarTapers(orderGroup.idPedido, nuevaCant)
                        },
                        onCobrarClick = { selectedOrderIdForPayment = orderGroup.idPedido }
                    )
                }
            }
        }

        if (selectedOrderForPayment != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedOrderIdForPayment = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                PaymentSheetContent(
                    orderGroup = selectedOrderForPayment,
                    onUpdateTapers = { nuevaCant ->
                        viewModel.actualizarTapers(selectedOrderForPayment.idPedido, nuevaCant)
                    },
                    onCancel = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            selectedOrderIdForPayment = null
                        }
                    },
                    onConfirmPayment = { metodoPago, montoEfectivo, montoYape, cantidadTapers ->
                        val targetId = selectedOrderForPayment.idPedido
                        viewModel.cobrarOrden(
                            idPedido = targetId,
                            metodoPago = metodoPago,
                            montoEfectivo = montoEfectivo,
                            montoYape = montoYape,
                            cantidadTapers = cantidadTapers
                        )
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            selectedOrderIdForPayment = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StandByOrderCard(
    orderGroup: StandByOrderGroup,
    onUpdateTapers: (Int) -> Unit = {},
    onCobrarClick: () -> Unit
) {
    val isCocinaReady = orderGroup.estadoCocina == "Listo"
    val statusBadgeColor = if (isCocinaReady) SuccessGreen else WarningRed
    val esMesa = orderGroup.tipoOrden.equals("En Mesa", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pedido #${orderGroup.idPedido}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = orderGroup.fechaHora.ifBlank { "Reciente" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = statusBadgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isCocinaReady) "✓ Listo para despacho" else "👨‍🍳 En Cocina",
                        color = statusBadgeColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (esMesa) Icons.Filled.TableRestaurant else Icons.Filled.LocalShipping,
                    contentDescription = "Destino",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val destinoTexto = if (orderGroup.cliente.equals(orderGroup.tipoOrden, ignoreCase = true) || orderGroup.cliente.isBlank()) {
                    orderGroup.tipoOrden
                } else {
                    "${orderGroup.tipoOrden} • ${orderGroup.cliente}"
                }
                Text(
                    text = destinoTexto,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Desglose de ítems del pedido (excluyendo el ítem de táper en Delivery para mostrarlo con su selector +/-)
            val displayItems = if (!esMesa) {
                orderGroup.items.filterNot { BarRepository.isTaperItem(it) }
            } else {
                orderGroup.items
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.cantidad}x ${item.producto}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "S/ ${"%.2f".format(item.totalItem)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Control rápido de Tápers en la tarjeta de Delivery
            if (!esMesa) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🥡 Tápers para llevar",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "S/ 1.00 c/u • Subtotal: S/ ${"%.2f".format(orderGroup.cantidadTapers * 1.0)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = {
                                    if (orderGroup.cantidadTapers > 0) {
                                        onUpdateTapers(orderGroup.cantidadTapers - 1)
                                    }
                                },
                                enabled = orderGroup.cantidadTapers > 0,
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Menos tápers", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${orderGroup.cantidadTapers}",
                                modifier = Modifier.padding(horizontal = 10.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledIconButton(
                                onClick = { onUpdateTapers(orderGroup.cantidadTapers + 1) },
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Más tápers", modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total acumulado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "S/ ${"%.2f".format(orderGroup.totalAcumulado)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = onCobrarClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "💳 Cobrar Pedido",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentSheetContent(
    orderGroup: StandByOrderGroup,
    onUpdateTapers: (Int) -> Unit = {},
    onCancel: () -> Unit,
    onConfirmPayment: (metodoPago: String, montoEfectivo: Double, montoYape: Double, cantidadTapers: Int?) -> Unit
) {
    val esDelivery = !orderGroup.tipoOrden.equals("En Mesa", ignoreCase = true)
    var tapersCount by remember(orderGroup.idPedido, orderGroup.cantidadTapers) {
        mutableStateOf(orderGroup.cantidadTapers)
    }

    val total = if (esDelivery) {
        orderGroup.subtotalSinTapers + (tapersCount * 1.0)
    } else {
        orderGroup.totalAcumulado
    }

    val metodos = listOf("Efectivo", "Yape", "Mixto")
    var metodoSeleccionado by remember { mutableStateOf("Efectivo") }

    var efectivoInput by remember(total) { mutableStateOf("%.2f".format(Locale.US, total / 2.0)) }
    var yapeInput by remember(total) { mutableStateOf("%.2f".format(Locale.US, total - (total / 2.0))) }

    val montoEfectivoMixto = efectivoInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    val montoYapeMixto = yapeInput.replace(",", ".").toDoubleOrNull() ?: 0.0

    val isPagoValido = when (metodoSeleccionado) {
        "Efectivo", "Yape" -> total > 0.0
        "Mixto" -> montoEfectivoMixto >= 0.0 &&
                montoYapeMixto >= 0.0 &&
                kotlin.math.abs((montoEfectivoMixto + montoYapeMixto) - total) < 0.05
        else -> false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Cobrar Pedido #${orderGroup.idPedido}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${orderGroup.tipoOrden} • ${orderGroup.cliente}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Selector interactivo de Tápers al momento de cobrar Delivery
        if (esDelivery) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🥡 Tápers usados (Delivery)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Ajusta si se usaron más o menos tápers (+S/ 1.00 c/u)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = {
                                    if (tapersCount > 0) {
                                        val next = tapersCount - 1
                                        tapersCount = next
                                        onUpdateTapers(next)
                                    }
                                },
                                enabled = tapersCount > 0,
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Menos tápers", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "$tapersCount",
                                modifier = Modifier.padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledIconButton(
                                onClick = {
                                    val next = tapersCount + 1
                                    tapersCount = next
                                    onUpdateTapers(next)
                                },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Más tápers", modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Subtotal productos:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "S/ ${"%.2f".format(orderGroup.subtotalSinTapers)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Recargo por tápers ($tapersCount unid.):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "S/ ${"%.2f".format(tapersCount * 1.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total a Cobrar:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "S/ ${"%.2f".format(total)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Método de Pago",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            metodos.forEach { metodo ->
                val selected = metodoSeleccionado == metodo
                FilterChip(
                    selected = selected,
                    onClick = {
                        metodoSeleccionado = metodo
                        if (metodo == "Mixto") {
                            val half = total / 2.0
                            efectivoInput = "%.2f".format(Locale.US, half)
                            yapeInput = "%.2f".format(Locale.US, total - half)
                        }
                    },
                    label = {
                        Text(
                            text = metodo,
                            modifier = Modifier.padding(vertical = 4.dp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (metodoSeleccionado == "Mixto") {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Desglose de Pago Mixto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = efectivoInput,
                onValueChange = { newVal ->
                    efectivoInput = newVal
                    val ef = newVal.replace(",", ".").toDoubleOrNull()
                    if (ef != null && ef <= total && ef >= 0.0) {
                        yapeInput = "%.2f".format(Locale.US, (total - ef).coerceAtLeast(0.0))
                    }
                },
                label = { Text("Monto en Efectivo (S/)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = montoEfectivoMixto > total || montoEfectivoMixto < 0.0
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = yapeInput,
                onValueChange = { newVal ->
                    yapeInput = newVal
                    val yp = newVal.replace(",", ".").toDoubleOrNull()
                    if (yp != null && yp <= total && yp >= 0.0) {
                        efectivoInput = "%.2f".format(Locale.US, (total - yp).coerceAtLeast(0.0))
                    }
                },
                label = { Text("Monto en Yape (S/)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = montoYapeMixto > total || montoYapeMixto < 0.0
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    val tapersFinal = if (esDelivery) tapersCount else null
                    when (metodoSeleccionado) {
                        "Efectivo" -> onConfirmPayment("Efectivo", total, 0.0, tapersFinal)
                        "Yape" -> onConfirmPayment("Yape", 0.0, total, tapersFinal)
                        "Mixto" -> onConfirmPayment("Mixto", montoEfectivoMixto, montoYapeMixto, tapersFinal)
                    }
                },
                enabled = isPagoValido,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Confirmar Cobro", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ActiveOrdersScreenPreview() {
    BarPOSTheme {
        ActiveOrdersScreen()
    }
}
