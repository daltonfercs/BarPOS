package com.example.barpos.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.barpos.model.Producto
import com.example.barpos.ui.theme.BarPOSTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// UiState
data class CatalogUiState(
    val productos: List<Producto> = emptyList(),
    val categorias: List<String> = listOf("Bebidas", "Comidas", "Snacks"),
    val categoriaSeleccionada: String = "Bebidas",
    val carrito: Map<Producto, Int> = emptyMap(),
    val tipoAtencion: String = "En Mesa 1"
) {
    val total: Double
        get() = carrito.entries.sumOf { it.key.precio * it.value }
    val cantidadTotal: Int
        get() = carrito.values.sum()
}

// ViewModel (Mock for now)
class CatalogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        // Load mock data
        _uiState.value = _uiState.value.copy(
            productos = listOf(
                Producto("1", "Cerveza Artesanal", "Bebidas", 15.0),
                Producto("2", "Pisco Sour", "Bebidas", 20.0),
                Producto("3", "Hamburguesa Clásica", "Comidas", 25.0),
                Producto("4", "Alitas BBQ", "Comidas", 22.0),
                Producto("5", "Nachos", "Snacks", 18.0)
            )
        )
    }

    fun setCategoria(categoria: String) {
        _uiState.value = _uiState.value.copy(categoriaSeleccionada = categoria)
    }

    fun agregarAlCarrito(producto: Producto) {
        val currentCart = _uiState.value.carrito.toMutableMap()
        currentCart[producto] = (currentCart[producto] ?: 0) + 1
        _uiState.value = _uiState.value.copy(carrito = currentCart)
    }

    fun removerDelCarrito(producto: Producto) {
        val currentCart = _uiState.value.carrito.toMutableMap()
        val currentCount = currentCart[producto] ?: 0
        if (currentCount > 1) {
            currentCart[producto] = currentCount - 1
        } else {
            currentCart.remove(producto)
        }
        _uiState.value = _uiState.value.copy(carrito = currentCart)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToCheckout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BarPOS - ${uiState.tipoAtencion}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState.cantidadTotal > 0) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${uiState.cantidadTotal} items", style = MaterialTheme.typography.bodyMedium)
                            Text("Total: S/ ${"%.2f".format(uiState.total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onNavigateToCheckout,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cobrar", color = Color.White)
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
            // Categories
            ScrollableTabRow(
                selectedTabIndex = uiState.categorias.indexOf(uiState.categoriaSeleccionada),
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 8.dp
            ) {
                uiState.categorias.forEachIndexed { index, categoria ->
                    Tab(
                        selected = uiState.categoriaSeleccionada == categoria,
                        onClick = { viewModel.setCategoria(categoria) },
                        text = { Text(categoria) }
                    )
                }
            }

            // Products Grid
            val productosFiltrados = uiState.productos.filter { it.categoria == uiState.categoriaSeleccionada }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(productosFiltrados) { producto ->
                    val cantidad = uiState.carrito[producto] ?: 0
                    ProductCard(
                        producto = producto,
                        cantidad = cantidad,
                        onAdd = { viewModel.agregarAlCarrito(producto) },
                        onRemove = { viewModel.removerDelCarrito(producto) }
                    )
                }
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = producto.nombre, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "S/ ${"%.2f".format(producto.precio)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            
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
                        Text("-", color = Color.White)
                    }
                    Text(text = cantidad.toString(), style = MaterialTheme.typography.titleMedium)
                    FilledIconButton(
                        onClick = onAdd,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("+", color = Color.White)
                    }
                }
            } else {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Text("Agregar")
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
