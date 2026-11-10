package com.daltonfercs.barpos.ui.reports

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daltonfercs.barpos.data.BarRepository
import com.daltonfercs.barpos.model.PedidoItem
import com.daltonfercs.barpos.ui.theme.BarPOSTheme
import com.daltonfercs.barpos.ui.theme.SuccessGreen
import com.daltonfercs.barpos.ui.theme.WarningRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReportsUiState(
    val selectedDate: String = "",
    val isLoading: Boolean = false,
    val totalRecaudado: Double = 0.0,
    val totalYape: Double = 0.0,
    val totalEfectivo: Double = 0.0,
    val totalComida: Double = 0.0,
    val cantidadComida: Int = 0,
    val totalBebida: Double = 0.0,
    val cantidadBebida: Int = 0,
    val totalPendiente: Double = 0.0,
    val ordenes: List<PedidoItem> = emptyList()
)

class ReportsViewModel : ViewModel() {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _uiState = MutableStateFlow(ReportsUiState(selectedDate = BarRepository.getTodayString()))
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        cargarReporte(_uiState.value.selectedDate)
    }

    fun cargarReporte(fecha: String) {
        _uiState.value = _uiState.value.copy(selectedDate = fecha, isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val pedidos = BarRepository.getOrdersForDate(fecha)

            val pagados = pedidos.filter { it.estadoPago == "Pagado" }
            val pendientes = pedidos.filter { it.estadoPago == "Pendiente" }

            val totalRecaudado = pagados.sumOf { it.totalItem }

            // Sumar correctamente Yape y Efectivo incluyendo los cobros en modo "Mixto"
            val totalYape = pagados.sumOf { item ->
                when {
                    item.metodoPago.equals("Yape", ignoreCase = true) -> item.totalItem
                    item.metodoPago.contains("Mixto", ignoreCase = true) -> {
                        if (item.montoYape > 0.0 || item.montoEfectivo > 0.0) item.montoYape else item.totalItem / 2.0
                    }
                    else -> 0.0
                }
            }

            val totalEfectivo = pagados.sumOf { item ->
                when {
                    item.metodoPago.equals("Efectivo", ignoreCase = true) -> item.totalItem
                    item.metodoPago.contains("Mixto", ignoreCase = true) -> {
                        if (item.montoEfectivo > 0.0 || item.montoYape > 0.0) item.montoEfectivo else item.totalItem / 2.0
                    }
                    else -> 0.0
                }
            }

            val comidas = pagados.filter {
                it.categoria.contains("Comida", ignoreCase = true) || it.categoria.contains("Snack", ignoreCase = true)
            }
            val totalComida = comidas.sumOf { it.totalItem }
            val cantidadComida = comidas.sumOf { it.cantidad }

            val bebidas = pagados.filter { it.categoria.contains("Bebida", ignoreCase = true) }
            val totalBebida = bebidas.sumOf { it.totalItem }
            val cantidadBebida = bebidas.sumOf { it.cantidad }

            val totalPendiente = pendientes.sumOf { it.totalItem }

            _uiState.value = ReportsUiState(
                selectedDate = fecha,
                isLoading = false,
                totalRecaudado = totalRecaudado,
                totalYape = totalYape,
                totalEfectivo = totalEfectivo,
                totalComida = totalComida,
                cantidadComida = cantidadComida,
                totalBebida = totalBebida,
                cantidadBebida = cantidadBebida,
                totalPendiente = totalPendiente,
                ordenes = pedidos
            )
        }
    }

    fun irDiaAnterior() {
        try {
            val cal = Calendar.getInstance()
            cal.time = dayFormat.parse(_uiState.value.selectedDate) ?: Date()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cargarReporte(dayFormat.format(cal.time))
        } catch (_: Exception) { }
    }

    fun irDiaSiguiente() {
        try {
            val cal = Calendar.getInstance()
            cal.time = dayFormat.parse(_uiState.value.selectedDate) ?: Date()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cargarReporte(dayFormat.format(cal.time))
        } catch (_: Exception) { }
    }

    fun irHoy() {
        cargarReporte(BarRepository.getTodayString())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val hoy = BarRepository.getTodayString()
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cierre de Caja & Reportes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { viewModel.cargarReporte(state.selectedDate) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selector de Fecha
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.irDiaAnterior() }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Día Anterior")
                        }

                        Button(
                            onClick = { showDatePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Calendario", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.selectedDate == hoy) "Hoy (${state.selectedDate})" else state.selectedDate,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { viewModel.irDiaSiguiente() }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Día Siguiente")
                        }
                    }
                }
            }

            // Botones de Exportación (PDF y CSV)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val fileName = withContext(Dispatchers.IO) {
                                    exportReportToPdf(context, state)
                                }
                                if (fileName != null) {
                                    snackbarHostState.showSnackbar("📄 PDF guardado en Descargas: $fileName")
                                } else {
                                    snackbarHostState.showSnackbar("❌ Error al generar el archivo PDF")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "📄 Descargar PDF",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val fileName = withContext(Dispatchers.IO) {
                                    exportReportToCsv(context, state)
                                }
                                if (fileName != null) {
                                    snackbarHostState.showSnackbar("📊 CSV guardado en Descargas: $fileName")
                                } else {
                                    snackbarHostState.showSnackbar("❌ Error al generar el archivo CSV")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "📊 Descargar CSV",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // 1. Total Recaudado (Ventas del día)
                item {
                    ReportCard(
                        title = "💵 Total Recaudado (Ventas del Día)",
                        amount = state.totalRecaudado,
                        isMain = true,
                        badgeText = "Pagado"
                    )
                }

                // 2 & 3. Desglose Yape vs Efectivo
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ReportCard(
                            title = "📱 Total Yape / Digital",
                            amount = state.totalYape,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cardColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                        ReportCard(
                            title = "🪙 Total en Efectivo (Caja)",
                            amount = state.totalEfectivo,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            cardColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    }
                }

                // 4 & 5. Total Comida vs Bebidas
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ReportCard(
                            title = "🍽️ Comida Vendida",
                            amount = state.totalComida,
                            subtitle = "${state.cantidadComida} platos servidos",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        ReportCard(
                            title = "🍻 Bebidas Vendidas",
                            amount = state.totalBebida,
                            subtitle = "${state.cantidadBebida} unidades",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                // 6. Total Pendiente de Cobro
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    "⚠️ Total Pendiente de Cobro",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Mesas abiertas o comandas sin pagar en esta fecha",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "S/ ${"%.2f".format(state.totalPendiente)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Lista de pedidos del día
                if (state.ordenes.isNotEmpty()) {
                    item {
                        Text(
                            "Detalle de Comandas (${state.ordenes.size} ítems)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(state.ordenes) { pedido ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        "${pedido.cantidad}x ${pedido.producto}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${pedido.idPedido} • ${pedido.idMesa} • ${pedido.tipoOrden}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "S/ ${"%.2f".format(pedido.totalItem)}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Surface(
                                        color = if (pedido.estadoPago == "Pagado") SuccessGreen else WarningRed,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = pedido.estadoPago,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo simple para seleccionar fecha
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = millis
                            // Compensar zona horaria UTC de DatePickerState
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            viewModel.cargarReporte(sdf.format(cal.time))
                        }
                        showDatePicker = false
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun ReportCard(
    title: String,
    amount: Double,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    isMain: Boolean = false,
    badgeText: String? = null,
    cardColor: Color = MaterialTheme.colorScheme.surface
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = if (isMain) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMain) Arrangement.Center else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                badgeText?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "S/ ${"%.2f".format(amount)}",
                style = if (isMain) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview
@Composable
fun ReportsScreenPreview() {
    BarPOSTheme {
        ReportsScreen()
    }
}

private fun openDownloadOutputStream(
    context: Context,
    fileName: String,
    mimeType: String
): OutputStream? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let { context.contentResolver.openOutputStream(it) }
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        FileOutputStream(File(downloadsDir, fileName))
    }
}

fun exportReportToCsv(context: Context, state: ReportsUiState): String? {
    return try {
        val safeDate = state.selectedDate.ifBlank { BarRepository.getTodayString() }
        val fileName = "Reporte_Caja_${safeDate}.csv"
        val out = openDownloadOutputStream(context, fileName, "text/csv") ?: return null
        out.use { stream ->
            val sb = StringBuilder()
            // UTF-8 BOM para que Excel reconozca tildes y caracteres especiales
            sb.append('\uFEFF')
            sb.appendLine("REPORTE DE CIERRE DE CAJA - BARPOS")
            sb.appendLine("Fecha,${safeDate}")
            sb.appendLine("Total Recaudado (Pagado),S/ ${"%.2f".format(Locale.US, state.totalRecaudado)}")
            sb.appendLine("Total Yape / Digital,S/ ${"%.2f".format(Locale.US, state.totalYape)}")
            sb.appendLine("Total en Efectivo (Caja),S/ ${"%.2f".format(Locale.US, state.totalEfectivo)}")
            sb.appendLine("Comida Vendida (${state.cantidadComida} platos),S/ ${"%.2f".format(Locale.US, state.totalComida)}")
            sb.appendLine("Bebidas Vendidas (${state.cantidadBebida} uds),S/ ${"%.2f".format(Locale.US, state.totalBebida)}")
            sb.appendLine("Total Pendiente de Cobro,S/ ${"%.2f".format(Locale.US, state.totalPendiente)}")
            sb.appendLine()
            sb.appendLine("ID_Pedido,Fecha_Hora,Mesa_o_Destino,Categoria,Producto,Cantidad,Precio_Unitario,Total_Item,Tipo_Orden,Metodo_Pago,Monto_Efectivo,Monto_Yape,Estado_Cocina,Estado_Pago")
            state.ordenes.forEach { item ->
                val prodClean = item.producto.replace("\"", "\"\"")
                sb.appendLine(
                    "\"${item.idPedido}\",\"${item.fechaHora}\",\"${item.idMesa}\",\"${item.categoria}\",\"$prodClean\",${item.cantidad},${"%.2f".format(Locale.US, item.precioUnitario)},${"%.2f".format(Locale.US, item.totalItem)},\"${item.tipoOrden}\",\"${item.metodoPago}\",${"%.2f".format(Locale.US, item.montoEfectivo)},${"%.2f".format(Locale.US, item.montoYape)},\"${item.estadoCocina}\",\"${item.estadoPago}\""
                )
            }
            stream.write(sb.toString().toByteArray(Charsets.UTF_8))
            stream.flush()
        }
        fileName
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun exportReportToPdf(context: Context, state: ReportsUiState): String? {
    return try {
        val safeDate = state.selectedDate.ifBlank { BarRepository.getTodayString() }
        val fileName = "Reporte_Caja_${safeDate}.pdf"
        val pdfDocument = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = android.graphics.Color.BLACK
        }
        val subtitlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = android.graphics.Color.DKGRAY
        }
        val bodyPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 11f
            color = android.graphics.Color.BLACK
        }
        val boldBodyPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = android.graphics.Color.BLACK
        }
        val linePaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
        }

        var y = 45f
        canvas.drawText("BARPOS - REPORTE DE CIERRE DE CAJA", 40f, y, titlePaint)
        y += 22f
        canvas.drawText("Fecha del Reporte: $safeDate", 40f, y, subtitlePaint)
        y += 15f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 22f

        canvas.drawText("RESUMEN DE CAJA", 40f, y, subtitlePaint)
        y += 20f
        canvas.drawText("Total Recaudado (Pagado):", 40f, y, boldBodyPaint)
        canvas.drawText("S/ ${"%.2f".format(Locale.US, state.totalRecaudado)}", 240f, y, boldBodyPaint)
        y += 18f
        canvas.drawText("Total Yape / Digital:", 40f, y, bodyPaint)
        canvas.drawText("S/ ${"%.2f".format(Locale.US, state.totalYape)}", 240f, y, bodyPaint)
        y += 18f
        canvas.drawText("Total en Efectivo (Caja):", 40f, y, bodyPaint)
        canvas.drawText("S/ ${"%.2f".format(Locale.US, state.totalEfectivo)}", 240f, y, bodyPaint)
        y += 18f
        canvas.drawText("Comida Vendida (${state.cantidadComida} platos):", 40f, y, bodyPaint)
        canvas.drawText("S/ ${"%.2f".format(Locale.US, state.totalComida)}", 240f, y, bodyPaint)
        y += 18f
        canvas.drawText("Bebidas Vendidas (${state.cantidadBebida} uds):", 40f, y, bodyPaint)
        canvas.drawText("S/ ${"%.2f".format(Locale.US, state.totalBebida)}", 240f, y, bodyPaint)
        y += 18f
        canvas.drawText("Total Pendiente de Cobro:", 40f, y, boldBodyPaint)
        canvas.drawText("S/ ${"%.2f".format(Locale.US, state.totalPendiente)}", 240f, y, boldBodyPaint)
        y += 18f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 24f

        canvas.drawText("DETALLE DE COMANDAS (${state.ordenes.size} ítems)", 40f, y, subtitlePaint)
        y += 20f

        canvas.drawText("Pedido / Mesa", 40f, y, boldBodyPaint)
        canvas.drawText("Producto", 165f, y, boldBodyPaint)
        canvas.drawText("Método", 360f, y, boldBodyPaint)
        canvas.drawText("Estado", 430f, y, boldBodyPaint)
        canvas.drawText("Total", 500f, y, boldBodyPaint)
        y += 8f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 16f

        for (item in state.ordenes) {
            if (y > pageHeight - 50) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 45f
            }
            val pedidoMesa = "${item.idPedido} (${item.idMesa})".take(20)
            val prodText = "${item.cantidad}x ${item.producto}".take(30)
            canvas.drawText(pedidoMesa, 40f, y, bodyPaint)
            canvas.drawText(prodText, 165f, y, bodyPaint)
            canvas.drawText(item.metodoPago.take(10), 360f, y, bodyPaint)
            canvas.drawText(item.estadoPago.take(10), 430f, y, bodyPaint)
            canvas.drawText("S/ ${"%.2f".format(Locale.US, item.totalItem)}", 500f, y, bodyPaint)
            y += 16f
        }

        pdfDocument.finishPage(page)

        val out = openDownloadOutputStream(context, fileName, "application/pdf")
        if (out == null) {
            pdfDocument.close()
            return null
        }
        out.use { stream ->
            pdfDocument.writeTo(stream)
            stream.flush()
        }
        pdfDocument.close()
        fileName
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
