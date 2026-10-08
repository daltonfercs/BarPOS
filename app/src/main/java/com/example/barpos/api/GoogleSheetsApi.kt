package com.example.barpos.api

import com.example.barpos.model.PedidoItem
import com.example.barpos.model.PedidoRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface GoogleSheetsApi {
    @POST("exec") // Replace with actual Apps Script deployment URL path
    suspend fun enviarPedido(@Body request: PedidoRequest): Response<Void>

    @GET("exec?action=getVentasHoy")
    suspend fun obtenerVentasHoy(): Response<List<PedidoItem>>
}
