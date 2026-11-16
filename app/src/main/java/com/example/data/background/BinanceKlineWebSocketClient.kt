package com.example.data.background

import android.util.Log
import com.example.domain.model.CandlePoint
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale

data class ClosedKlineEvent(
    val symbol: String,
    val marketType: TradingMarketType,
    val timeframe: Timeframe,
    val candle: CandlePoint
)

/**
 * Real-time Binance closed-bar WebSocket listener.
 * Emits ONLY when `k.x == true` (completed bar close) — never triggers on open/forming intrabar ticks.
 */
class BinanceKlineWebSocketClient(
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentStreamUrl: String? = null
    private var currentSymbol: String? = null
    private var currentMarketType: TradingMarketType = TradingMarketType.FUTURES
    private var currentTimeframes: List<Timeframe> = emptyList()
    private var shouldRun: Boolean = false
    private var reconnectAttempt: Int = 0

    private val _closedEvents = MutableSharedFlow<ClosedKlineEvent>(extraBufferCapacity = 32)
    val closedEvents: SharedFlow<ClosedKlineEvent> = _closedEvents.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun subscribe(
        symbol: String,
        marketType: TradingMarketType,
        timeframes: List<Timeframe> = listOf(Timeframe.FIVE_MIN, Timeframe.FIFTEEN_MIN, Timeframe.ONE_HOUR, Timeframe.FOUR_HOURS)
    ) {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isBlank() || timeframes.isEmpty()) return

        val pair = if (clean.endsWith("USDT") || clean.endsWith("USDC")) clean.lowercase(Locale.US) else "${clean.lowercase(Locale.US)}usdt"
        val streams = timeframes.mapNotNull { toBinanceStreamInterval(it) }
            .distinct()
            .joinToString("/") { "$pair@kline_$it" }
        if (streams.isBlank()) return

        val base = if (marketType == TradingMarketType.FUTURES) {
            "wss://fstream.binance.com/stream?streams=$streams"
        } else {
            "wss://stream.binance.com:9443/stream?streams=$streams"
        }

        if (base == currentStreamUrl && activeWebSocket != null && shouldRun) return

        currentSymbol = clean
        currentMarketType = marketType
        currentTimeframes = timeframes
        currentStreamUrl = base
        shouldRun = true
        reconnectAttempt = 0
        openSocket(base)
    }

    fun disconnect() {
        shouldRun = false
        reconnectJob?.cancel()
        reconnectJob = null
        activeWebSocket?.close(1000, "Client closing")
        activeWebSocket = null
        currentStreamUrl = null
        _isConnected.value = false
    }

    private fun openSocket(url: String) {
        reconnectJob?.cancel()
        activeWebSocket?.cancel()
        val req = Request.Builder().url(url).build()
        activeWebSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = parseClosedKlineMessage(text, currentSymbol, currentMarketType)
                if (parsed != null) {
                    _closedEvents.tryEmit(parsed)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                scheduleReconnectIfNeeded()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                Log.w("BinanceWS", "Kline WS error: ${t.message}")
                scheduleReconnectIfNeeded()
            }
        })
    }

    private fun scheduleReconnectIfNeeded() {
        if (!shouldRun) return
        val url = currentStreamUrl ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            val backoffMs = (2_000L shl reconnectAttempt.coerceAtMost(4)).coerceAtMost(32_000L)
            reconnectAttempt++
            delay(backoffMs)
            if (isActive && shouldRun) {
                openSocket(url)
            }
        }
    }

    companion object {
        fun toBinanceStreamInterval(tf: Timeframe): String? = when (tf) {
            Timeframe.ONE_MIN -> "1m"
            Timeframe.FIVE_MIN -> "5m"
            Timeframe.FIFTEEN_MIN -> "15m"
            Timeframe.THIRTY_MIN -> "30m"
            Timeframe.ONE_HOUR -> "1h"
            Timeframe.FOUR_HOURS -> "4h"
            Timeframe.ONE_DAY -> "1d"
            Timeframe.FIVE_DAYS -> "1w"
            else -> null
        }

        fun fromBinanceInterval(interval: String): Timeframe? = when (interval.lowercase(Locale.US)) {
            "1m" -> Timeframe.ONE_MIN
            "5m" -> Timeframe.FIVE_MIN
            "15m" -> Timeframe.FIFTEEN_MIN
            "30m" -> Timeframe.THIRTY_MIN
            "1h" -> Timeframe.ONE_HOUR
            "4h" -> Timeframe.FOUR_HOURS
            "1d" -> Timeframe.ONE_DAY
            "1w" -> Timeframe.FIVE_DAYS
            else -> null
        }

        /**
         * Parses a Binance combined stream (`{"stream":"...","data":{...}}`) or raw kline message.
         * Returns null when `k.x != true` (bar still open/forming) so only closed bars emit.
         */
        fun parseClosedKlineMessage(
            jsonText: String,
            fallbackSymbol: String? = null,
            marketType: TradingMarketType = TradingMarketType.FUTURES
        ): ClosedKlineEvent? {
            return runCatching {
                val root = JSONObject(jsonText)
                val payload = root.optJSONObject("data") ?: root
                if (payload.optString("e") != "kline") return null
                val k = payload.optJSONObject("k") ?: return null
                // Strict closed-bar filter: ignore forming candles (x == false)
                if (!k.optBoolean("x", false)) return null

                val tf = fromBinanceInterval(k.optString("i")) ?: return null
                val rawPair = k.optString("s").ifBlank { payload.optString("s") }
                val baseSym = when {
                    rawPair.endsWith("USDT", ignoreCase = true) -> rawPair.dropLast(4).uppercase(Locale.US)
                    rawPair.endsWith("USDC", ignoreCase = true) -> rawPair.dropLast(4).uppercase(Locale.US)
                    rawPair.isNotBlank() -> rawPair.uppercase(Locale.US)
                    else -> fallbackSymbol?.uppercase(Locale.US) ?: return null
                }

                val openTime = k.optLong("t", 0L)
                val closeTime = k.optLong("T", openTime + tf.stepMillis)
                val open = k.optDouble("o", 0.0)
                val high = k.optDouble("h", 0.0)
                val low = k.optDouble("l", 0.0)
                val close = k.optDouble("c", 0.0)
                val vol = k.optDouble("v", 0.0)
                if (openTime <= 0L || close <= 0.0 || high < low) return null

                ClosedKlineEvent(
                    symbol = baseSym,
                    marketType = marketType,
                    timeframe = tf,
                    candle = CandlePoint(
                        timestamp = openTime,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = vol,
                        isClosed = true,
                        closeTimestamp = closeTime
                    )
                )
            }.getOrNull()
        }
    }
}
