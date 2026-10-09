package com.example.barpos.ui.roles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectionScreen(
    onNavigateToCatalog: () -> Unit,
    onNavigateToKitchen: () -> Unit,
    onNavigateToReports: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BarPOS",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Selecciona tu rol para continuar",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))

        RoleButton("Caja / Mesero (Salón)", onNavigateToCatalog)
        Spacer(modifier = Modifier.height(16.dp))
        RoleButton("Cocina (KDS)", onNavigateToKitchen)
        Spacer(modifier = Modifier.height(16.dp))
        RoleButton("Administrador (Reportes)", onNavigateToReports)
    }
}

@Composable
fun RoleButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
