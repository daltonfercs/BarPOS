package com.daltonfercs.barpos.ui.tables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import com.daltonfercs.barpos.model.TableModel
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import com.daltonfercs.barpos.ui.theme.SuccessGreen
import com.daltonfercs.barpos.ui.theme.WarningRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TablesViewModel : ViewModel() {
    val mesas: StateFlow<List<TableModel>> = BarRepository.tables

    private val _selectedTableBill = MutableStateFlow<List<PedidoItem>>(emptyList())
    val selectedTableBill: StateFlow<List<PedidoItem>> = _selectedTableBill.asStateFlow()

    private val _isLoadingBill = MutableStateFlow(false)
    val isLoadingBill: StateFlow<Boolean> = _isLoadingBill.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun loadTableBill(idMesa: String) {
        _isLoadingBill.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val bill = BarRepository.getTableBill(idMesa)
            _selectedTableBill.value = bill
            _isLoadingBill.value = false
        }
    }

    fun payAndFreeTable(
        idMesa: String,
        metodoPago: String,
        montoEfectivo: Double,
        montoYape: Double
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            BarRepository.payAndFreeTable(idMesa, metodoPago, montoEfectivo, montoYape)
            _selectedTableBill.value = emptyList()
            _feedbackMessage.value = "✅ Cuenta cobrada con éxito. Mesa liberada."
        }
    }

    fun createTable(numero: Int, capacidad: Int) {
        BarRepository.addTable(numero, capacidad)
        _feedbackMessage.value = "Mesa $numero registrada con éxito."
    }

    fun refreshMesas() {
        viewModelScope.launch {
            BarRepository.syncTables()
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesScreen(
    viewModel: TablesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onTableSelected: (TableModel) -> Unit
) {
    val mesas by viewModel.mesas.collectAsState()
    val tableBill by viewModel.selectedTableBill.collectAsState()
    val isLoadingBill by viewModel.isLoadingBill.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    var activeTable by remember { mutableStateOf<TableModel?>(null) }
    var showCreateTableDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Mesas y Salón", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { viewModel.refreshMesas() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateTableDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva Mesa", tint = Color.White)
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(mesas) { mesa ->
                TableCard(
                    mesa = mesa,
                    onClick = {
                        if (mesa.estado == "Ocupada") {
                            activeTable = mesa
                            viewModel.loadTableBill(mesa.idMesa)
                            showBottomSheet = true
                        } else {
                            // Si está libre, abrir comanda directamente
                            onTableSelected(mesa)
                        }
                    }
                )
            }
        }

        if (showBottomSheet && activeTable != null) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                TableBillSheet(
                    mesa = activeTable!!,
                    billItems = tableBill,
                    isLoading = isLoadingBill,
                    onAddProducts = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onTableSelected(activeTable!!)
                        }
                    },
                    onPay = { metodo, montoEfectivo, montoYape ->
                        viewModel.payAndFreeTable(activeTable!!.idMesa, metodo, montoEfectivo, montoYape)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                        }
                    }
                )
            }
        }

        if (showCreateTableDialog) {
            CreateTableDialog(
                onDismiss = { showCreateTableDialog = false },
                onConfirm = { num, cap ->
                    viewModel.createTable(num, cap)
                    showCreateTableDialog = false
                }
            )
        }
    }
}

@Composable
fun CreateTableDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var numero by remember { mutableStateOf("") }
    var capacidad by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Nueva Mesa", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = numero,
                    onValueChange = { numero = it },
                    label = { Text("Número de Mesa (ej. 7)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = capacidad,
                    onValueChange = { capacidad = it },
                    label = { Text("Capacidad (personas)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = numero.toIntOrNull() ?: 0
                    val c = capacidad.toIntOrNull() ?: 0
                    if (n > 0 && c > 0) onConfirm(n, c)
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun TableCard(mesa: TableModel, onClick: () -> Unit) {
    val isOcupada = mesa.estado == "Ocupada"
    val containerColor = if (isOcupada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val badgeColor = if (isOcupada) WarningRed else SuccessGreen

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Mesa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${mesa.numero}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${mesa.capacidad} pers.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = mesa.estado,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TableBillSheet(
    mesa: TableModel,
    billItems: List<PedidoItem>,
    isLoading: Boolean = false,
    onAddProducts: () -> Unit,
    onPay: (metodo: String, montoEfectivo: Double, montoYape: Double) -> Unit
) {
    val total = billItems.sumOf { it.totalItem }
    var modoCobro by remember { mutableStateOf(false) }
    var modoMixto by remember { mutableStateOf(false) }
    var efectivoInput by remember(total) { mutableStateOf("%.2f".format(java.util.Locale.US, total / 2.0)) }
    var yapeInput by remember(total) { mutableStateOf("%.2f".format(java.util.Locale.US, total - (total / 2.0))) }

    val montoEfectivo = efectivoInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    val montoYape = yapeInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    val isMixtoValid = montoEfectivo >= 0.0 && montoYape >= 0.0 && kotlin.math.abs((montoEfectivo + montoYape) - total) < 0.05

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            "Cuenta Acumulada - Mesa ${mesa.numero}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Pre-cuenta con pedidos pendientes de pago",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (billItems.isEmpty()) {
            Text(
                "No hay consumos pendientes registrados para esta mesa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                billItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${item.cantidad}x ${item.producto} (${item.categoria})", style = MaterialTheme.typography.bodyMedium)
                        Text("S/ ${"%.2f".format(item.totalItem)}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total Acumulado:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "S/ ${"%.2f".format(total)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (modoMixto) {
            Text("Desglose de Pago Mixto", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = efectivoInput,
                onValueChange = { newVal ->
                    efectivoInput = newVal
                    val ef = newVal.replace(",", ".").toDoubleOrNull()
                    if (ef != null && ef <= total && ef >= 0.0) {
                        yapeInput = "%.2f".format(java.util.Locale.US, (total - ef).coerceAtLeast(0.0))
                    }
                },
                label = { Text("Monto en Efectivo (S/)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = montoEfectivo > total || montoEfectivo < 0.0
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = yapeInput,
                onValueChange = { newVal ->
                    yapeInput = newVal
                    val yp = newVal.replace(",", ".").toDoubleOrNull()
                    if (yp != null && yp <= total && yp >= 0.0) {
                        efectivoInput = "%.2f".format(java.util.Locale.US, (total - yp).coerceAtLeast(0.0))
                    }
                },
                label = { Text("Monto en Yape (S/)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = montoYape > total || montoYape < 0.0
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { modoMixto = false },
                    modifier = Modifier.weight(1f)
                ) { Text("Atrás") }

                Button(
                    onClick = { onPay("Mixto", montoEfectivo, montoYape) },
                    modifier = Modifier.weight(1f),
                    enabled = isMixtoValid && total > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Confirmar") }
            }
        } else if (modoCobro) {
            Text("Seleccione Método de Pago", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onPay("Efectivo", total, 0.0) },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Efectivo") }

                Button(
                    onClick = { onPay("Yape", 0.0, total) },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("Yape") }

                Button(
                    onClick = {
                        val half = total / 2.0
                        efectivoInput = "%.2f".format(java.util.Locale.US, half)
                        yapeInput = "%.2f".format(java.util.Locale.US, total - half)
                        modoMixto = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) { Text("Mixto") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { modoCobro = false },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancelar") }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onAddProducts() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("➕ Agregar Más") }

                Button(
                    onClick = { modoCobro = true },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("💳 Cobrar Cuenta") }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview
@Composable
fun TablesScreenPreview() {
    BarPOSTheme {
        TablesScreen(onTableSelected = {})
    }
}
