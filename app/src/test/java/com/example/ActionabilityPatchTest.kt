package com.example

import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.daytrader.news.SymbolNewsMatcher
import com.example.domain.daytrader.plan.ExitPlanner
import com.example.domain.daytrader.plan.StopRuleType
import com.example.domain.daytrader.replay.BarRatio
import com.example.domain.daytrader.replay.FullSession
import com.example.domain.daytrader.replay.ReplayEngine
import com.example.domain.daytrader.replay.ReplayLean
import com.example.domain.daytrader.replay.ReplayPlan
import com.example.domain.daytrader.replay.SessionPool
import com.example.domain.daytrader.risk.RiskBudgetEngine
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.daytrader.scanner.DayTraderScannerEngine
import com.example.domain.engine.SignalEngineV2
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.MarketQuote
import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsItem
import com.example.domain.model.PositionSide
import com.example.domain.model.SentimentPolarity
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalFamily
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlagType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ActionabilityPatchTest {

    private fun buildFreshCandles(
        startPrice: Double,
        count: Int,
        timeframe: Timeframe = Timeframe.ONE_HOUR,
        stepFn: (index: Int, prevClose: Double) -> Triple<Double, Double, Double>
    ): List<CandlePoint> {
        val now = System.currentTimeMillis()
        val interval = timeframe.stepMillis.coerceAtLeast(60_000L)
        val startTs = now - (count.toLong() * interval)
        val candles = mutableListOf<CandlePoint>()
        var price = startPrice
        for (i in 0 until count) {
            val (high, low, close) = stepFn(i, price)
            candles.add(
                CandlePoint(
                    timestamp = startTs + (i * interval),
                    open = price,
                    high = maxOf(price, high, close),
                    low = minOf(price, low, close),
                    close = close,
                    volume = 50_000.0 + (i % 5) * 4_000.0,
                    isClosed = true
                )
            )
            price = close
        }
        return candles
    }

    @Test
    fun test1_OverExtendedMarketOutputsSoftFlagWaitWithNumericConditionalPlanAndOrderedTps() {
        // Build 150 bars of steady market, followed by a sharp 6-bar rally (> 2.5x ATR above EMA20/VWAP)
        val candles = buildFreshCandles(
            startPrice = 85.0,
            count = 155,
            timeframe = Timeframe.ONE_HOUR
        ) { idx, prev ->
            if (idx < 148) {
                val close = prev * (if (idx % 2 == 0) 1.001 else 0.999)
                Triple(close * 1.004, close * 0.996, close)
            } else {
                val close = prev * 1.025
                Triple(close * 1.006, prev * 0.999, close)
            }
        }
        val livePrice = candles.last().close

        val output = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = livePrice,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                candles = candles
            )
        )

        assertEquals(
            "Over-extended setup should defer to WAIT conditional plan rather than NO_TRADE",
            SignalVerdictState.WAIT,
            output.verdictState
        )
        assertTrue(
            "OVER_EXTENDED must be flagged as a SoftFlag",
            output.softFlags.any { it.type == SoftFlagType.OVER_EXTENDED }
        )
        assertTrue(
            "Setup grade must be B or C_WATCH on soft flags",
            output.setupGrade == SetupGrade.B || output.setupGrade == SetupGrade.C_WATCH
        )
        assertTrue(
            "Needs condition must start with plain 'needs:' guidance",
            output.needsCondition.startsWith("needs:", ignoreCase = true)
        )

        val setup = output.primarySetup
        assertNotNull("Conditional WAIT plan must still compute numeric levels", setup)
        setup!!
        assertTrue(setup.entryPrice > 0.0)
        assertTrue(setup.stopLossPrice > 0.0)
        assertTrue(setup.stopLossPrice < setup.entryPrice)
        assertTrue("TP1 < TP2 < TP3 for Long", setup.takeProfit1 > setup.entryPrice && setup.takeProfit1 < setup.takeProfit2 && setup.takeProfit2 < setup.takeProfit3)
    }

    @Test
    fun test2_HistoryGuard_150BarsAllowsSetupWithEma200NA_80BarsHardVetoes() {
        // Case A: 150 bars (>= 100, < 250) -> no History Guard veto; EMA200 = N/A
        val candles150 = buildFreshCandles(
            startPrice = 100.0,
            count = 150,
            timeframe = Timeframe.ONE_HOUR
        ) { idx, prev ->
            val close = prev * (1.0 + if (idx % 3 == 0) 0.002 else -0.0005)
            Triple(close * 1.005, close * 0.995, close)
        }
        val output150 = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = candles150.last().close,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                candles = candles150
            )
        )
        assertFalse(
            "150 bars must NOT veto on History Guard",
            output150.vetoReasons.any { it.contains("Insufficient history", ignoreCase = true) }
        )
        assertEquals(150, output150.barsLoaded)
        val trendVote = output150.allFamilyVotes.firstOrNull { it.family == SignalFamily.TREND }
        assertNotNull("Trend family vote must be present on 150 bars", trendVote)
        assertEquals("EMA200 must report N/A when bars < 250", "N/A", trendVote!!.rawValues["EMA200"])

        // Case B: 80 bars (< 100) -> Hard NO_TRADE History Guard Veto
        val candles80 = candles150.takeLast(80)
        val output80 = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = candles80.last().close,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                candles = candles80
            )
        )
        assertEquals(SignalVerdictState.NO_TRADE, output80.verdictState)
        assertTrue(
            output80.vetoReasons.any { it.contains("Insufficient history", ignoreCase = true) }
        )
        assertEquals(80, output80.barsLoaded)
    }

    @Test
    fun test3_DeadRegimeEmitsSoftFlagAndConditionalExpansionBreakoutPlan() {
        // First 110 bars moderately volatile, then last 45 bars compressed into a tight dead squeeze
        val candles = buildFreshCandles(
            startPrice = 50.0,
            count = 155,
            timeframe = Timeframe.FIVE_MIN
        ) { idx, prev ->
            if (idx < 110) {
                val close = 50.0 + ((idx % 10) - 5) * 0.45
                Triple(close + 0.60, close - 0.60, close)
            } else {
                val close = 50.0 + ((idx % 2) * 0.008 - 0.004)
                Triple(close + 0.015, close - 0.015, close)
            }
        }
        val livePrice = candles.last().close
        val output = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "DOGE",
                currentPrice = livePrice,
                timeframe = Timeframe.FIVE_MIN,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                candles = candles
            )
        )

        assertEquals(MarketRegime.DEAD, output.marketRegime)
        assertEquals(
            "Dead regime must produce a conditional WAIT plan rather than NO_TRADE",
            SignalVerdictState.WAIT,
            output.verdictState
        )
        assertTrue(
            output.softFlags.any { it.type == SoftFlagType.DEAD_REGIME }
        )
        val setup = output.primarySetup
        assertNotNull("Dead regime conditional plan must still supply valid numeric levels", setup)
        setup!!
        assertTrue(setup.entryPrice > 0.0)
        assertTrue(setup.stopLossPrice > 0.0)
        assertTrue(setup.takeProfit1 > 0.0)
        assertTrue(
            "Expansion wait condition must specify RVOL >= 1.2 breakout",
            output.needsCondition.contains("RVOL >= 1.2", ignoreCase = true)
        )
    }

    @Test
    fun test4_UnifiedScannerDeduplicatesMatchesEngineAndBuildsMarketSummary() {
        val bullDaily = buildFreshCandles(
            startPrice = 100.0,
            count = 30,
            timeframe = Timeframe.ONE_DAY
        ) { idx, prev ->
            val close = prev * 1.008
            Triple(close * 1.005, close * 0.995, close)
        }
        val btc15m = buildFreshCandles(
            startPrice = 60000.0,
            count = 150,
            timeframe = Timeframe.FIFTEEN_MIN
        ) { idx, prev ->
            val close = prev * (if (idx % 2 == 0) 1.002 else 0.999)
            Triple(close * 1.003, close * 0.997, close)
        }
        val doge15m = buildFreshCandles(
            startPrice = 0.15,
            count = 150,
            timeframe = Timeframe.FIFTEEN_MIN
        ) { idx, prev ->
            val close = prev * 0.9985
            Triple(close * 1.003, close * 0.997, close)
        }

        // Deliberately pass duplicate BTC bundle to verify distinctBy deduplication
        val bundles = listOf(
            DayTraderScannerEngine.SymbolChartBundle(
                symbol = "BTC",
                name = "Bitcoin",
                assetType = AssetType.CRYPTO,
                dailyCandles = bullDaily,
                fiveMinuteCandles = btc15m,
                volume24hUsd = 20_000_000_000.0,
                currentPrice = btc15m.last().close,
                setupCandles15m = btc15m,
                htfCandles1h = bullDaily,
                change24hPercent = 2.4
            ),
            DayTraderScannerEngine.SymbolChartBundle(
                symbol = "BTC",
                name = "Bitcoin",
                assetType = AssetType.CRYPTO,
                dailyCandles = bullDaily,
                fiveMinuteCandles = btc15m,
                volume24hUsd = 20_000_000_000.0,
                currentPrice = btc15m.last().close,
                setupCandles15m = btc15m,
                htfCandles1h = bullDaily,
                change24hPercent = 2.4
            ),
            DayTraderScannerEngine.SymbolChartBundle(
                symbol = "DOGE",
                name = "Dogecoin",
                assetType = AssetType.CRYPTO,
                dailyCandles = bullDaily.map { it.copy(close = it.close * 0.001, open = it.open * 0.001, high = it.high * 0.001, low = it.low * 0.001) },
                fiveMinuteCandles = doge15m,
                volume24hUsd = 900_000_000.0,
                currentPrice = doge15m.last().close,
                setupCandles15m = doge15m,
                htfCandles1h = bullDaily.map { it.copy(close = it.close * 0.001, open = it.open * 0.001, high = it.high * 0.001, low = it.low * 0.001) },
                change24hPercent = -3.1
            )
        )

        val result = DayTraderScannerEngine.scanSetupsAndMarketSummary(
            bundles = bundles,
            marketType = TradingMarketType.FUTURES,
            universeLabel = "Test Crypto Universe"
        )

        assertEquals("Duplicate bundles must be deduplicated into 2 setup cards", 2, result.setups.size)
        assertEquals(2, result.marketSummary.totalEvaluated)
        assertTrue(result.marketSummary.btcRegime.isNotBlank() && result.marketSummary.btcRegime != "N/A")
        assertTrue(result.marketSummary.pctBelowEma20 in 0.0..100.0)
        assertTrue(result.marketSummary.pctBelowEma50 in 0.0..100.0)
        assertEquals(
            result.marketSummary.totalEvaluated,
            result.marketSummary.gradeACount + result.marketSummary.gradeBCount + result.marketSummary.watchCount + result.marketSummary.noTradeCount
        )

        // Ensure raw rule hit descriptions do not double-prefix "Matched a rule:"
        result.rawRuleHits.forEach { hit ->
            assertFalse(
                "Rule description must not repeat 'Matched a rule:' prefix",
                hit.ruleDescription.contains("Matched a rule:", ignoreCase = true)
            )
            assertTrue("Quantitative summary must be populated", hit.quantitativeSummary.isNotBlank())
        }
    }

    @Test
    fun test5_ReplayInsufficientResolvedEmitsNeutralLeanAndNoRiskBudgetVeto() {
        // Build a 22-session pool with tiny 0.05% bar moves, paired with ultra-wide +/-20% SL/TP
        val sessions = List(22) { sIdx ->
            val bars = List(78) { bIdx ->
                BarRatio(
                    timestamp = 1700000000000L + (sIdx * 86400000L) + (bIdx * 300000L),
                    timeOfDayIndex = bIdx,
                    openRatio = 1.0,
                    highRatio = 1.0005,
                    lowRatio = 0.9995,
                    closeRatio = if (bIdx % 2 == 0) 1.0001 else 0.9999,
                    barAtr = 0.05
                )
            }
            FullSession("2026-10-${String.format("%02d", sIdx + 1)}", isWeekend = false, bars = bars)
        }
        val pool = SessionPool(
            symbol = "NVDA",
            profileType = MarketProfileType.US_EQUITY,
            sessions = sessions,
            poolAtr = 0.05,
            recentAtr = 0.05
        )
        val widePlan = ReplayPlan(
            symbol = "NVDA",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopLossPrice = 80.0,
            takeProfitPrice = 140.0,
            entryBarTimestamp = 1700000000000L,
            timeOfDayIndex = 10,
            horizonBars = 12,
            profileType = MarketProfileType.US_EQUITY
        )

        val replayResult = ReplayEngine.runReplay(widePlan, pool, seed = 19L, numReplays = 500)
        assertTrue("Resolved count must be < 200 on ultra-wide horizon bounds", replayResult.resolvedCount < 200)
        assertEquals(ReplayLean.INSUFFICIENT_RESOLVED, replayResult.verdict.lean)
        assertTrue(replayResult.verdict.headline.contains("Not enough resolved replays", ignoreCase = true))

        // Verify RiskBudgetEngine treats INSUFFICIENT_RESOLVED neutrally without Replay veto
        val dayTraderPlan = ExitPlanner.createPlan(
            symbol = "NVDA",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 98.0,
            profile = MarketProfile.defaultUsStock()
        )
        val riskEval = RiskBudgetEngine.evaluateRiskAndSize(
            plan = dayTraderPlan,
            profile = MarketProfile.defaultUsStock(),
            settings = RiskSettings(accountBalance = 25_000.0, maxRiskPerTradeDollars = 250.0, isConfigured = true),
            replayVerdict = replayResult.verdict,
            currentTimestamp = 1763130600000L // within US stock market window (Friday ~14:30 UTC / 09:30 ET)
        )
        assertFalse(
            "INSUFFICIENT_RESOLVED must never cause a Replay Agent veto",
            riskEval.blockedReasons.any { it.contains("Replay Agent veto", ignoreCase = true) }
        )
        assertTrue(
            riskEval.checks.any { it.title == "Replay Engine" && it.isPassed && it.detail.contains("NEUTRAL", ignoreCase = true) }
        )
    }

    @Test
    fun test6_StrictSymbolScopedNewsRejectsSubstringSolVsSolutionsAndCrossAssetLeaks() {
        val solutionHeadline = NewsItem(
            title = "Enterprise Solutions Inc announces Q3 cloud revenue beat",
            source = "PR Newswire",
            pubDate = "10m ago",
            link = "https://example.com/1",
            sentiment = SentimentPolarity.BULLISH,
            sentimentScore = 0.4
        )
        val solHeadline = NewsItem(
            title = "Solana (SOL) validators approve throughput upgrade",
            source = "CoinDesk",
            pubDate = "5m ago",
            link = "https://example.com/2",
            sentiment = SentimentPolarity.BULLISH,
            sentimentScore = 0.6
        )
        val nvda8k = NewsItem(
            title = "NVDA files Form 8-K SEC filing with new guidance",
            source = "SEC EDGAR",
            pubDate = "2m ago",
            link = "https://example.com/3",
            sentiment = SentimentPolarity.BEARISH,
            sentimentScore = -0.8
        )
        val fomcMacro = NewsItem(
            title = "Federal Reserve FOMC holds interest rate steady",
            source = "Reuters",
            pubDate = "1h ago",
            link = "https://example.com/4",
            sentiment = SentimentPolarity.NEUTRAL,
            sentimentScore = 0.0
        )

        assertFalse(
            "'SOL' must NOT match 'Solutions'",
            SymbolNewsMatcher.matchesSymbol(solutionHeadline, "SOL", AssetType.CRYPTO)
        )
        assertTrue(
            "'SOL' must match 'Solana (SOL)'",
            SymbolNewsMatcher.matchesSymbol(solHeadline, "SOL", AssetType.CRYPTO)
        )
        assertFalse(
            "NVDA 8-K must NOT match DOGE",
            SymbolNewsMatcher.matchesSymbol(nvda8k, "DOGE", AssetType.CRYPTO)
        )
        assertTrue(
            "Macro FOMC news must apply cleanly with MACRO tag",
            SymbolNewsMatcher.matchesSymbol(fomcMacro, "SOL", AssetType.CRYPTO) &&
                SymbolNewsMatcher.matchesSymbol(fomcMacro, "NVDA", AssetType.STOCK)
        )
    }

    @Test
    fun test7_PriceFormattingAndSeparate24hVsCandleChange() {
        // Formatting guarantees: zero/invalid never print $0.00, NaN, or Infinity
        assertEquals("-", PriceFormatter.formatCurrency(0.0))
        assertEquals("-", PriceFormatter.formatCurrency(-5.0))
        assertEquals("-", PriceFormatter.formatCurrency(Double.NaN))
        assertEquals("-", PriceFormatter.formatCurrency(Double.POSITIVE_INFINITY))
        assertEquals("-", PriceFormatter.formatPercent(Double.POSITIVE_INFINITY))
        assertEquals("-", PriceFormatter.formatSignedPercent(Double.NaN))
        assertEquals("-", PriceFormatter.formatRMultiple(Double.NEGATIVE_INFINITY))
        assertEquals("-", PriceFormatter.formatZoneRange(0.0648, 0.0))

        // Separate 24h vs single-bar change on MarketQuote
        val lastBar = CandlePoint(
            timestamp = System.currentTimeMillis() - 3_600_000L,
            open = 86.00,
            high = 86.40,
            low = 85.20,
            close = 85.43,
            volume = 1_000_000.0,
            isClosed = true
        )
        val quote = MarketQuote(
            symbol = "SOL",
            name = "Solana",
            assetType = AssetType.CRYPTO,
            marketType = TradingMarketType.SPOT,
            currentPrice = 85.43,
            change = 3.43,
            changePercent = 4.18, // 24h change vs open24h = 82.00
            open = 82.00,
            ticker24hOpen = 82.00,
            candleChangePercent = ((lastBar.close - lastBar.open) / lastBar.open) * 100.0,
            dayHigh = 88.07,
            dayLow = 84.91,
            volume = 1_700_000.0,
            candles = listOf(lastBar)
        )

        assertTrue(
            "24h changePercent (+4.18%) must differ from 1h candle changePercent (-0.66%)",
            abs(quote.changePercent - (quote.candleChangePercent ?: 0.0)) > 1.0
        )
    }
}
