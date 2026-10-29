package com.daltonfercs.barpos.model

import com.google.gson.annotations.SerializedName

/**
 * Respuesta genérica para la API de Google Apps Script cuando retorna un contenedor:
 * {"status": "success", "data": [...]}
 */
data class SheetsDataResponse<T>(
    @SerializedName("status")
    val status: String = "success",

    @SerializedName("data")
    val data: List<T> = emptyList(),

    @SerializedName("message")
    val message: String? = null
)

data class Producto(
    @SerializedName("id", alternate = ["ID", "id_producto", "idProducto"])
    val id: String = "",

    @SerializedName("nombre", alternate = ["Nombre", "producto", "Producto"])
    val nombre: String = "",

    @SerializedName("categoria", alternate = ["Categoria", "CATEGORIA"])
    val categoria: String = "General", // "Bebidas", "Comidas", "Snacks"

    @SerializedName("tipo", alternate = ["Tipo"])
    val tipo: String = "Genérico",

    @SerializedName("precio", alternate = ["Precio", "precio_unitario"])
    val precio: Double = 0.0,

    @SerializedName("disponible", alternate = ["Disponible"])
    val disponible: Boolean = true,

    @SerializedName("stock", alternate = ["Stock"])
    val stock: Int = 0
) {
    val id_producto: String
        get() = id.ifBlank { nombre }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Producto) return false
        val thisKey = id.ifBlank { nombre }
        val otherKey = other.id.ifBlank { other.nombre }
        return thisKey == otherKey
    }

    override fun hashCode(): Int {
        return (id.ifBlank { nombre }).hashCode()
    }
}

data class CreateProductRequest(
    @SerializedName("action", alternate = ["ACTION"])
    val action: String = "crear_producto",

    @SerializedName("producto", alternate = ["Producto"])
    val producto: Producto
)

data class CreateTableRequest(
    @SerializedName("action", alternate = ["ACTION"])
    val action: String = "crear_mesa",

    @SerializedName("mesa", alternate = ["Mesa"])
    val mesa: TableModel
)

data class MarcarListoRequest(
    @SerializedName("action", alternate = ["ACTION"])
    val action: String = "marcar_listo",

    @SerializedName("id_pedido", alternate = ["idPedido", "ID_Pedido"])
    val idPedido: String
)

data class PedidoItem(
    @SerializedName("ID_Pedido", alternate = ["id_pedido", "idPedido", "ID_PEDIDO"])
    val idPedido: String = "",

    @SerializedName("Fecha_Hora", alternate = ["fecha_hora", "fechaHora", "FECHA_HORA"])
    val fechaHora: String = "",

    @SerializedName("Categoria", alternate = ["categoria", "CATEGORIA"])
    val categoria: String = "", // "Bebidas", "Comidas", "Snacks", "Empaque"

    @SerializedName("Producto", alternate = ["producto", "PRODUCTO"])
    val producto: String = "",

    @SerializedName("Cantidad", alternate = ["cantidad", "CANTIDAD"])
    val cantidad: Int = 1,

    @SerializedName("Precio_Unitario", alternate = ["precio_unitario", "precioUnitario", "PRECIO_UNITARIO"])
    val precioUnitario: Double = 0.0,

    @SerializedName("Total_Item", alternate = ["total_item", "totalItem", "TOTAL_ITEM"])
    val totalItem: Double = 0.0,

    @SerializedName("Tipo_Orden", alternate = ["tipo_orden", "tipoOrden", "TIPO_ORDEN"])
    val tipoOrden: String = "Envío", // "Envío", "Para Llevar", "En Mesa"

    @SerializedName("ID_Mesa", alternate = ["id_mesa", "idMesa", "ID_MESA"])
    val idMesa: String = "", // Identificador o dirección

    @SerializedName("Cliente", alternate = ["cliente", "CLIENTE", "direccion", "Direccion", "Nombre_Cliente"])
    val cliente: String = "", // Nombre del cliente o dirección de envío

    @SerializedName("Metodo_Pago", alternate = ["metodo_pago", "metodoPago", "METODO_PAGO"])
    val metodoPago: String = "Pendiente", // "Efectivo", "Yape", "Mixto", "Pendiente"

    @SerializedName("Estado_Cocina", alternate = ["estado_cocina", "estadoCocina", "ESTADO_COCINA"])
    val estadoCocina: String = "Pendiente", // "Pendiente", "Preparando", "Listo"

    @SerializedName("Estado_Pago", alternate = ["estado_pago", "estadoPago", "ESTADO_PAGO"])
    val estadoPago: String = "Pendiente", // "Pendiente", "Pagado"

    @SerializedName("Monto_Efectivo", alternate = ["monto_efectivo", "montoEfectivo", "MONTO_EFECTIVO"])
    val montoEfectivo: Double = 0.0,

    @SerializedName("Monto_Yape", alternate = ["monto_yape", "montoYape", "MONTO_YAPE"])
    val montoYape: Double = 0.0
) {
    val direccionOCliente: String
        get() = cliente.ifBlank { idMesa.ifBlank { "Delivery" } }
}

// Wrapper to send array of items to Google Apps Script
data class PedidoRequest(
    @SerializedName("action", alternate = ["ACTION"])
    val action: String = "enviar_pedido",

    @SerializedName("items", alternate = ["ITEMS", "productos", "pedidos"])
    val items: List<PedidoItem>
)

data class TableModel(
    @SerializedName("id_mesa", alternate = ["idMesa", "ID_Mesa"])
    val idMesa: String = "",

    @SerializedName("numero", alternate = ["Numero", "NUMERO"])
    val numero: Int = 0,

    @SerializedName("capacidad", alternate = ["Capacidad", "CAPACIDAD"])
    val capacidad: Int = 0,

    @SerializedName("estado", alternate = ["Estado", "ESTADO"])
    val estado: String = "Libre" // "Libre", "Ocupada"
)

data class PayTableRequest(
    @SerializedName("action", alternate = ["ACTION"])
    val action: String = "cobrar_mesa",

    @SerializedName("id_mesa", alternate = ["idMesa", "ID_Mesa"])
    val idMesa: String,

    @SerializedName("metodo_pago", alternate = ["metodoPago", "Metodo_Pago"])
    val metodoPago: String, // "Efectivo", "Yape", "Mixto"

    @SerializedName("monto_total", alternate = ["montoTotal", "Monto_Total"])
    val montoTotal: Double,

    @SerializedName("monto_efectivo", alternate = ["montoEfectivo", "Monto_Efectivo"])
    val montoEfectivo: Double = 0.0,

    @SerializedName("monto_yape", alternate = ["montoYape", "Monto_Yape"])
    val montoYape: Double = 0.0
)

data class CobrarOrdenRequest(
    @SerializedName("action", alternate = ["ACTION"])
    val action: String = "cobrar_orden",

    @SerializedName("id_pedido", alternate = ["idPedido", "ID_Pedido"])
    val idPedido: String,

    @SerializedName("metodo_pago", alternate = ["metodoPago", "Metodo_Pago"])
    val metodoPago: String, // "Efectivo", "Yape", "Mixto"

    @SerializedName("monto_efectivo", alternate = ["montoEfectivo", "Monto_Efectivo"])
    val montoEfectivo: Double = 0.0,

    @SerializedName("monto_yape", alternate = ["montoYape", "Monto_Yape"])
    val montoYape: Double = 0.0,

    @SerializedName("monto_total", alternate = ["montoTotal", "Monto_Total"])
    val montoTotal: Double = montoEfectivo + montoYape
)


data class CierreCaja(
    @SerializedName("total_ventas", alternate = ["totalVentas", "Total_Ventas"])
    val totalVentas: Double = 0.0,

    @SerializedName("total_efectivo", alternate = ["totalEfectivo", "Total_Efectivo"])
    val totalEfectivo: Double = 0.0,

    @SerializedName("total_yape", alternate = ["totalYape", "Total_Yape"])
    val totalYape: Double = 0.0,

    @SerializedName("total_comida", alternate = ["totalComida", "Total_Comida"])
    val totalComida: Double = 0.0,

    @SerializedName("cantidad_comida", alternate = ["cantidadComida", "Cantidad_Comida"])
    val cantidadComida: Int = 0,

    @SerializedName("total_bebida", alternate = ["totalBebida", "Total_Bebida"])
    val totalBebida: Double = 0.0,

    @SerializedName("cantidad_bebida", alternate = ["cantidadBebida", "Cantidad_Bebida"])
    val cantidadBebida: Int = 0,

    @SerializedName("total_pendiente", alternate = ["totalPendiente", "Total_Pendiente"])
    val totalPendiente: Double = 0.0
)
