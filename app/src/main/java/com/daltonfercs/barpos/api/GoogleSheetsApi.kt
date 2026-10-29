package com.daltonfercs.barpos.api

import com.daltonfercs.barpos.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface GoogleSheetsApi {
    @POST("exec")
    suspend fun enviarPedido(@Body request: PedidoRequest): Response<ResponseBody>

    @POST("exec")
    suspend fun enviarPedidoArray(@Body items: List<PedidoItem>): Response<ResponseBody>

    @GET("exec?action=getVentasHoy")
    suspend fun obtenerVentasHoy(): Response<SheetsDataResponse<PedidoItem>>

    @GET("exec?tipo=ventas")
    suspend fun obtenerVentasPorFecha(@Query("fecha") fecha: String): Response<SheetsDataResponse<PedidoItem>>

    @GET("exec?tipo=pedidos")
    suspend fun getAllOrders(): Response<SheetsDataResponse<PedidoItem>>

    @GET("exec?tipo=pedidos&estado=Pendiente")
    suspend fun getKitchenOrders(): Response<SheetsDataResponse<PedidoItem>>

    @POST("exec")
    suspend fun marcarListo(@Body request: MarcarListoRequest): Response<ResponseBody>

    @POST("exec")
    suspend fun cobrarOrden(@Body request: CobrarOrdenRequest): Response<ResponseBody>

    @GET("exec?tipo=mesas")
    suspend fun getTables(): Response<SheetsDataResponse<TableModel>>

    @GET("exec?tipo=cuenta_mesa")
    suspend fun getTableBill(@Query("id_mesa") idMesa: String): Response<SheetsDataResponse<PedidoItem>>

    @POST("exec")
    suspend fun payTableBill(@Body request: PayTableRequest): Response<ResponseBody>

    @GET("exec?tipo=cierre_caja")
    suspend fun getCierreCaja(@Query("fecha") fecha: String? = null): Response<CierreCaja>

    @GET("exec?tipo=productos")
    suspend fun getProducts(): Response<SheetsDataResponse<Producto>>

    @POST("exec")
    suspend fun createProduct(@Body request: CreateProductRequest): Response<ResponseBody>

    @POST("exec")
    suspend fun createTable(@Body request: CreateTableRequest): Response<ResponseBody>
}

typealias BarApiService = GoogleSheetsApi
