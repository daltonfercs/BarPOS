package com.example.barpos.model

data class Producto(
    val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double
)

data class PedidoItem(
    val idPedido: String,
    val fechaHora: String,
    val categoria: String,
    val producto: String,
    val cantidad: Int,
    val precioUnitario: Double,
    val totalItem: Double,
    val tipoOrden: String, // "En Mesa" o "Para Llevar"
    val metodoPago: String, // "Efectivo", "Yape", "Mixto"
    val estadoCocina: String = "Pendiente" // "Pendiente", "Preparando", "Listo"
)

// Wrapper to send array of items to Google Apps Script
data class PedidoRequest(
    val items: List<PedidoItem>
)
