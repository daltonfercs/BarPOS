package com.daltonfercs.barpos.api

object RetrofitClient {
    val instance: GoogleSheetsApi
        get() = NetworkModule.apiService
}
