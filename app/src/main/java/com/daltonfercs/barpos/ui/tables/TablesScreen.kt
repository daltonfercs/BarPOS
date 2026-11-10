package com.daltonfercs.barpos.ui.tables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
        _feedbackMessage.value = "✅ Mesa $numero registrada con éxito."
    }

    fun updateTable(idMesa: String, numero: Int, capacidad: Int) {
        BarRepository.updateTable(idMesa, numero, capacidad)
        _feedbackMessage.value = "✏️ Mesa $numero actualizada con éxito."
    }

    fun deleteTable(mesa: TableModel) {
        BarRepository.deleteTable(mesa.idMesa)
        _feedbackMessage.value = "🗑️ Mesa ${mesa.numero} eliminada."
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
    var tableToEdit by remember { mutableStateOf<TableModel?>(null) }
    var tableToDelete by remember { mutableStateOf<TableModel?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                onClick = {
                    tableToEdit = null
                    showCreateTableDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva Mesa", tint = Color.White)
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(mesas, key = { it.idMesa }) { mesa ->
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
                    },
                    onEditClick = {
                        tableToEdit = mesa
                        showCreateTableDialog = true
                    },
                    onDeleteClick = {
                        tableToDelete = mesa
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
                initialTable = tableToEdit,
                onDismiss = {
                    showCreateTableDialog = false
                    tableToEdit = null
                },
                onConfirm = { num, cap ->
                    val editing = tableToEdit
                    if (editing != null) {
                        viewModel.updateTable(editing.idMesa, num, cap)
                    } else {
                        viewModel.createTable(num, cap)
                    }
                    showCreateTableDialog = false
                    tableToEdit = null
                }
            )
        }

        if (tableToDelete != null) {
            val targetMesa = tableToDelete!!
            AlertDialog(
                onDismissRequest = { tableToDelete = null },
                title = { Text("Eliminar Mesa ${targetMesa.numero}", fontWeight = FontWeight.Bold) },
                text = {
                    Text("¿Estás seguro de que deseas eliminar la Mesa ${targetMesa.numero} (${targetMesa.idMesa})? Esta acción se sincronizará con el sistema.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTable(targetMesa)
                            tableToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { tableToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun CreateTableDialog(
    initialTable: TableModel? = null,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val isEditing = initialTable != null
    var numero by remember(initialTable) { mutableStateOf(initialTable?.numero?.toString() ?: "") }
    var capacidad by remember(initialTable) { mutableStateOf(initialTable?.capacidad?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Editar Mesa ${initialTable?.numero}" else "Registrar Nueva Mesa",
                fontWeight = FontWeight.Bold
            )
        },
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
            ) { Text(if (isEditing) "Actualizar" else "Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableCard(
    mesa: TableModel,
    onClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val isOcupada = mesa.estado.equals("Ocupada", ignoreCase = true)
    val statusColor = if (isOcupada) WarningRed else SuccessGreen
    val containerColor = if (isOcupada) {
        WarningRed.copy(alpha = 0.10f)
    } else {
        SuccessGreen.copy(alpha = 0.08f)
    }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Franja vertical en el borde izquierdo indicando el estado (Verde = Libre, Rojo = Ocupada)
            Box(
                modifier = Modifier
                    .matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(8.dp)
                        .background(statusColor)
                        .align(Alignment.CenterStart)
                )
            }

            // Botón de menú (⋮) en la esquina superior derecha + DropdownMenu
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Opciones de Mesa",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar Mesa") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Editar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar Mesa", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Mesa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    "${mesa.numero}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    "${mesa.capacidad} pers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = mesa.estado,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
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
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
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
        Spacer(modifier = Modifier.height(14.dp))

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
                        Text(
                            "${item.cantidad}x ${item.producto} (${item.categoria})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            "S/ ${"%.2f".format(item.totalItem)}",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total Acumulado:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "S/ ${"%.2f".format(total)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (modoMixto) {
            Text("Desglose de Pago Mixto", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = efectivoInput,
                    onValueChange = { newVal ->
                        efectivoInput = newVal
                        val ef = newVal.replace(",", ".").toDoubleOrNull()
                        if (ef != null && ef <= total && ef >= 0.0) {
                            yapeInput = "%.2f".format(java.util.Locale.US, (total - ef).coerceAtLeast(0.0))
                        }
                    },
                    label = { Text("Efectivo (S/)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = montoEfectivo > total || montoEfectivo < 0.0
                )
                OutlinedTextField(
                    value = yapeInput,
                    onValueChange = { newVal ->
                        yapeInput = newVal
                        val yp = newVal.replace(",", ".").toDoubleOrNull()
                        if (yp != null && yp <= total && yp >= 0.0) {
                            efectivoInput = "%.2f".format(java.util.Locale.US, (total - yp).coerceAtLeast(0.0))
                        }
                    },
                    label = { Text("Yape (S/)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = montoYape > total || montoYape < 0.0
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { modoMixto = false },
                    modifier = Modifier.weight(1f)
                ) { Text("Atrás", maxLines = 1, softWrap = false) }

                Button(
                    onClick = { onPay("Mixto", montoEfectivo, montoYape) },
                    modifier = Modifier.weight(1f),
                    enabled = isMixtoValid && total > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Confirmar", maxLines = 1, softWrap = false) }
            }
        } else if (modoCobro) {
            Text("Seleccione Método de Pago", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onPay("Efectivo", total, 0.0) },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Efectivo", maxLines = 1, softWrap = false) }

                Button(
                    onClick = { onPay("Yape", 0.0, total) },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("Yape", maxLines = 1, softWrap = false) }

                Button(
                    onClick = {
                        val half = total / 2.0
                        efectivoInput = "%.2f".format(java.util.Locale.US, half)
                        yapeInput = "%.2f".format(java.util.Locale.US, total - half)
                        modoMixto = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) { Text("Mixto", maxLines = 1, softWrap = false) }
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("➕ Agregar Más", maxLines = 1, softWrap = false) }

                Button(
                    onClick = { modoCobro = true },
                    modifier = Modifier.weight(1f),
                    enabled = total > 0,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("💳 Cobrar Cuenta", maxLines = 1, softWrap = false) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview
@Composable
fun TablesScreenPreview() {
    BarPOSTheme {
        TablesScreen(onTableSelected = {})
    }
}
