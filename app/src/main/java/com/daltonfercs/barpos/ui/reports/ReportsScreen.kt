package com.daltonfercs.barpos.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
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
import com.daltonfercs.barpos.ui.theme.SuccessGreen
import com.daltonfercs.barpos.ui.theme.WarningRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReportsUiState(
    val selectedDate: String = "",
    val isLoading: Boolean = false,
    val totalRecaudado: Double = 0.0,
    val totalYape: Double = 0.0,
    val totalEfectivo: Double = 0.0,
    val totalComida: Double = 0.0,
    val cantidadComida: Int = 0,
    val totalBebida: Double = 0.0,
    val cantidadBebida: Int = 0,
    val totalPendiente: Double = 0.0,
    val ordenes: List<PedidoItem> = emptyList()
)

class ReportsViewModel : ViewModel() {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _uiState = MutableStateFlow(ReportsUiState(selectedDate = BarRepository.getTodayString()))
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        cargarReporte(_uiState.value.selectedDate)
    }

    fun cargarReporte(fecha: String) {
        _uiState.value = _uiState.value.copy(selectedDate = fecha, isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val pedidos = BarRepository.getOrdersForDate(fecha)

            val pagados = pedidos.filter { it.estadoPago == "Pagado" }
            val pendientes = pedidos.filter { it.estadoPago == "Pendiente" }

            val totalRecaudado = pagados.sumOf { it.totalItem }

            // Sumar correctamente Yape y Efectivo incluyendo los cobros en modo "Mixto"
            val totalYape = pagados.sumOf { item ->
                when {
                    item.metodoPago.equals("Yape", ignoreCase = true) -> item.totalItem
                    item.metodoPago.contains("Mixto", ignoreCase = true) -> {
                        if (item.montoYape > 0.0 || item.montoEfectivo > 0.0) item.montoYape else item.totalItem / 2.0
                    }
                    else -> 0.0
                }
            }

            val totalEfectivo = pagados.sumOf { item ->
                when {
                    item.metodoPago.equals("Efectivo", ignoreCase = true) -> item.totalItem
                    item.metodoPago.contains("Mixto", ignoreCase = true) -> {
                        if (item.montoEfectivo > 0.0 || item.montoYape > 0.0) item.montoEfectivo else item.totalItem / 2.0
                    }
                    else -> 0.0
                }
            }

            val comidas = pagados.filter {
                it.categoria.contains("Comida", ignoreCase = true) || it.categoria.contains("Snack", ignoreCase = true)
            }
            val totalComida = comidas.sumOf { it.totalItem }
            val cantidadComida = comidas.sumOf { it.cantidad }

            val bebidas = pagados.filter { it.categoria.contains("Bebida", ignoreCase = true) }
            val totalBebida = bebidas.sumOf { it.totalItem }
            val cantidadBebida = bebidas.sumOf { it.cantidad }

            val totalPendiente = pendientes.sumOf { it.totalItem }

            _uiState.value = ReportsUiState(
                selectedDate = fecha,
                isLoading = false,
                totalRecaudado = totalRecaudado,
                totalYape = totalYape,
                totalEfectivo = totalEfectivo,
                totalComida = totalComida,
                cantidadComida = cantidadComida,
                totalBebida = totalBebida,
                cantidadBebida = cantidadBebida,
                totalPendiente = totalPendiente,
                ordenes = pedidos
            )
        }
    }

    fun irDiaAnterior() {
        try {
            val cal = Calendar.getInstance()
            cal.time = dayFormat.parse(_uiState.value.selectedDate) ?: Date()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cargarReporte(dayFormat.format(cal.time))
        } catch (_: Exception) { }
    }

    fun irDiaSiguiente() {
        try {
            val cal = Calendar.getInstance()
            cal.time = dayFormat.parse(_uiState.value.selectedDate) ?: Date()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cargarReporte(dayFormat.format(cal.time))
        } catch (_: Exception) { }
    }

    fun irHoy() {
        cargarReporte(BarRepository.getTodayString())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val hoy = BarRepository.getTodayString()
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cierre de Caja & Reportes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { viewModel.cargarReporte(state.selectedDate) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar")
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
            // Selector de Fecha
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.irDiaAnterior() }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Día Anterior")
                        }

                        Button(
                            onClick = { showDatePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Calendario", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.selectedDate == hoy) "Hoy (${state.selectedDate})" else state.selectedDate,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { viewModel.irDiaSiguiente() }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Día Siguiente")
                        }
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // 1. Total Recaudado (Ventas del día)
                item {
                    ReportCard(
                        title = "💵 Total Recaudado (Ventas del Día)",
                        amount = state.totalRecaudado,
                        isMain = true,
                        badgeText = "Pagado"
                    )
                }

                // 2 & 3. Desglose Yape vs Efectivo
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ReportCard(
                            title = "📱 Total Yape / Digital",
                            amount = state.totalYape,
                            modifier = Modifier.weight(1f),
                            cardColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                        ReportCard(
                            title = "🪙 Total en Efectivo (Caja)",
                            amount = state.totalEfectivo,
                            modifier = Modifier.weight(1f),
                            cardColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    }
                }

                // 4 & 5. Total Comida vs Bebidas
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ReportCard(
                            title = "🍽️ Comida Vendida",
                            amount = state.totalComida,
                            subtitle = "${state.cantidadComida} platos servidos",
                            modifier = Modifier.weight(1f)
                        )
                        ReportCard(
                            title = "🍻 Bebidas Vendidas",
                            amount = state.totalBebida,
                            subtitle = "${state.cantidadBebida} unidades",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 6. Total Pendiente de Cobro
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "⚠️ Total Pendiente de Cobro",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Mesas abiertas o comandas sin pagar en esta fecha",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "S/ ${"%.2f".format(state.totalPendiente)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Lista de pedidos del día
                if (state.ordenes.isNotEmpty()) {
                    item {
                        Text(
                            "Detalle de Comandas (${state.ordenes.size} ítems)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(state.ordenes) { pedido ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "${pedido.cantidad}x ${pedido.producto}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${pedido.idPedido} • ${pedido.idMesa} • ${pedido.tipoOrden}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "S/ ${"%.2f".format(pedido.totalItem)}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Surface(
                                        color = if (pedido.estadoPago == "Pagado") SuccessGreen else WarningRed,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = pedido.estadoPago,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo simple para seleccionar fecha
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = millis
                            // Compensar zona horaria UTC de DatePickerState
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            viewModel.cargarReporte(sdf.format(cal.time))
                        }
                        showDatePicker = false
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun ReportCard(
    title: String,
    amount: Double,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    isMain: Boolean = false,
    badgeText: String? = null,
    cardColor: Color = MaterialTheme.colorScheme.surface
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = if (isMain) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMain) Arrangement.Center else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                badgeText?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "S/ ${"%.2f".format(amount)}",
                style = if (isMain) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
