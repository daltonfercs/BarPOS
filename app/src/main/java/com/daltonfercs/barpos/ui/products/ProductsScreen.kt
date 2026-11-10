package com.daltonfercs.barpos.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daltonfercs.barpos.model.Producto
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.viewModelScope
import com.daltonfercs.barpos.data.BarRepository

// UiState for robust state management
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class ProductsViewModel : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Producto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Producto>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            BarRepository.products.collect { prods ->
                _uiState.value = UiState.Success(prods)
            }
        }
    }

    fun addProduct(producto: Producto) {
        viewModelScope.launch {
            BarRepository.addProduct(producto)
        }
    }

    fun updateProduct(producto: Producto) {
        viewModelScope.launch {
            BarRepository.updateProduct(producto)
        }
    }

    fun deleteProduct(productoId: String) {
        viewModelScope.launch {
            BarRepository.deleteProduct(productoId)
        }
    }

    fun toggleDisponibilidad(productoId: String, disponible: Boolean) {
        BarRepository.toggleProductDisponibilidad(productoId, disponible)
    }

    fun updateStock(productoId: String, nuevoStock: Int) {
        BarRepository.updateProductStock(productoId, nuevoStock)
    }

    fun refreshProducts() {
        viewModelScope.launch {
            BarRepository.syncProducts()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(viewModel: ProductsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Todos") }
    val tabs = listOf("Todos", "Bebidas", "Comidas", "Snacks")

    var showAddDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Producto?>(null) }
    var productToDelete by remember { mutableStateOf<Producto?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Inventario de Productos", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { viewModel.refreshProducts() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    productToEdit = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo Producto", tint = Color.White)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Buscador
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar producto...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filtros
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab) }
                    )
                }
            }

            // Contenido reactivo
            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    val filteredList = state.data.filter {
                        (selectedTab == "Todos" || it.categoria == selectedTab) &&
                        it.nombre.contains(searchQuery, ignoreCase = true)
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredList, key = { it.id_producto }) { prod ->
                            ProductInventoryCard(
                                producto = prod,
                                onToggleDisponible = { disp ->
                                    viewModel.toggleDisponibilidad(prod.id_producto, disp)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            if (disp) "✅ \"${prod.nombre}\" activado"
                                            else "⛔ \"${prod.nombre}\" desactivado"
                                        )
                                    }
                                },
                                onStockChange = { newStock ->
                                    viewModel.updateStock(prod.id_producto, newStock)
                                },
                                onEditClick = {
                                    productToEdit = prod
                                    showAddDialog = true
                                },
                                onDeleteClick = {
                                    productToDelete = prod
                                }
                            )
                        }
                    }
                }
            }
        }

        // BottomSheet para crear o editar producto
        if (showAddDialog) {
            ModalBottomSheet(
                onDismissRequest = {
                    showAddDialog = false
                    productToEdit = null
                },
                sheetState = sheetState
            ) {
                AddProductForm(
                    initialProduct = productToEdit,
                    onSave = { prodGuardado ->
                        val isEditing = productToEdit != null
                        if (isEditing) {
                            viewModel.updateProduct(prodGuardado)
                        } else {
                            viewModel.addProduct(prodGuardado)
                        }
                        scope.launch {
                            sheetState.hide()
                            showAddDialog = false
                            productToEdit = null
                            snackbarHostState.showSnackbar(
                                if (isEditing) "✏️ Producto actualizado correctamente"
                                else "✅ Producto guardado exitosamente"
                            )
                        }
                    }
                )
            }
        }

        // Diálogo de confirmación para eliminar producto
        if (productToDelete != null) {
            val target = productToDelete!!
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                title = { Text("Eliminar Producto", fontWeight = FontWeight.Bold) },
                text = {
                    Text("¿Estás seguro de que deseas eliminar \"${target.nombre}\" del inventario? Esta acción enviará la eliminación al sistema.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val idBorrar = target.id.ifBlank { target.id_producto }
                            viewModel.deleteProduct(idBorrar)
                            productToDelete = null
                            scope.launch {
                                snackbarHostState.showSnackbar("🗑️ Producto \"${target.nombre}\" eliminado")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun ProductInventoryCard(
    producto: Producto,
    onToggleDisponible: (Boolean) -> Unit = {},
    onStockChange: (Int) -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val isActive = producto.disponible && producto.stock > 0
    val statusText = when {
        !producto.disponible -> "Stock: ${producto.stock} (Desactivado)"
        producto.stock <= 0 -> "Stock: 0 (Agotado)"
        else -> "Stock: ${producto.stock}"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = producto.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${producto.categoria} • ${producto.tipo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        FilledTonalIconButton(
                            onClick = { onStockChange((producto.stock - 1).coerceAtLeast(0)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalIconButton(
                            onClick = { onStockChange(producto.stock + 1) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "S/ ${"%.2f".format(producto.precio)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Switch(
                        checked = isActive,
                        onCheckedChange = { checked -> onToggleDisponible(checked) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Fila de acciones CRUD (Editar y Eliminar) alineada al final
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar producto",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Eliminar producto",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddProductForm(
    initialProduct: Producto? = null,
    onSave: (Producto) -> Unit
) {
    val isEditing = initialProduct != null
    var nombre by remember(initialProduct) { mutableStateOf(initialProduct?.nombre ?: "") }
    var categoria by remember(initialProduct) { mutableStateOf(initialProduct?.categoria ?: "Bebidas") }
    var tipo by remember(initialProduct) { mutableStateOf(initialProduct?.tipo ?: "") }
    var precio by remember(initialProduct) {
        mutableStateOf(if (initialProduct != null) "%.2f".format(java.util.Locale.US, initialProduct.precio) else "")
    }
    var stock by remember(initialProduct) {
        mutableStateOf(initialProduct?.stock?.toString() ?: "")
    }

    val parsedPrecio = precio.replace(",", ".").toDoubleOrNull()
    val parsedStock = stock.toIntOrNull()
    val isFormValid = nombre.isNotBlank() && tipo.isNotBlank() && parsedPrecio != null && parsedStock != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isEditing) "Editar Producto" else "Nuevo Producto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre del Producto") },
            modifier = Modifier.fillMaxWidth(),
            isError = nombre.isBlank()
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Categoría:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Bebidas", "Comidas", "Snacks").forEach { cat ->
                    FilterChip(
                        selected = categoria == cat,
                        onClick = { categoria = cat },
                        label = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (categoria == cat) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OutlinedTextField(
            value = tipo,
            onValueChange = { tipo = it },
            label = { Text("Tipo (ej. Trago, Fondo)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = precio,
                onValueChange = { precio = it },
                label = { Text("Precio (S/)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                isError = precio.isNotEmpty() && parsedPrecio == null
            )
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it },
                label = { Text("Stock") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                isError = stock.isNotEmpty() && parsedStock == null
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val stockVal = parsedStock ?: 0
                val prodResult = Producto(
                    id = initialProduct?.id?.ifBlank { initialProduct.id_producto }
                        ?: System.currentTimeMillis().toString(),
                    nombre = nombre.trim(),
                    categoria = categoria,
                    tipo = tipo.trim(),
                    precio = parsedPrecio ?: 0.0,
                    disponible = if (isEditing) (initialProduct!!.disponible && stockVal > 0) else stockVal > 0,
                    stock = stockVal
                )
                onSave(prodResult)
            },
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = if (isEditing) "Actualizar Producto" else "Guardar Producto",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview
@Composable
fun ProductsScreenPreview() {
    BarPOSTheme {
        ProductsScreen()
    }
}
