package com.example

import com.example.data.api.MarketDataService
import com.example.data.repository.MarketRepository
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.PositionSide
import com.example.domain.model.Timeframe
import com.example.ui.components.ChartBadgeLayoutHelper
import com.example.ui.components.LevelBadgeSpec
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    private fun generateMockCandles(
        basePrice: Double,
        volatility: Double,
        count: Int = 30,
        timeframe: Timeframe = Timeframe.FIFTEEN_MIN
    ): List<CandlePoint> {
        val candles = mutableListOf<CandlePoint>()
        var price = basePrice
        val step = timeframe.stepMillis
        val startTs = 1700000000000L
        for (i in 0 until count) {
            val high = price + (volatility * 0.7)
            val low = price - (volatility * 0.7)
            val close = price + (if (i % 2 == 0) volatility * 0.3 else -volatility * 0.3)
            candles.add(
                CandlePoint(
                    timestamp = startTs + (i * step),
                    open = price,
                    high = high,
                    low = low,
                    close = close,
                    volume = 1000.0
                )
            )
            price = close
        }
        return candles
    }

    private data class AssetTestCase(
        val symbol: String,
        val price: Double,
        val dayHigh: Double,
        val dayLow: Double,
        val assetType: AssetType
    )

    private val testAssets = listOf(
        // BTC
        AssetTestCase(
            symbol = "BTCUSDT",
            price = 81680.0,
            dayHigh = 84146.72,
            dayLow = 81514.28,
            assetType = AssetType.CRYPTO
        ),
        // SOL
        AssetTestCase(
            symbol = "SOLUSDT",
            price = 112.36,
            dayHigh = 113.40,
            dayLow = 109.24,
            assetType = AssetType.CRYPTO
        ),
        // Low-priced coin
        AssetTestCase(
            symbol = "PEPEUSDT",
            price = 0.0000098,
            dayHigh = 0.0000105,
            dayLow = 0.0000090,
            assetType = AssetType.CRYPTO
        ),
        // Stock
        AssetTestCase(
            symbol = "AAPL",
            price = 180.0,
            dayHigh = 183.0,
            dayLow = 176.0,
            assetType = AssetType.STOCK
        )
    )

    @Test
    fun testMonotonicRiskAndTargetDistancesAcrossAllTimeframes() {
        val allTimeframes = listOf(
            Timeframe.ONE_MIN,
            Timeframe.FIVE_MIN,
            Timeframe.FIFTEEN_MIN,
            Timeframe.THIRTY_MIN,
            Timeframe.ONE_HOUR,
            Timeframe.FOUR_HOURS,
            Timeframe.ONE_DAY,
            Timeframe.FIVE_DAYS,
            Timeframe.ONE_MONTH,
            Timeframe.THREE_MONTHS,
            Timeframe.ONE_YEAR
        )

        for (asset in testAssets) {
            val dayRange = asset.dayHigh - asset.dayLow

            for (side in listOf(PositionSide.LONG, PositionSide.SHORT)) {
                var previousRisk = 0.0
                var previousTp1Dist = 0.0
                var previousTp3Dist = 0.0

                for (tf in allTimeframes) {
                    // Volatility scales naturally with timeframe
                    val candleVolatility = (dayRange * 0.02 * (allTimeframes.indexOf(tf) + 1)).coerceAtLeast(asset.price * 0.0001)
                    val candles = generateMockCandles(
                        basePrice = asset.price,
                        volatility = candleVolatility,
                        count = 25,
                        timeframe = tf
                    )

                    val plan = MarketRepository.calculateTradeSetupPlan(
                        symbol = asset.symbol,
                        currentPrice = asset.price,
                        side = side,
                        leverage = 10,
                        candles = candles,
                        dayHigh = asset.dayHigh,
                        dayLow = asset.dayLow,
                        timeframe = tf,
                        assetType = asset.assetType,
                        isSynthetic = false
                    )

                    assertTrue("${asset.symbol} ${tf.label} plan must be available", plan.isAvailable)
                    val risk = abs(plan.recommendedEntry - plan.stopLoss)
                    val tp1Dist = abs(plan.takeProfit1 - plan.recommendedEntry)
                    val tp2Dist = abs(plan.takeProfit2 - plan.recommendedEntry)
                    val tp3Dist = abs(plan.takeProfit3 - plan.recommendedEntry)

                    // 1. Assert: Risk, TP1 distance and TP3 distance never decrease with longer timeframes
                    if (previousRisk > 0.0) {
                        assertTrue(
                            "${asset.symbol} $side ${tf.label} risk ($risk) must not decrease vs previous tf ($previousRisk)",
                            risk >= previousRisk * 0.999
                        )
                        assertTrue(
                            "${asset.symbol} $side ${tf.label} TP1 distance ($tp1Dist) must not decrease vs previous tf ($previousTp1Dist)",
                            tp1Dist >= previousTp1Dist * 0.999
                        )
                        assertTrue(
                            "${asset.symbol} $side ${tf.label} TP3 distance ($tp3Dist) must not decrease vs previous tf ($previousTp3Dist)",
                            tp3Dist >= previousTp3Dist * 0.999
                        )
                    }

                    // 2. Assert: SL is below entry for longs and above for shorts
                    if (side == PositionSide.LONG) {
                        assertTrue("${asset.symbol} ${tf.label} Long SL must be strictly below entry", plan.stopLoss < plan.recommendedEntry)
                        assertTrue("${asset.symbol} ${tf.label} Long SL must be below live price", plan.stopLoss < asset.price)
                    } else {
                        assertTrue("${asset.symbol} ${tf.label} Short SL must be strictly above entry", plan.stopLoss > plan.recommendedEntry)
                        assertTrue("${asset.symbol} ${tf.label} Short SL must be above live price", plan.stopLoss > asset.price)
                    }

                    // 3. Assert: TP1 < TP2 < TP3 with gaps of at least 0.5R
                    if (side == PositionSide.LONG) {
                        assertTrue("${asset.symbol} ${tf.label} Long TP1 < TP2", plan.takeProfit1 < plan.takeProfit2)
                        assertTrue("${asset.symbol} ${tf.label} Long TP2 < TP3", plan.takeProfit2 < plan.takeProfit3)
                        val gap12 = plan.takeProfit2 - plan.takeProfit1
                        val gap23 = plan.takeProfit3 - plan.takeProfit2
                        assertTrue("${asset.symbol} ${tf.label} TP1->TP2 gap ($gap12) >= 0.5R (${0.5 * risk})", gap12 >= 0.5 * risk * 0.99)
                        assertTrue("${asset.symbol} ${tf.label} TP2->TP3 gap ($gap23) >= 0.5R (${0.5 * risk})", gap23 >= 0.5 * risk * 0.99)
                    } else {
                        assertTrue("${asset.symbol} ${tf.label} Short TP1 > TP2", plan.takeProfit1 > plan.takeProfit2)
                        assertTrue("${asset.symbol} ${tf.label} Short TP2 > TP3", plan.takeProfit2 > plan.takeProfit3)
                        val gap12 = plan.takeProfit1 - plan.takeProfit2
                        val gap23 = plan.takeProfit2 - plan.takeProfit3
                        assertTrue("${asset.symbol} ${tf.label} TP1->TP2 gap ($gap12) >= 0.5R (${0.5 * risk})", gap12 >= 0.5 * risk * 0.99)
                        assertTrue("${asset.symbol} ${tf.label} TP2->TP3 gap ($gap23) >= 0.5R (${0.5 * risk})", gap23 >= 0.5 * risk * 0.99)
                    }

                    // 4. Assert: No TP is already behind the live price at creation
                    if (side == PositionSide.LONG) {
                        assertTrue("${asset.symbol} ${tf.label} Long TP1 must be ahead of live price", plan.takeProfit1 > asset.price)
                        assertTrue("${asset.symbol} ${tf.label} Long TP2 must be ahead of live price", plan.takeProfit2 > asset.price)
                        assertTrue("${asset.symbol} ${tf.label} Long TP3 must be ahead of live price", plan.takeProfit3 > asset.price)
                    } else {
                        assertTrue("${asset.symbol} ${tf.label} Short TP1 must be below live price", plan.takeProfit1 < asset.price)
                        assertTrue("${asset.symbol} ${tf.label} Short TP2 must be below live price", plan.takeProfit2 < asset.price)
                        assertTrue("${asset.symbol} ${tf.label} Short TP3 must be below live price", plan.takeProfit3 < asset.price)
                    }

                    previousRisk = risk
                    previousTp1Dist = tp1Dist
                    previousTp3Dist = tp3Dist
                }
            }
        }
    }

    @Test
    fun testCandlesOfWrongIntervalAreRejected() {
        val btcPrice = 81680.0
        // Generate candles with 15m step
        val candles15m = generateMockCandles(btcPrice, volatility = 30.0, count = 25, timeframe = Timeframe.FIFTEEN_MIN)

        // Request 1m setup with 15m candles
        val setupMismatched = MarketRepository.calculateTradeSetupPlan(
            symbol = "BTCUSDT",
            currentPrice = btcPrice,
            side = PositionSide.LONG,
            leverage = 10,
            candles = candles15m,
            dayHigh = 84146.72,
            dayLow = 81514.28,
            timeframe = Timeframe.ONE_MIN
        )

        assertFalse("Candles of wrong interval must be rejected", setupMismatched.isAvailable)
        assertTrue(
            "Unavailable reason must explain candle interval mismatch",
            setupMismatched.unavailableReason.contains("Waiting for 1m candles") || setupMismatched.reasoning.contains("interval mismatch")
        )
    }

    @Test
    fun testSyntheticQuotesProduceNoSetup() {
        val btcPrice = 81680.0
        val candles15m = generateMockCandles(btcPrice, volatility = 30.0, count = 25, timeframe = Timeframe.FIFTEEN_MIN)

        val setupSynthetic = MarketRepository.calculateTradeSetupPlan(
            symbol = "BTCUSDT",
            currentPrice = btcPrice,
            side = PositionSide.LONG,
            leverage = 10,
            candles = candles15m,
            dayHigh = 84146.72,
            dayLow = 81514.28,
            timeframe = Timeframe.FIFTEEN_MIN,
            isSynthetic = true
        )

        assertFalse("Synthetic quotes must NEVER produce an active setup", setupSynthetic.isAvailable)
        assertEquals("Live data unavailable", setupSynthetic.unavailableReason)
    }

    @Test
    fun testFourHourStockCandleAggregation() {
        val service = MarketDataService()
        val fourHoursMillis = 14_400_000L
        val baseTs = (1700000000000L / fourHoursMillis) * fourHoursMillis
        val oneHourMillis = 3_600_000L

        // 8 hourly candles -> should aggregate into exactly 2 4h candles
        val hourlyCandles = listOf(
            CandlePoint(baseTs + (0 * oneHourMillis), open = 100.0, high = 105.0, low = 99.0, close = 103.0, volume = 1000.0),
            CandlePoint(baseTs + (1 * oneHourMillis), open = 103.0, high = 108.0, low = 102.0, close = 107.0, volume = 1500.0),
            CandlePoint(baseTs + (2 * oneHourMillis), open = 107.0, high = 109.0, low = 104.0, close = 105.0, volume = 1200.0),
            CandlePoint(baseTs + (3 * oneHourMillis), open = 105.0, high = 106.0, low = 98.0, close = 101.0, volume = 2000.0),

            CandlePoint(baseTs + (4 * oneHourMillis), open = 101.0, high = 110.0, low = 100.0, close = 109.0, volume = 1100.0),
            CandlePoint(baseTs + (5 * oneHourMillis), open = 109.0, high = 112.0, low = 108.0, close = 111.0, volume = 1300.0),
            CandlePoint(baseTs + (6 * oneHourMillis), open = 111.0, high = 115.0, low = 110.0, close = 114.0, volume = 1400.0),
            CandlePoint(baseTs + (7 * oneHourMillis), open = 114.0, high = 116.0, low = 112.0, close = 115.0, volume = 1600.0)
        )

        val aggregated = service.aggregateCandles(hourlyCandles, fourHoursMillis)
        assertEquals("8 hourly candles must aggregate into 2 4h candles", 2, aggregated.size)

        val candle1 = aggregated[0]
        assertEquals(100.0, candle1.open, 0.001)
        assertEquals(109.0, candle1.high, 0.001)
        assertEquals(98.0, candle1.low, 0.001)
        assertEquals(101.0, candle1.close, 0.001)
        assertEquals(5700.0, candle1.volume, 0.001)

        val candle2 = aggregated[1]
        assertEquals(101.0, candle2.open, 0.001)
        assertEquals(116.0, candle2.high, 0.001)
        assertEquals(100.0, candle2.low, 0.001)
        assertEquals(115.0, candle2.close, 0.001)
        assertEquals(5400.0, candle2.volume, 0.001)
    }

    @Test
    fun testChartLabelLayoutNoIntersectionFor1mBtcExample() {
        // Real example of the bug: BTC 1m with tight prices where LIMIT, SL, TP1 were overlapping
        val btcPrice = 81680.0
        val chartHeight = 500f
        val badgeHeight = 36f
        val minGapPx = 4f
        val canvasWidth = 600f

        val specs = listOf(
            LevelBadgeSpec(
                id = "TP3",
                label = "TP3 $82,327.98",
                price = 82327.98,
                idealY = 120f,
                isPinnedTop = false,
                isPinnedBottom = false,
                textColor = Color.Cyan,
                bgColor = Color.Black,
                strokeColor = Color.Cyan,
                trueLineY = 120f
            ),
            LevelBadgeSpec(
                id = "TP2",
                label = "TP2 $82,050.00",
                price = 82050.00,
                idealY = 180f,
                isPinnedTop = false,
                isPinnedBottom = false,
                textColor = Color.Green,
                bgColor = Color.Black,
                strokeColor = Color.Green,
                trueLineY = 180f
            ),
            LevelBadgeSpec(
                id = "TP1",
                label = "TP1 $81,892.36",
                price = 81892.36,
                idealY = 220f,
                isPinnedTop = false,
                isPinnedBottom = false,
                textColor = Color.Green,
                bgColor = Color.Black,
                strokeColor = Color.Green,
                trueLineY = 220f
            ),
            LevelBadgeSpec(
                id = "LIMIT",
                label = "LIMIT $81,630.98",
                price = 81630.98,
                idealY = 230f, // very close to TP1 & SL
                isPinnedTop = false,
                isPinnedBottom = false,
                textColor = Color.Cyan,
                bgColor = Color.Black,
                strokeColor = Color.Cyan,
                trueLineY = 230f
            ),
            LevelBadgeSpec(
                id = "SL",
                label = "SL $81,456.73",
                price = 81456.73,
                idealY = 240f, // only 10px from LIMIT
                isPinnedTop = false,
                isPinnedBottom = false,
                textColor = Color.Red,
                bgColor = Color.Black,
                strokeColor = Color.Red,
                trueLineY = 240f
            )
        )

        val positioned = ChartBadgeLayoutHelper.computeChartBadgeLayout(
            specs = specs,
            chartHeight = chartHeight,
            badgeHeight = badgeHeight,
            minGapPx = minGapPx,
            canvasWidth = canvasWidth,
            measureTextWidth = { it.length * 10f }
        )

        assertTrue("Badges must be laid out", positioned.isNotEmpty())

        // Assert: No two badge rectangles intersect
        for (i in 0 until positioned.size) {
            val a = positioned[i]
            val aTop = a.topY
            val aBottom = a.topY + a.height
            val aLeft = a.leftX
            val aRight = a.leftX + a.width

            for (j in i + 1 until positioned.size) {
                val b = positioned[j]
                val bTop = b.topY
                val bBottom = b.topY + b.height
                val bLeft = b.leftX
                val bRight = b.leftX + b.width

                val verticalOverlap = !(aBottom <= bTop || bBottom <= aTop)
                val horizontalOverlap = !(aRight <= bLeft || bRight <= aLeft)
                val intersects = verticalOverlap && horizontalOverlap

                assertFalse(
                    "Badge ${a.label} [${aTop}..${aBottom}] must not intersect badge ${b.label} [${bTop}..${bBottom}]",
                    intersects
                )
            }
        }
    }
}
