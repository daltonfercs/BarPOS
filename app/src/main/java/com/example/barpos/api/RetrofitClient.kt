package com.example.barpos.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // La URL base siempre debe terminar con "/" en Retrofit
    private const val BASE_URL = "https://script.google.com/macros/s/AKfycbwc4hdUK3MxHeCgCGKdJfJmlklB211mSyhH3VMZ_fNhkpIrUuBoypPeF-oTlSapnhesWA/"

    val instance: GoogleSheetsApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(GoogleSheetsApi::class.java)
    }
}
