package com.daltonfercs.barpos.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableRestaurant
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
import com.daltonfercs.barpos.model.Producto
import com.daltonfercs.barpos.model.TableModel
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// UI State para Pedidos (En Mesa y Envíos / Para Llevar)
data class CatalogUiState(
    val productos: List<Producto> = emptyList(),
    val mesas: List<TableModel> = emptyList(),
    val categorias: List<String> = listOf("Bebidas", "Comidas", "Snacks"),
    val categoriaSeleccionada: String = "Bebidas",
    // FIX DEL CRASH: Usar String (ID del producto) como clave en lugar de la instancia entera
    val carrito: Map<String, Int> = emptyMap(),
    val tipoOrden: String = "Delivery", // "Delivery", "En Mesa"
    val mesaSeleccionada: String = "M-01",
    val clienteDireccion: String = "",
    val extraTapers: Int = 0,
    val isLoading: Boolean = false,
    val mensajeFeedback: String? = null
) {
    val esEnMesa: Boolean
        get() = tipoOrden.equals("En Mesa", ignoreCase = true)

    val subtotalProductos: Double
        get() = carrito.entries.sumOf { (idProd, cantidad) ->
            val p = productos.find { (it.id.ifBlank { it.nombre }) == idProd }
            (p?.precio ?: 0.0) * cantidad
        }

    /**
     * Cuenta la cantidad de productos de comida (o que no sean bebidas).
     * Si es "En Mesa", tapersBase es 0.
     */
    val tapersBase: Int
        get() = if (esEnMesa) 0 else carrito.entries.filter { (idProd, _) ->
            val p = productos.find { (it.id.ifBlank { it.nombre }) == idProd }
            p != null &&
            !p.categoria.equals("Bebidas", ignoreCase = true) &&
            !p.categoria.equals("Bebida", ignoreCase = true)
        }.sumOf { it.value }

    val totalTapers: Int
        get() = if (esEnMesa) 0 else tapersBase + extraTapers

    val recargoTapers: Double
        get() = if (esEnMesa) 0.0 else totalTapers * 1.0

    val total: Double
        get() = subtotalProductos + recargoTapers

    val cantidadTotal: Int
        get() = carrito.values.sum()
}

class CatalogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            BarRepository.products.collect { prods ->
                _uiState.value = _uiState.value.copy(productos = prods)
            }
        }
        viewModelScope.launch {
            BarRepository.tables.collect { tablesList ->
                val currentMesa = _uiState.value.mesaSeleccionada
                val defaultMesa = if (currentMesa.isBlank() && tablesList.isNotEmpty()) {
                    tablesList.first().idMesa
                } else currentMesa
                _uiState.value = _uiState.value.copy(mesas = tablesList, mesaSeleccionada = defaultMesa)
            }
        }
    }

    fun setCategoria(categoria: String) {
        _uiState.value = _uiState.value.copy(categoriaSeleccionada = categoria)
    }

    fun setTipoOrden(tipo: String) {
        _uiState.value = _uiState.value.copy(
            tipoOrden = tipo,
            extraTapers = if (tipo.equals("En Mesa", ignoreCase = true)) 0 else _uiState.value.extraTapers
        )
    }

    fun setMesaSeleccionada(idMesa: String) {
        _uiState.value = _uiState.value.copy(
            mesaSeleccionada = idMesa,
            tipoOrden = "En Mesa"
        )
    }

    fun setClienteDireccion(direccion: String) {
        _uiState.value = _uiState.value.copy(clienteDireccion = direccion)
    }

    fun incrementarExtraTapers() {
        _uiState.value = _uiState.value.copy(extraTapers = _uiState.value.extraTapers + 1)
    }

    fun decrementarExtraTapers() {
        if (_uiState.value.extraTapers > 0) {
            _uiState.value = _uiState.value.copy(extraTapers = _uiState.value.extraTapers - 1)
        }
    }

    // FIX: Agrupación por productoId (String) inmutable y seguro contra NPE + Control de Stock
    fun agregarAlCarrito(productoId: String) {
        if (productoId.isBlank()) return
        val prod = _uiState.value.productos.find { (it.id.ifBlank { it.nombre }) == productoId }
        val currentCart = _uiState.value.carrito.toMutableMap()
        val currentQty = currentCart[productoId] ?: 0

        if (prod != null) {
            if (!prod.disponible || prod.stock <= 0) {
                _uiState.value = _uiState.value.copy(mensajeFeedback = "⚠️ ${prod.nombre} está agotado")
                return
            }
            if (currentQty >= prod.stock) {
                _uiState.value = _uiState.value.copy(mensajeFeedback = "⚠️ Stock máximo alcanzado para ${prod.nombre} (${prod.stock} disp.)")
                return
            }
        }

        currentCart[productoId] = currentQty + 1
        _uiState.value = _uiState.value.copy(carrito = currentCart)
    }

    fun removerDelCarrito(productoId: String) {
        val currentCart = _uiState.value.carrito.toMutableMap()
        val currentCount = currentCart[productoId] ?: 0
        if (currentCount > 1) {
            currentCart[productoId] = currentCount - 1
        } else {
            currentCart.remove(productoId)
        }
        _uiState.value = _uiState.value.copy(carrito = currentCart)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(mensajeFeedback = null)
    }

    /**
     * Envía el pedido (En Mesa o Delivery) a Cocina registrándolo en Sheets con Estado_Cocina = "Pendiente".
     */
    fun enviarComandaACocina(onComandaEnviada: (String) -> Unit) {
        val state = _uiState.value
        if (state.carrito.isEmpty()) return

        _uiState.value = state.copy(isLoading = true)

        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<PedidoItem>()
            val destinoMesa = if (state.esEnMesa) {
                state.mesaSeleccionada.ifBlank { "M-01" }
            } else {
                state.clienteDireccion.ifBlank { "Delivery" }
            }
            val clienteNombre = if (state.esEnMesa) {
                val mesaObj = state.mesas.find { it.idMesa == destinoMesa }
                if (mesaObj != null) "Mesa ${mesaObj.numero}" else destinoMesa
            } else {
                state.clienteDireccion.ifBlank { "Delivery" }
            }

            // 1. Agregar productos seleccionados con Estado_Cocina = "Pendiente"
            state.carrito.forEach { (prodId, cantidad) ->
                val prod = state.productos.find { (it.id.ifBlank { it.nombre }) == prodId }
                if (prod != null) {
                    items.add(
                        PedidoItem(
                            idPedido = "",
                            fechaHora = "",
                            categoria = prod.categoria,
                            producto = prod.nombre,
                            cantidad = cantidad,
                            precioUnitario = prod.precio,
                            totalItem = prod.precio * cantidad,
                            tipoOrden = if (state.esEnMesa) "En Mesa" else "Delivery",
                            idMesa = destinoMesa,
                            cliente = clienteNombre,
                            metodoPago = "Pendiente",
                            estadoCocina = "Pendiente",
                            estadoPago = "Pendiente"
                        )
                    )
                }
            }

            // 2. Si es Delivery y hay tápers, insertar ítem de cobro explícito de empaque
            if (!state.esEnMesa && state.totalTapers > 0) {
                items.add(
                    PedidoItem(
                        idPedido = "",
                        fechaHora = "",
                        categoria = "Empaque",
                        producto = "Táper para llevar",
                        cantidad = state.totalTapers,
                        precioUnitario = 1.0,
                        totalItem = state.totalTapers * 1.0,
                        tipoOrden = "Delivery",
                        idMesa = destinoMesa,
                        cliente = clienteNombre,
                        metodoPago = "Pendiente",
                        estadoCocina = "Listo",
                        estadoPago = "Pendiente"
                    )
                )
            }

            val result = BarRepository.enviarComandaACocina(
                items = items,
                idMesa = destinoMesa,
                tipoOrden = if (state.esEnMesa) "En Mesa" else "Delivery",
                cliente = clienteNombre
            )

            val orderId = result.getOrDefault("ORD-${System.currentTimeMillis() % 10000}")

            _uiState.value = _uiState.value.copy(
                carrito = emptyMap(),
                clienteDireccion = "",
                extraTapers = 0,
                isLoading = false,
                mensajeFeedback = "✅ Pedido $orderId ($clienteNombre) enviado a Cocina"
            )

            viewModelScope.launch(Dispatchers.Main) {
                onComandaEnviada(orderId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    idMesaInicial: String? = null,
    viewModel: CatalogViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToCheckout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCartBottomSheet by remember { mutableStateOf(false) }
    var showMesaDropdown by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(idMesaInicial) {
        if (!idMesaInicial.isNullOrBlank()) {
            viewModel.setMesaSeleccionada(idMesaInicial)
        } else {
            viewModel.setTipoOrden("Delivery")
        }
    }

    LaunchedEffect(uiState.mensajeFeedback) {
        uiState.mensajeFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val mesaActualObj = uiState.mesas.find { it.idMesa == uiState.mesaSeleccionada }
    val mesaLabel = if (mesaActualObj != null) "Mesa ${mesaActualObj.numero}" else uiState.mesaSeleccionada

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (uiState.esEnMesa) Icons.Filled.TableRestaurant else Icons.Filled.LocalShipping,
                            contentDescription = "Pedidos",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (uiState.esEnMesa) "Pedido en Salón ($mesaLabel)" else "Envíos / Para Llevar",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (uiState.esEnMesa) "Atención en mesa"
                                else if (uiState.clienteDireccion.isNotBlank()) "Destino: ${uiState.clienteDireccion}"
                                else "Con cálculo automático de tápers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.esEnMesa && uiState.mesas.isNotEmpty()) {
                        Box {
                            AssistChip(
                                onClick = { showMesaDropdown = true },
                                label = { Text(mesaLabel, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.TableRestaurant,
                                        contentDescription = "Elegir Mesa",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            DropdownMenu(
                                expanded = showMesaDropdown,
                                onDismissRequest = { showMesaDropdown = false }
                            ) {
                                uiState.mesas.forEach { mesa ->
                                    DropdownMenuItem(
                                        text = { Text("Mesa ${mesa.numero} (${mesa.estado})") },
                                        onClick = {
                                            viewModel.setMesaSeleccionada(mesa.idMesa)
                                            showMesaDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState.cantidadTotal > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.clickable { showCartBottomSheet = true }) {
                            Text(
                                "${uiState.cantidadTotal} productos • ${uiState.tipoOrden}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Total: S/ ${"%.2f".format(uiState.total)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showCartBottomSheet = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = "Ver Pedido", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resumen")
                            }

                            Button(
                                onClick = {
                                    viewModel.enviarComandaACocina { }
                                },
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("👨‍🍳 A Cocina", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Selector rápido de modalidad: "En Mesa" vs "Para Llevar / Envío"
            val modalidades = listOf("En Mesa", "Para Llevar")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                modalidades.forEachIndexed { index, modo ->
                    val isSelected = (modo == "En Mesa" && uiState.esEnMesa) || (modo != "En Mesa" && !uiState.esEnMesa)
                    SegmentedButton(
                        selected = isSelected,
                        onClick = { viewModel.setTipoOrden(modo) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modalidades.size)
                    ) {
                        Text(if (modo == "En Mesa") "🪑 En Mesa ($mesaLabel)" else "🛵 Para Llevar / Envío")
                    }
                }
            }

            // Categorías
            ScrollableTabRow(
                selectedTabIndex = uiState.categorias.indexOf(uiState.categoriaSeleccionada),
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 8.dp
            ) {
                uiState.categorias.forEach { categoria ->
                    Tab(
                        selected = uiState.categoriaSeleccionada == categoria,
                        onClick = { viewModel.setCategoria(categoria) },
                        text = { Text(categoria) }
                    )
                }
            }

            // Grid de productos
            val productosFiltrados = uiState.productos.filter { it.categoria == uiState.categoriaSeleccionada }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(productosFiltrados) { producto ->
                    val prodKey = producto.id.ifBlank { producto.nombre }
                    val cantidad = uiState.carrito[prodKey] ?: 0
                    ProductCard(
                        producto = producto,
                        cantidad = cantidad,
                        onAdd = { viewModel.agregarAlCarrito(prodKey) },
                        onRemove = { viewModel.removerDelCarrito(prodKey) }
                    )
                }
            }
        }

        // BottomSheet interactivo de Resumen (En Mesa o Envíos con Tápers)
        if (showCartBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCartBottomSheet = false },
                sheetState = sheetState
            ) {
                CartBottomSheetContent(
                    uiState = uiState,
                    onTipoOrdenChange = { viewModel.setTipoOrden(it) },
                    onMesaChange = { viewModel.setMesaSeleccionada(it) },
                    onClienteDireccionChange = { viewModel.setClienteDireccion(it) },
                    onAddProductId = { viewModel.agregarAlCarrito(it) },
                    onRemoveProductId = { viewModel.removerDelCarrito(it) },
                    onIncrementExtraTapers = { viewModel.incrementarExtraTapers() },
                    onDecrementExtraTapers = { viewModel.decrementarExtraTapers() },
                    onConfirmOrder = {
                        viewModel.enviarComandaACocina {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showCartBottomSheet = false
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartBottomSheetContent(
    uiState: CatalogUiState,
    onTipoOrdenChange: (String) -> Unit,
    onMesaChange: (String) -> Unit,
    onClienteDireccionChange: (String) -> Unit,
    onAddProductId: (String) -> Unit,
    onRemoveProductId: (String) -> Unit,
    onIncrementExtraTapers: () -> Unit,
    onDecrementExtraTapers: () -> Unit,
    onConfirmOrder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Resumen de la Comanda",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Selector de Modalidad en el BottomSheet
        val modalidades = listOf("En Mesa", "Para Llevar")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modalidades.forEachIndexed { index, modo ->
                val isSelected = (modo == "En Mesa" && uiState.esEnMesa) || (modo != "En Mesa" && !uiState.esEnMesa)
                SegmentedButton(
                    selected = isSelected,
                    onClick = { onTipoOrdenChange(modo) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modalidades.size)
                ) {
                    Text(if (modo == "En Mesa") "🪑 En Mesa" else "🛵 Para Llevar / Envío")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.esEnMesa) {
            Text(
                text = "Seleccionar Mesa:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.mesas.take(6).forEach { mesa ->
                    FilterChip(
                        selected = uiState.mesaSeleccionada == mesa.idMesa,
                        onClick = { onMesaChange(mesa.idMesa) },
                        label = { Text("M-${mesa.numero}") }
                    )
                }
            }
        } else {
            // Campo opcional para Nombre del Cliente / Dirección
            OutlinedTextField(
                value = uiState.clienteDireccion,
                onValueChange = onClienteDireccionChange,
                label = { Text("Nombre del Cliente / Dirección (Opcional)") },
                placeholder = { Text("Ej. Juan Pérez - Av. Larco 456") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de ítems en carrito
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.carrito.entries.toList()) { (prodId, cantidad) ->
                val producto = uiState.productos.find { (it.id.ifBlank { it.nombre }) == prodId }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(producto?.nombre ?: prodId, fontWeight = FontWeight.Medium)
                        Text(
                            "${producto?.categoria ?: ""} • S/ ${"%.2f".format(producto?.precio ?: 0.0)} c/u",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = { onRemoveProductId(prodId) },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Menos", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "$cantidad",
                            modifier = Modifier.padding(horizontal = 10.dp),
                            fontWeight = FontWeight.Bold
                        )
                        FilledIconButton(
                            onClick = { onAddProductId(prodId) },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Más", modifier = Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sección destacada de Tápers (solo si NO es En Mesa)
        if (!uiState.esEnMesa) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🥡 Empaques y Tápers para Llevar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tápers automáticos por comidas: ${uiState.tapersBase} unid. (S/ ${"%.2f".format(uiState.tapersBase * 1.0)})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tápers adicionales (+S/ 1.00 c/u):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = onDecrementExtraTapers,
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Menos", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${uiState.extraTapers}",
                                modifier = Modifier.padding(horizontal = 10.dp),
                                fontWeight = FontWeight.Bold
                            )
                            FilledIconButton(
                                onClick = onIncrementExtraTapers,
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Más", modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Costo de empaque: S/ ${"%.2f".format(uiState.totalTapers * 1.0)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        // Resumen de Pago
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Subtotal productos:", style = MaterialTheme.typography.bodyMedium)
                Text("S/ ${"%.2f".format(uiState.subtotalProductos)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            if (!uiState.esEnMesa) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recargo tápers (${uiState.totalTapers} unid.):", style = MaterialTheme.typography.bodyMedium)
                    Text("S/ ${"%.2f".format(uiState.recargoTapers)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total final a pagar:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "S/ ${"%.2f".format(uiState.total)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onConfirmOrder,
            enabled = !uiState.isLoading && uiState.cantidadTotal > 0,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(
                    text = "👨‍🍳 Confirmar y Enviar a Cocina",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ProductCard(
    producto: Producto,
    cantidad: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val sinStock = !producto.disponible || producto.stock <= 0
    val stockRestante = (producto.stock - cantidad).coerceAtLeast(0)
    val topeAlcanzado = cantidad >= producto.stock

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sinStock) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = producto.nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (sinStock) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "S/ ${"%.2f".format(producto.precio)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (sinStock) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = when {
                        sinStock -> MaterialTheme.colorScheme.errorContainer
                        stockRestante <= 5 -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (sinStock) "Agotado" else "Stock: $stockRestante",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            sinStock -> MaterialTheme.colorScheme.error
                            stockRestante <= 5 -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (cantidad > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Text("-", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = cantidad.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledIconButton(
                        onClick = onAdd,
                        enabled = !topeAlcanzado,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onAdd,
                    enabled = !sinStock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Text(if (sinStock) "Sin Stock" else "Agregar")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CatalogScreenPreview() {
    BarPOSTheme {
        CatalogScreen(onNavigateToCheckout = {})
    }
}
