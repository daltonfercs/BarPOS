package com.daltonfercs.barpos.data

import android.content.Context
import android.util.Log
import com.daltonfercs.barpos.api.NetworkModule
import com.daltonfercs.barpos.data.local.*
import com.daltonfercs.barpos.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

object BarRepository {
    private const val TAG = "BarRepository"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    @Volatile
    private var localDao: BarPosDao? = null
    private val isLocalDbInitialized = AtomicBoolean(false)

    // Almacena el instante exacto (epoch ms) en que cada pedido fue enviado a cocina
    private val orderStartTimes = ConcurrentHashMap<String, Long>()

    // Almacena la cantidad de tápers modificada manualmente por pedido en Stand By / Cobro
    private val modifiedTapersByOrder = ConcurrentHashMap<String, Int>()

    fun isTaperItem(item: PedidoItem): Boolean {
        return item.categoria.equals("Empaque", ignoreCase = true) ||
                item.producto.contains("Táper", ignoreCase = true) ||
                item.producto.contains("Taper", ignoreCase = true)
    }

    private fun applyTapersToOrderList(list: List<PedidoItem>, idPedido: String, nuevaCantidad: Int): List<PedidoItem> {
        val cant = nuevaCantidad.coerceAtLeast(0)
        val orderItems = list.filter { it.idPedido == idPedido }
        if (orderItems.isEmpty()) return list

        val firstItem = orderItems.first()
        val existingTaper = orderItems.firstOrNull { isTaperItem(it) }
        val result = mutableListOf<PedidoItem>()
        var taperInserted = false

        for (item in list) {
            if (item.idPedido != idPedido) {
                result.add(item)
            } else {
                if (isTaperItem(item)) {
                    if (cant > 0 && !taperInserted) {
                        val precioUnit = if (item.precioUnitario > 0.0) item.precioUnitario else 1.0
                        result.add(
                            item.copy(
                                cantidad = cant,
                                precioUnitario = precioUnit,
                                totalItem = cant * precioUnit
                            )
                        )
                        taperInserted = true
                    }
                    // Si cant == 0, se omite el ítem de táper
                } else {
                    result.add(item)
                }
            }
        }

        // Si no existía un ítem de táper previo en ese pedido y cant > 0, lo agregamos
        if (cant > 0 && !taperInserted) {
            val newTaperItem = (existingTaper ?: firstItem).copy(
                categoria = "Empaque",
                producto = "Táper para llevar",
                cantidad = cant,
                precioUnitario = 1.0,
                totalItem = cant * 1.0,
                estadoCocina = "Listo"
            )
            val lastIdx = result.indexOfLast { it.idPedido == idPedido }
            if (lastIdx >= 0) {
                result.add(lastIdx + 1, newTaperItem)
            } else {
                result.add(newTaperItem)
            }
        }

        return result
    }

    private fun applyAllModifiedTapers(list: List<PedidoItem>): List<PedidoItem> {
        var current = list
        for ((idPed, cant) in modifiedTapersByOrder) {
            current = applyTapersToOrderList(current, idPed, cant)
        }
        return current
    }

    /**
     * Permite modificar en tiempo real la cantidad de tápers de un pedido Delivery (en Stand By o al cobrar).
     */
    fun actualizarTapersOrden(idPedido: String, nuevaCantidad: Int) {
        val cant = nuevaCantidad.coerceAtLeast(0)
        modifiedTapersByOrder[idPedido] = cant
        _orders.value = applyTapersToOrderList(_orders.value, idPedido, cant)
    }

    /**
     * Obtiene el instante exacto (en milisegundos) en que se creó/envió el pedido.
     * Si fue enviado desde la app, retorna el timestamp registrado al presionar "Enviar".
     * Si proviene de Google Sheets, intenta parsear Fecha_Hora; si no tiene hora, memoriza el instante de primera detección.
     */
    fun getOrderStartTimeMillis(idPedido: String, fechaHora: String): Long {
        orderStartTimes[idPedido]?.let { return it }

        val now = System.currentTimeMillis()
        val parsedMillis = parseFechaHoraToMillis(fechaHora)
        val startMillis = if (parsedMillis != null && parsedMillis in (now - 24 * 3600 * 1000L)..(now + 60_000L)) {
            parsedMillis.coerceAtMost(now)
        } else {
            now
        }
        orderStartTimes.putIfAbsent(idPedido, startMillis)
        return orderStartTimes[idPedido] ?: startMillis
    }

    private fun parseFechaHoraToMillis(fechaHora: String): Long? {
        val raw = fechaHora.trim()
        if (raw.isBlank()) return null

        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss" to Locale.getDefault(),
            "dd/MM/yyyy HH:mm:ss" to Locale.getDefault(),
            "yyyy-MM-dd HH:mm" to Locale.getDefault(),
            "dd/MM/yyyy HH:mm" to Locale.getDefault()
        )
        for ((pattern, locale) in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, locale)
                val date = sdf.parse(raw)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        // Intentar formatos ISO 8601 (ej. devueltos por Google Sheets en UTC)
        val isoPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in isoPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(raw)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    private val _tables = MutableStateFlow<List<TableModel>>(
        listOf(
            TableModel("M-01", 1, 4, "Libre"),
            TableModel("M-02", 2, 2, "Libre"),
            TableModel("M-03", 3, 4, "Libre"),
            TableModel("M-04", 4, 6, "Libre"),
            TableModel("M-05", 5, 2, "Libre"),
            TableModel("M-06", 6, 8, "Libre")
        )
    )
    val tables: StateFlow<List<TableModel>> = _tables.asStateFlow()

    private val _products = MutableStateFlow<List<Producto>>(
        listOf(
            Producto("1", "Cerveza Cusqueña", "Bebidas", "Cerveza", 12.0, true, 50),
            Producto("2", "Pisco Sour Catedral", "Bebidas", "Trago", 22.0, true, 30),
            Producto("3", "Chilcano Clásico", "Bebidas", "Trago", 18.0, true, 40),
            Producto("4", "Gaseosa 500ml", "Bebidas", "Sin Alcohol", 6.0, true, 60),
            Producto("5", "Agua Mineral", "Bebidas", "Sin Alcohol", 5.0, true, 40),
            Producto("6", "Alitas BBQ (8 uds)", "Comidas", "Piqueo", 25.0, true, 20),
            Producto("7", "Hamburguesa Clásica", "Comidas", "Fondo", 24.0, true, 25),
            Producto("8", "Salchipapa Especial", "Comidas", "Fondo", 20.0, true, 30),
            Producto("9", "Tequeños de Queso (10 uds)", "Comidas", "Piqueo", 18.0, true, 25),
            Producto("10", "Nachos con Queso & Guacamole", "Snacks", "Snack", 16.0, true, 30),
            Producto("11", "Chicharrón de Pollo", "Comidas", "Fondo", 26.0, true, 15)
        )
    )
    val products: StateFlow<List<Producto>> = _products.asStateFlow()

    private val _orders = MutableStateFlow<List<PedidoItem>>(emptyList())
    val orders: StateFlow<List<PedidoItem>> = _orders.asStateFlow()

    /**
     * Inicializa la base de datos local Room (SQLite) en el celular.
     * Carga mesas, productos y pedidos guardados localmente y suscribe los flujos para persistir cualquier cambio.
     */
    fun initLocalDatabase(context: Context) {
        if (!isLocalDbInitialized.compareAndSet(false, true)) return

        val dao = BarPosRoomDatabase.getInstance(context.applicationContext).barPosDao()
        localDao = dao

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Cargar o sembrar Mesas locales
                val savedMesas = dao.getAllMesas()
                if (savedMesas.isNotEmpty()) {
                    _tables.value = savedMesas.map { it.toDomain() }
                } else {
                    dao.insertMesas(_tables.value.map { it.toEntity() })
                }

                // 2. Cargar o sembrar Productos locales
                val savedProds = dao.getAllProductos()
                if (savedProds.isNotEmpty()) {
                    _products.value = savedProds.map { it.toDomain() }
                } else {
                    dao.insertProductos(_products.value.map { it.toEntity() })
                }

                // 3. Cargar Pedidos locales y restaurar cronómetros de cocina
                val savedPedidos = dao.getAllPedidos()
                if (savedPedidos.isNotEmpty()) {
                    savedPedidos.forEach { entity ->
                        if (entity.idPedido.isNotBlank() && entity.startTimeMillis > 0L) {
                            orderStartTimes.putIfAbsent(entity.idPedido, entity.startTimeMillis)
                        }
                    }
                    _orders.value = savedPedidos.map { it.toDomain() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando datos iniciales de Room: ${e.message}", e)
            }

            // 4. Observar cambios reactivos para persistir automáticamente en Room en el celular
            launch {
                _tables.collect { list ->
                    try {
                        dao.insertMesas(list.map { it.toEntity() })
                    } catch (e: Exception) {
                        Log.e(TAG, "Error guardando mesas en Room: ${e.message}")
                    }
                }
            }
            launch {
                _products.collect { list ->
                    try {
                        dao.insertProductos(list.map { it.toEntity() })
                    } catch (e: Exception) {
                        Log.e(TAG, "Error guardando productos en Room: ${e.message}")
                    }
                }
            }
            launch {
                _orders.collect { list ->
                    try {
                        val entities = list.map { item ->
                            val startMs = if (item.idPedido.isNotBlank()) {
                                getOrderStartTimeMillis(item.idPedido, item.fechaHora)
                            } else {
                                System.currentTimeMillis()
                            }
                            item.toEntity(startMs)
                        }
                        dao.clearAndReplaceAllPedidos(entities)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error guardando pedidos en Room: ${e.message}")
                    }
                }
            }

            // 5. Intentar sincronizar con Google Sheets en segundo plano (sin perder datos locales)
            syncTables()
            syncProducts()
            fetchAllOrders()
        }
    }

    fun getTodayString(): String = dayFormat.format(Date())

    suspend fun syncTables() = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.apiService.getTables()
            if (response.isSuccessful) {
                val remoteTables = response.body()?.data
                if (!remoteTables.isNullOrEmpty()) {
                    val localMap = _tables.value.associateBy { it.idMesa }
                    val mergedRemote = remoteTables.map { remote ->
                        val local = localMap[remote.idMesa]
                        val hasPendingOrders = _orders.value.any { ord ->
                            ord.estadoPago.equals("Pendiente", ignoreCase = true) && (
                                    ord.idMesa.equals(remote.idMesa, ignoreCase = true) ||
                                            ord.idMesa.equals("Mesa ${remote.numero}", ignoreCase = true) ||
                                            ord.idMesa == remote.numero.toString()
                                    )
                        }
                        if (hasPendingOrders || local?.estado == "Ocupada") {
                            remote.copy(estado = "Ocupada")
                        } else {
                            remote
                        }
                    }
                    val remoteIds = mergedRemote.map { it.idMesa }.toSet()
                    val onlyLocal = _tables.value.filter { it.idMesa !in remoteIds }
                    _tables.value = (mergedRemote + onlyLocal).sortedBy { it.numero }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Modo Local activo (sin conexión a Sheets para mesas): ${e.message}")
        }
    }

    suspend fun syncProducts() = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.apiService.getProducts()
            if (response.isSuccessful) {
                val remoteProds = response.body()?.data
                if (!remoteProds.isNullOrEmpty()) {
                    val localMap = _products.value.associateBy { it.id_producto }
                    val mergedRemote = remoteProds.map { remote ->
                        val local = localMap[remote.id_producto]
                        // Si localmente se descontó stock o se editó, conservar el menor stock o estado local
                        if (local != null && local.stock != remote.stock) {
                            local
                        } else {
                            remote
                        }
                    }
                    val remoteIds = mergedRemote.map { it.id_producto }.toSet()
                    val onlyLocal = _products.value.filter { it.id_producto !in remoteIds }
                    _products.value = mergedRemote + onlyLocal
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Modo Local activo (sin conexión a Sheets para productos): ${e.message}")
        }
    }

    /**
     * Consulta todos los pedidos del backend.
     * Endpoint: GET /exec?tipo=pedidos
     */
    suspend fun fetchAllOrders(): List<PedidoItem> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.apiService.getAllOrders()
            if (response.isSuccessful) {
                val remoteOrders = response.body()?.data
                if (!remoteOrders.isNullOrEmpty()) {
                    // Combinar con órdenes locales pendientes que aún no se hayan reflejado o priorizar estado local si ya fue pagado/listo
                    val localMap = _orders.value.associateBy { "${it.idPedido}_${it.producto}" }
                    val mergedRemote = remoteOrders.map { remote ->
                        val local = localMap["${remote.idPedido}_${remote.producto}"]
                        val effectiveFechaHora = local?.fechaHora?.takeIf { it.isNotBlank() } ?: remote.fechaHora
                        if (remote.idPedido.isNotBlank()) {
                            getOrderStartTimeMillis(remote.idPedido, effectiveFechaHora)
                        }
                        if (local != null) {
                            remote.copy(
                                fechaHora = effectiveFechaHora,
                                estadoPago = if (local.estadoPago == "Pagado") "Pagado" else remote.estadoPago,
                                metodoPago = if (local.estadoPago == "Pagado") local.metodoPago else remote.metodoPago,
                                montoEfectivo = if (local.estadoPago == "Pagado") local.montoEfectivo else remote.montoEfectivo,
                                montoYape = if (local.estadoPago == "Pagado") local.montoYape else remote.montoYape,
                                estadoCocina = if (local.estadoCocina == "Listo") "Listo" else remote.estadoCocina
                            )
                        } else {
                            remote
                        }
                    }
                    val remoteKeys = mergedRemote.map { "${it.idPedido}_${it.producto}" }.toSet()
                    val onlyLocal = _orders.value.filter { "${it.idPedido}_${it.producto}" !in remoteKeys }
                    val combined = applyAllModifiedTapers(mergedRemote + onlyLocal)
                    _orders.value = combined
                    return@withContext combined
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al consultar todos los pedidos: ${e.message}")
        }
        _orders.value
    }

    /**
     * Consulta pedidos pendientes para la Cola de Cocina (KDS).
     * Endpoint: GET /exec?tipo=pedidos&estado=Pendiente
     */
    suspend fun fetchKitchenOrders(): List<PedidoItem> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.apiService.getKitchenOrders()
            if (response.isSuccessful) {
                val remoteOrders = response.body()?.data
                if (!remoteOrders.isNullOrEmpty()) {
                    val localMap = _orders.value.associateBy { "${it.idPedido}_${it.producto}" }
                    val mergedRemote = remoteOrders.map { remote ->
                        val local = localMap["${remote.idPedido}_${remote.producto}"]
                        val effectiveFechaHora = local?.fechaHora?.takeIf { it.isNotBlank() } ?: remote.fechaHora
                        if (remote.idPedido.isNotBlank()) {
                            getOrderStartTimeMillis(remote.idPedido, effectiveFechaHora)
                        }
                        if (local != null) {
                            remote.copy(
                                fechaHora = effectiveFechaHora,
                                estadoPago = if (local.estadoPago == "Pagado") "Pagado" else remote.estadoPago,
                                metodoPago = if (local.estadoPago == "Pagado") local.metodoPago else remote.metodoPago,
                                montoEfectivo = if (local.estadoPago == "Pagado") local.montoEfectivo else remote.montoEfectivo,
                                montoYape = if (local.estadoPago == "Pagado") local.montoYape else remote.montoYape,
                                estadoCocina = if (local.estadoCocina == "Listo") "Listo" else remote.estadoCocina
                            )
                        } else remote
                    }
                    val remoteKeys = mergedRemote.map { "${it.idPedido}_${it.producto}" }.toSet()
                    val onlyLocal = _orders.value.filter { "${it.idPedido}_${it.producto}" !in remoteKeys }
                    val combined = applyAllModifiedTapers(mergedRemote + onlyLocal)
                    _orders.value = combined
                    return@withContext combined.filter { it.estadoCocina == "Pendiente" }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al consultar cola de cocina: ${e.message}")
        }
        _orders.value.filter { it.estadoCocina == "Pendiente" }
    }

    /**
     * Envía una comanda de envío / delivery a cocina de forma asíncrona y descuenta el stock local.
     */
    suspend fun enviarComandaACocina(
        items: List<PedidoItem>,
        idMesa: String,
        tipoOrden: String,
        cliente: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val orderId = "ORD-${Random.nextInt(1000, 9999)}"
        val nowMillis = System.currentTimeMillis()
        orderStartTimes[orderId] = nowMillis
        val nowStr = dateFormat.format(Date(nowMillis))

        val enrichedItems = items.map { item ->
            item.copy(
                idPedido = orderId,
                fechaHora = nowStr,
                tipoOrden = if (item.tipoOrden.isNotBlank()) item.tipoOrden else tipoOrden,
                idMesa = if (item.idMesa.isNotBlank()) item.idMesa else idMesa.ifBlank { cliente.ifBlank { "Envío" } },
                cliente = if (item.cliente.isNotBlank()) item.cliente else cliente.ifBlank { idMesa.ifBlank { "Envío" } },
                estadoCocina = if (item.categoria == "Empaque") "Listo" else item.estadoCocina.ifBlank { "Pendiente" },
                estadoPago = item.estadoPago.ifBlank { "Pendiente" },
                metodoPago = item.metodoPago.ifBlank { "Pendiente" }
            )
        }

        // 1. Actualizar estado local de órdenes inmediatamente
        val updatedOrders = _orders.value.toMutableList().apply { addAll(enrichedItems) }
        _orders.value = updatedOrders

        // 2. Descontar Stock local de los productos vendidos
        _products.value = _products.value.map { prod ->
            val vendido = enrichedItems.filter {
                it.producto.equals(prod.nombre, ignoreCase = true) || it.producto == prod.id_producto
            }.sumOf { it.cantidad }
            if (vendido > 0) {
                val nuevoStock = (prod.stock - vendido).coerceAtLeast(0)
                prod.copy(stock = nuevoStock, disponible = nuevoStock > 0 && prod.disponible)
            } else {
                prod
            }
        }

        if (tipoOrden.contains("Mesa", ignoreCase = true) && idMesa.isNotBlank()) {
            _tables.value = _tables.value.map {
                if (it.idMesa == idMesa || "Mesa ${it.numero}" == idMesa || it.numero.toString() == idMesa) {
                    it.copy(estado = "Ocupada")
                } else it
            }
        }

        // 3. Enviar a Google Apps Script
        try {
            val request = PedidoRequest(action = "enviar_pedido", items = enrichedItems)
            val response = NetworkModule.apiService.enviarPedido(request)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: ""
                Log.d(TAG, "Google Sheets POST exitoso: $body")
            } else {
                Log.w(TAG, "Google Sheets POST retornó código HTTP: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al enviar pedido a Google Sheets: ${e.message}", e)
        }

        Result.success(orderId)
    }

    /**
     * Cobra una orden en Stand By por ID_Pedido y libera la mesa si ya no tiene más pedidos pendientes.
     * POST action: "cobrar_orden"
     */
    suspend fun cobrarOrden(
        idPedido: String,
        metodoPago: String,
        montoEfectivo: Double,
        montoYape: Double,
        cantidadTapers: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (cantidadTapers != null) {
            actualizarTapersOrden(idPedido, cantidadTapers)
        }
        val orderItems = _orders.value.filter { it.idPedido == idPedido }
        val totalOrder = orderItems.sumOf { it.totalItem }.takeIf { it > 0.0 } ?: (montoEfectivo + montoYape)
        val affectedMesas = orderItems.map { it.idMesa }.filter { it.isNotBlank() }.distinct()

        // 1. Actualizar estado local inmediatamente a "Pagado" con su proporción de Efectivo y Yape
        _orders.value = _orders.value.map { item ->
            if (item.idPedido == idPedido) {
                val ratio = if (totalOrder > 0.0) item.totalItem / totalOrder else 0.0
                val itemEfectivo = when (metodoPago) {
                    "Efectivo" -> item.totalItem
                    "Yape" -> 0.0
                    "Mixto" -> montoEfectivo * ratio
                    else -> 0.0
                }
                val itemYape = when (metodoPago) {
                    "Yape" -> item.totalItem
                    "Efectivo" -> 0.0
                    "Mixto" -> montoYape * ratio
                    else -> 0.0
                }
                item.copy(
                    estadoPago = "Pagado",
                    metodoPago = metodoPago,
                    montoEfectivo = itemEfectivo,
                    montoYape = itemYape
                )
            } else item
        }

        // 2. Si el pedido pertenecía a una mesa y ya no quedan ítems pendientes en esa mesa, liberarla
        if (affectedMesas.isNotEmpty()) {
            _tables.value = _tables.value.map { table ->
                val matchesOrderMesa = affectedMesas.any { m ->
                    table.idMesa.equals(m, ignoreCase = true) ||
                            "Mesa ${table.numero}".equals(m, ignoreCase = true) ||
                            "M-${table.numero}".equals(m, ignoreCase = true) ||
                            table.numero.toString() == m
                }
                if (matchesOrderMesa) {
                    val stillHasPending = _orders.value.any { rem ->
                        rem.estadoPago.equals("Pendiente", ignoreCase = true) && (
                                rem.idMesa.equals(table.idMesa, ignoreCase = true) ||
                                        rem.idMesa.equals("Mesa ${table.numero}", ignoreCase = true) ||
                                        rem.idMesa.equals("M-${table.numero}", ignoreCase = true) ||
                                        rem.idMesa == table.numero.toString()
                                )
                    }
                    if (!stillHasPending) table.copy(estado = "Libre") else table
                } else table
            }
        }

        // 3. Enviar cobro a Google Apps Script
        try {
            val request = CobrarOrdenRequest(
                action = "cobrar_orden",
                idPedido = idPedido,
                metodoPago = metodoPago,
                montoEfectivo = montoEfectivo,
                montoYape = montoYape,
                montoTotal = montoEfectivo + montoYape
            )
            val response = NetworkModule.apiService.cobrarOrden(request)
            Log.d(TAG, "Orden $idPedido cobrada en Sheets: code ${response.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando cobrar_orden a Sheets: ${e.message}")
        }

        Result.success(Unit)
    }

    /**
     * Marca el pedido como listo en cocina tanto localmente como en el backend.
     * POST action: "marcar_listo"
     */
    suspend fun marcarPedidoListo(idPedido: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Actualizar local
        _orders.value = _orders.value.map {
            if (it.idPedido == idPedido) it.copy(estadoCocina = "Listo") else it
        }

        try {
            val response = NetworkModule.apiService.marcarListo(
                MarcarListoRequest(action = "marcar_listo", idPedido = idPedido)
            )
            Log.d(TAG, "Pedido $idPedido marcado como listo en Sheets: code ${response.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error notificando marcar_listo a Sheets: ${e.message}")
        }

        Result.success(Unit)
    }

    /**
     * Obtiene la cuenta acumulada para una mesa.
     */
    suspend fun getTableBill(idMesa: String): List<PedidoItem> = withContext(Dispatchers.IO) {
        val localBill = _orders.value.filter {
            (it.idMesa == idMesa || "Mesa ${it.idMesa}" == idMesa || it.idMesa.endsWith(idMesa)) &&
                    it.estadoPago == "Pendiente"
        }

        try {
            val response = NetworkModule.apiService.getTableBill(idMesa)
            if (response.isSuccessful) {
                val remoteItems = response.body()?.data
                if (!remoteItems.isNullOrEmpty()) {
                    val combined = (localBill + remoteItems).distinctBy { "${it.idPedido}_${it.producto}" }
                    return@withContext combined
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando cuenta en Sheets: ${e.message}")
        }

        localBill
    }

    /**
     * Cobra la cuenta acumulada de la mesa, la libera y sincroniza con Sheets.
     */
    suspend fun payAndFreeTable(
        idMesa: String,
        metodoPago: String,
        montoEfectivo: Double,
        montoYape: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val total = montoEfectivo + montoYape
        val tableItems = _orders.value.filter {
            (it.idMesa == idMesa || "Mesa ${it.idMesa}" == idMesa || it.idMesa.endsWith(idMesa)) &&
                    it.estadoPago == "Pendiente"
        }
        val sumItems = tableItems.sumOf { it.totalItem }.takeIf { it > 0.0 } ?: total

        _orders.value = _orders.value.map { item ->
            if ((item.idMesa == idMesa || "Mesa ${item.idMesa}" == idMesa || item.idMesa.endsWith(idMesa)) &&
                item.estadoPago == "Pendiente"
            ) {
                val ratio = if (sumItems > 0.0) item.totalItem / sumItems else 0.0
                val itemEfectivo = when (metodoPago) {
                    "Efectivo" -> item.totalItem
                    "Yape" -> 0.0
                    "Mixto" -> montoEfectivo * ratio
                    else -> 0.0
                }
                val itemYape = when (metodoPago) {
                    "Yape" -> item.totalItem
                    "Efectivo" -> 0.0
                    "Mixto" -> montoYape * ratio
                    else -> 0.0
                }
                item.copy(
                    estadoPago = "Pagado",
                    metodoPago = metodoPago,
                    montoEfectivo = itemEfectivo,
                    montoYape = itemYape
                )
            } else item
        }

        _tables.value = _tables.value.map {
            if (it.idMesa == idMesa || "Mesa ${it.numero}" == idMesa || it.numero.toString() == idMesa) {
                it.copy(estado = "Libre")
            } else it
        }

        try {
            val request = PayTableRequest(
                action = "cobrar_mesa",
                idMesa = idMesa,
                metodoPago = metodoPago,
                montoTotal = total,
                montoEfectivo = montoEfectivo,
                montoYape = montoYape
            )
            val response = NetworkModule.apiService.payTableBill(request)
            Log.d(TAG, "Cobro enviado a Sheets, code: ${response.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando cobro a Sheets: ${e.message}")
        }

        Result.success(Unit)
    }

    /**
     * Obtiene los registros para una fecha específica (formato YYYY-MM-DD).
     */
    suspend fun getOrdersForDate(dateStr: String): List<PedidoItem> = withContext(Dispatchers.IO) {
        val localForDate = _orders.value.filter { it.fechaHora.startsWith(dateStr) }
        try {
            val response = NetworkModule.apiService.obtenerVentasPorFecha(dateStr)
            if (response.isSuccessful) {
                val remote = response.body()?.data
                if (!remote.isNullOrEmpty()) {
                    val localMap = localForDate.associateBy { "${it.idPedido}_${it.producto}" }
                    val mergedRemote = remote.map { rem ->
                        val loc = localMap["${rem.idPedido}_${rem.producto}"]
                        if (loc != null && loc.estadoPago == "Pagado") {
                            rem.copy(
                                estadoPago = "Pagado",
                                metodoPago = loc.metodoPago,
                                montoEfectivo = if (loc.montoEfectivo > 0.0 || loc.montoYape > 0.0) loc.montoEfectivo else rem.montoEfectivo,
                                montoYape = if (loc.montoYape > 0.0 || loc.montoEfectivo > 0.0) loc.montoYape else rem.montoYape
                            )
                        } else rem
                    }
                    val remoteKeys = mergedRemote.map { "${it.idPedido}_${it.producto}" }.toSet()
                    val onlyLocal = localForDate.filter { "${it.idPedido}_${it.producto}" !in remoteKeys }
                    return@withContext applyAllModifiedTapers(mergedRemote + onlyLocal)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo ventas de Sheets para $dateStr: ${e.message}")
        }

        localForDate
    }

    fun markKitchenOrderDone(idPedido: String) {
        _orders.value = _orders.value.map {
            if (it.idPedido == idPedido) it.copy(estadoCocina = "Listo") else it
        }
    }

    fun addTable(numero: Int, capacidad: Int) {
        val newTable = TableModel(
            idMesa = "M-${"%02d".format(numero)}",
            numero = numero,
            capacidad = capacidad,
            estado = "Libre"
        )
        _tables.value = _tables.value + newTable
    }

    fun addProduct(producto: Producto) {
        _products.value = _products.value + producto
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NetworkModule.apiService.createProduct(CreateProductRequest(producto = producto))
            } catch (e: Exception) {
                Log.e(TAG, "Error creando producto en Sheets: ${e.message}")
            }
        }
    }

    fun toggleProductDisponibilidad(productoId: String, disponible: Boolean) {
        _products.value = _products.value.map { prod ->
            if (prod.id_producto == productoId || prod.id == productoId) {
                prod.copy(disponible = disponible)
            } else prod
        }
    }

    fun updateProductStock(productoId: String, nuevoStock: Int) {
        val stockSafe = nuevoStock.coerceAtLeast(0)
        _products.value = _products.value.map { prod ->
            if (prod.id_producto == productoId || prod.id == productoId) {
                prod.copy(stock = stockSafe, disponible = stockSafe > 0)
            } else prod
        }
    }
}
