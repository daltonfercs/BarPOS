package com.example.barpos.ui.catalog

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CatalogViewModelTest {
  @Test
  fun catalogViewModel_initialState_hasProducts() = runTest {
    val viewModel = CatalogViewModel()
    val state = viewModel.uiState.value
    assertEquals("Bebidas", state.categoriaSeleccionada)
    assertEquals(5, state.productos.size)
  }

  @Test
  fun catalogViewModel_setCategoria_updatesSelection() = runTest {
    val viewModel = CatalogViewModel()
    viewModel.setCategoria("Comidas")
    assertEquals("Comidas", viewModel.uiState.value.categoriaSeleccionada)
  }
}
