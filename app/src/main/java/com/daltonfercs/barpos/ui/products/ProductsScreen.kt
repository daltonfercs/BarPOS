package com.daltonfercs.barpos.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Inventario de Productos") },
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
                onClick = { showAddDialog = true },
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
                        items(filteredList) { prod ->
                            ProductInventoryCard(
                                producto = prod,
                                onToggleDisponible = { disp ->
                                    viewModel.toggleDisponibilidad(prod.id_producto, disp)
                                },
                                onStockChange = { newStock ->
                                    viewModel.updateStock(prod.id_producto, newStock)
                                }
                            )
                        }
                    }
                }
            }
        }

        // BottomSheet para crear producto
        if (showAddDialog) {
            ModalBottomSheet(
                onDismissRequest = { showAddDialog = false },
                sheetState = sheetState
            ) {
                AddProductForm(
                    onSave = { nuevoProd ->
                        viewModel.addProduct(nuevoProd)
                        scope.launch {
                            sheetState.hide()
                            showAddDialog = false
                            snackbarHostState.showSnackbar("Producto guardado exitosamente")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProductInventoryCard(
    producto: Producto,
    onToggleDisponible: (Boolean) -> Unit = {},
    onStockChange: (Int) -> Unit = {}
) {
    val sinStock = !producto.disponible || producto.stock <= 0

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
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
                        text = if (sinStock) "Stock: ${producto.stock} (Agotado)" else "Stock: ${producto.stock}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sinStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
                Spacer(modifier = Modifier.height(8.dp))
                Switch(
                    checked = producto.disponible && producto.stock > 0,
                    onCheckedChange = { checked -> onToggleDisponible(checked) }
                )
            }
        }
    }
}

@Composable
fun AddProductForm(onSave: (Producto) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("Bebidas") }
    var tipo by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }

    val isFormValid = nombre.isNotBlank() && tipo.isNotBlank() && precio.toDoubleOrNull() != null && stock.toIntOrNull() != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Nuevo Producto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre del Producto") },
            modifier = Modifier.fillMaxWidth(),
            isError = nombre.isBlank()
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Categoría:", modifier = Modifier.padding(end = 16.dp))
            listOf("Bebidas", "Comidas", "Snacks").forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    RadioButton(selected = categoria == cat, onClick = { categoria = cat })
                    Text(cat)
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
                isError = precio.isNotEmpty() && precio.toDoubleOrNull() == null
            )
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it },
                label = { Text("Stock Inicial") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                isError = stock.isNotEmpty() && stock.toIntOrNull() == null
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val newProd = Producto(
                    id = System.currentTimeMillis().toString(),
                    nombre = nombre,
                    categoria = categoria,
                    tipo = tipo,
                    precio = precio.toDouble(),
                    disponible = true,
                    stock = stock.toInt()
                )
                onSave(newProd)
            },
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Guardar Producto")
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
