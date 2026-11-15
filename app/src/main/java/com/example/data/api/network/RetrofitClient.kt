package com.example.data.api.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

// Moshi DTOs for Binance
data class BinanceTickerPriceDto(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "price") val price: String
)

data class Binance24hrTickerDto(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "lastPrice") val lastPrice: String?,
    @Json(name = "priceChange") val priceChange: String?,
    @Json(name = "priceChangePercent") val priceChangePercent: String?,
    @Json(name = "highPrice") val highPrice: String?,
    @Json(name = "lowPrice") val lowPrice: String?,
    @Json(name = "volume") val volume: String?,
    @Json(name = "quoteVolume") val quoteVolume: String?
)

data class BinanceFuturesPremiumDto(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "markPrice") val markPrice: String?,
    @Json(name = "indexPrice") val indexPrice: String?,
    @Json(name = "lastFundingRate") val lastFundingRate: String?,
    @Json(name = "nextFundingTime") val nextFundingTime: Long?
)

// Moshi DTOs for Bybit
data class BybitKlineResultDto(
    @Json(name = "category") val category: String?,
    @Json(name = "symbol") val symbol: String?,
    @Json(name = "list") val list: List<List<String>>?
)

data class BybitKlineResponseDto(
    @Json(name = "retCode") val retCode: Int,
    @Json(name = "retMsg") val retMsg: String,
    @Json(name = "result") val result: BybitKlineResultDto?
)

data class BybitTickerItemDto(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "lastPrice") val lastPrice: String?,
    @Json(name = "price24hPcnt") val price24hPcnt: String?,
    @Json(name = "highPrice24h") val highPrice24h: String?,
    @Json(name = "lowPrice24h") val lowPrice24h: String?,
    @Json(name = "volume24h") val volume24h: String?
)

data class BybitTickerResultDto(
    @Json(name = "list") val list: List<BybitTickerItemDto>?
)

data class BybitTickerResponseDto(
    @Json(name = "retCode") val retCode: Int,
    @Json(name = "result") val result: BybitTickerResultDto?
)

// Moshi DTOs for MEXC
data class Mexc24hrTickerDto(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "lastPrice") val lastPrice: String?,
    @Json(name = "priceChange") val priceChange: String?,
    @Json(name = "priceChangePercent") val priceChangePercent: String?,
    @Json(name = "highPrice") val highPrice: String?,
    @Json(name = "lowPrice") val lowPrice: String?,
    @Json(name = "volume") val volume: String?
)

object RetrofitClient {
    private const val USER_AGENT = "Mozilla/5.0 (Android; Mobile; TradingAgents/2.0)"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val binanceSpotApi: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BinanceApiService::class.java)
    }

    val binanceFuturesApi: BinanceApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://fapi.binance.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BinanceApiService::class.java)
    }

    val bybitApi: BybitApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bybit.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BybitApiService::class.java)
    }

    val mexcApi: MexcApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mexc.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MexcApiService::class.java)
    }
}
