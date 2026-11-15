package com.example.data.api.network

import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApiService {
    @GET("api/v3/ticker/price")
    suspend fun getSpotPrice(@Query("symbol") symbol: String): BinanceTickerPriceDto

    @GET("api/v3/ticker/24hr")
    suspend fun getSpot24hrTicker(@Query("symbol") symbol: String): Binance24hrTickerDto

    @GET("fapi/v1/ticker/price")
    suspend fun getFuturesPrice(@Query("symbol") symbol: String): BinanceTickerPriceDto

    @GET("fapi/v1/ticker/24hr")
    suspend fun getFutures24hrTicker(@Query("symbol") symbol: String): Binance24hrTickerDto

    @GET("fapi/v1/premiumIndex")
    suspend fun getFuturesPremium(@Query("symbol") symbol: String): BinanceFuturesPremiumDto

    @GET("api/v3/klines")
    suspend fun getSpotKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 100
    ): List<List<Any>>

    @GET("fapi/v1/klines")
    suspend fun getFuturesKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 100
    ): List<List<Any>>
}

interface BybitApiService {
    @GET("v5/market/tickers")
    suspend fun getTickers(
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String
    ): BybitTickerResponseDto

    @GET("v5/market/kline")
    suspend fun getKlines(
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "60",
        @Query("limit") limit: Int = 100
    ): BybitKlineResponseDto
}

interface MexcApiService {
    @GET("api/v3/ticker/24hr")
    suspend fun get24hrTicker(@Query("symbol") symbol: String): Mexc24hrTickerDto

    @GET("api/v3/ticker/price")
    suspend fun getPrice(@Query("symbol") symbol: String): BinanceTickerPriceDto
}
