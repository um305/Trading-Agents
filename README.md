# TradingAgents (Android)

An Android (Kotlin + Jetpack Compose + Material 3) multi-horizon trading research, technical analysis, and risk-management terminal for **Stocks** and **Crypto (Spot & Futures)**.

TradingAgents runs a deterministic quantitative, Smart-Money Concepts (SMC), order-flow, replay, and risk engine locally on-device—so every chart, setup, scanner hit, and verdict works out of the box in **Engine-only** mode—and optionally enriches verdicts in the background when you connect one or more LLM API keys in **AI Fleet & APIs**.

> **Disclaimer:** TradingAgents is a technical analysis and scenario-modeling tool. Outputs are probabilistic estimates and research notes, **not** financial advice or trade execution guarantees.

---

## Key Features

### 1. OneQuery Terminal & Plain-English Trade Card
- **Symbol Search & Routing:** Analyze US equities/ETFs and crypto pairs across **Spot** and **Futures** (`SPOT` / `FUTURES`), with automatic Spot-short routing to Futures perpetuals when a perp market exists.
- **`PlainTradeCard` ("WHAT TO DO NOW"):** Translates the selected horizon into clear beginner-friendly guidance (`BUY`, `SELL`, `DON'T BUY`, `WAIT`, or `STAY OUT`), showing live price, limit order price & entry zone, stop-loss, three take-profit levels, reward-to-risk ratio, win probability, confidence grade, and idea validity window.
- **Interactive Candlestick Chart (`CandlestickChart`):**
  - One-finger horizontal drag to pan through candles, vertical drag preserved for page scrolling, and two-finger pinch to zoom.
  - Overlay toggles for Entry Zone, Limit, TP1–TP3, Stop Loss (with out-of-range price-ladder badge stacking), SMA/EMA, VWAP, SMC blocks/FVGs, Liquidity Pools (BSL/SSL), Volume Profile, and Replay fan cones.
- **Multi-Exchange Quote Comparison:** Live order-book/ticker spread checks across Binance, Bybit, MEXC, OKX, and Coinbase for crypto assets.

### 2. Multi-Horizon Master Verdict & 17-Agent Matrix
- **5 Trading Horizons (`MasterVerdictEngine`):**
  - **Scalp:** `1h` bias / `15m` setup / `5m` trigger (~3h hold)
  - **Intraday:** `4h` bias / `1h` setup / `15m` trigger (up to 24h; stocks flat-by rules)
  - **Swing:** `1D` bias / `4h` setup / `1h` trigger (2–10 days)
  - **Position:** `1W` (`5D` proxy) bias / `1D` setup / `4h` trigger (2–8 weeks)
  - **Outlook:** `1Y / 3M / 1M` context-only trend and accumulation/distribution zones (no entries)
- **17×7 Agent Matrix (`AgentMatrixEvaluator`):** Evaluates **Data Integrity**, **Session & Event**, **Regime**, **MTF Bias**, **Structure (SMC)**, **Value / Volume**, **Derivatives**, **Order Flow (DOM)**, **Intermarket**, **Trigger**, **Risk Sizer**, **Red Team**, **Fundamentals**, **News & Sentiment**, **Replay (Bootstrap)**, **Calibration (k/n)**, and **AI Synthesis** across timeframes.
- **Two-Tier Veto System:** Separates hard safety vetoes (`NO_TRADE`, e.g., stale data or structured event risk) from soft flags (`Grade B` / `Grade C WATCH` conditional plans).

### 3. Day-Trader Suite, Replay & Calibration
- **Bootstrap Historical Replay (`ReplayEngine`):** Runs seeded path simulations with drift-neutral odds checks, 95% Wilson confidence intervals, and honest `Inconclusive` detection when >75% of paths remain unresolved within the time window.
- **Entry Timer & Exit Planner:** Candle-close confirmation state machine (`EntryTimerStateMachine`) and structural stop/target planning.
- **Risk Budget Sizer & Paper Ledger:** Account-size and `% risk` configuration (no silent fake account defaults), minimum-notional warnings, liquidation distance safety guards, and Room-backed trade outcome calibration (`PaperLedgerEngine`).
- **Market Scanner & Strategy Backtest:** Multi-symbol rule scanner across crypto/equity universes and configurable historical strategy backtesting.

### 4. Background Monitoring & Alerts
- **`MarketMonitorWorker` (WorkManager):** Periodic 15-minute background watchlist evaluations when `BackgroundMonitorMode` is `STANDARD` or `LIVE`, with Battery Saver and Quiet Hours awareness.
- **`LiveMarketMonitorService` (Foreground Service):** Real-time Binance WebSocket closed-bar monitoring (`BinanceKlineWebSocketClient`) in `LIVE` mode, updating cached candles and triggering local notifications for armed horizon alerts and setups.
- **`BootCompletedReceiver`:** Restores configured monitoring schedules after device reboot or app update.

### 5. Optional AI Fleet & Encrypted Key Storage
- **Engine-First, AI-Later Architecture:** `MainViewModel` displays deterministic engine verdicts immediately and runs optional LLM committee enrichment asynchronously in the background with strict timeouts.
- **Multi-Provider AI Routing (`AiFleetRouter`, `MultiAiDispatcher`, `CompanyDiscoveryEngine`):** Connect your own API keys in **AI Fleet & APIs** with load balancing, free-tier prioritization, and local Android Keystore/encrypted storage (`data/security`).

---

## Tech Stack

- **Language & UI:** Kotlin `2.2.10`, Jetpack Compose (BOM `2024.09.00`), Material 3, Adaptive `WindowSizeClass` (Compact / Medium / Expanded two-column tablet & landscape layouts)
- **Architecture:** MVVM + Domain Engines (`StateFlow` / `ViewModel`, `Room` `2.7.0` with KSP, `WorkManager` `2.10.0`)
- **Networking & Serialization:** `OkHttp` `4.10.0`, `Retrofit` `2.12.0`, `Moshi` `1.15.2` (with KSP codegen), WebSockets for live Binance kline streams
- **AI / SDK Integration:** Firebase AI Logic / App Check (`firebase-bom` `34.17.0`) + direct user-configured provider endpoints via AI Fleet
- **Testing:** JUnit 4, Kotlin Coroutines Test, Robolectric `4.16.1`, Compose UI Test, Roborazzi `1.59.0`
- **Android SDK:** `minSdk = 24`, `compileSdk = 36`, `targetSdk = 36`, Java 11

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # App shell, adaptive NavigationBar / NavigationRail, top bar
├── data/
│   ├── api/                         # Market data REST clients (Binance, Yahoo Finance, etc.) & network utils
│   ├── background/                  # WorkManager worker, Foreground Service, WebSocket client, Boot receiver
│   ├── cache/                       # Incremental OHLCV closed-candle cache repository
│   ├── db/                          # Room database, DAOs, entities (Watchlist, Reports, Outcomes, Armed Plans)
│   ├── notification/                # Notification channels & alert dispatchers
│   ├── repository/                  # MarketRepository unifying feeds, cache, agents & persistence
│   └── security/                    # Encrypted credential & API key storage
├── domain/
│   ├── agents/                      # Multi-agent committee, NewsScannerAgent, TradingView technicals
│   ├── ai/                          # MultiAiDispatcher, AiFleetRouter, CompanyDiscoveryEngine
│   ├── analytics/                   # QuantitativeEngine (ATR, EMA/SMA, RSI, SMC, FVGs, Order Blocks, BSL/SSL)
│   ├── daytrader/                   # Replay, RiskBudget, EntryTimer, ExitPlanner, Scanner, PaperLedger, Session
│   ├── engine/                      # SignalEngineV2, UnifiedEntryEngine, SignalCalibrationEngine, VetoDetector
│   ├── master/                      # MasterVerdictEngine, AgentMatrixEvaluator, HorizonPlan models
│   ├── model/                       # Core domain models (MarketQuote, VerdictState, TradeSetupPlan, etc.)
│   └── util/                        # PriceFormatter & formatting utilities
└── ui/
    ├── MainViewModel.kt             # Serialized off-UI coroutine scope, UiState, live ticker & enrichment orchestration
    ├── adaptive/                    # Window size class helpers
    ├── components/                  # CandlestickChart, PlainTradeCard, MasterVerdictComponents, AgentMatrixGrid, etc.
    ├── screens/                     # DashboardScreen, CryptoScannerScreen, FavoritesScreen, BacktestScreen, ApiManagerScreen, SavedReportsScreen
    └── theme/                       # Material 3 colors, typography & dark trading theme
```

---

## Getting Started

### Prerequisites
- Android Studio (supporting AGP `9.1.1` / Kotlin `2.2.10` / JDK 17+ to run Gradle, targeting Java 11 bytecode)
- Android SDK Platform 36 (`compileSdk = 36`, `minSdk = 24`)

### Configuration & Secrets
1. The app ships ready to run in **Engine-only** mode with **no API key required**.
2. If using Gradle Secrets (`.env`) for build-time keys, copy `.env.example` to `.env` and add any desired values (never commit `.env` or real keys).
3. If using Firebase AI Logic, provide your own `app/google-services.json`.
4. To add AI model providers at runtime, open the **AI Fleet** tab inside the app and connect your provider key.

### Build & Run Tests

```bash
# Build debug APK
gradle :app:assembleDebug

# Run unit & Robolectric UI/engine tests
gradle :app:testDebugUnitTest
```

*(Or open the project in Android Studio and run the `:app` configuration on an emulator or physical Android device.)*

---

## Permissions Used
Declared in `app/src/main/AndroidManifest.xml`:
- `INTERNET` / `ACCESS_NETWORK_STATE` — Market data, news feeds, and optional AI API calls
- `POST_NOTIFICATIONS` / `VIBRATE` — Trade setup, stop/target, and conditional price alerts
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` — Optional Live WebSocket market monitor mode
- `RECEIVE_BOOT_COMPLETED` — Reschedules enabled background monitoring after reboot

---

## Acknowledgments
- Replay and day-trader risk/odds concepts adapted from the MIT-licensed `day-trader-skill` project (`Ideas adapted from day-trader-skill (MIT)`).
