package com.example.barpos.ui.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.barpos.ui.theme.BarPOSTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    total: Double = 125.0,
    onConfirm: (metodoPago: String) -> Unit = {}
) {
    var metodoPagoSeleccionado by remember { mutableStateOf("Yape") }
    val metodos = listOf("Efectivo", "Yape", "Mixto")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen de Comanda") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Button(
                    onClick = { onConfirm(metodoPagoSeleccionado) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirmar y Enviar a Cocina", color = Color.White)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Total a Cobrar", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "S/ ${"%.2f".format(total)}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Método de Pago", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(16.dp))
            
            metodos.forEach { metodo ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = metodoPagoSeleccionado == metodo,
                        onClick = { metodoPagoSeleccionado = metodo },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(metodo, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Preview
@Composable
fun SummaryScreenPreview() {
    BarPOSTheme {
        SummaryScreen()
    }
}
