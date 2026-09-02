# sanat_trading

Android app in Kotlin + Jetpack Compose with Kotlin DSL (`.kts`) Gradle files.

The three supplied screener implementations are retained under:
- `app/src/main/kotlin/com/sanat/trading/screener/intraday/IntradayScreener.kt`
- `app/src/main/kotlin/com/sanat/trading/screener/swing/SwingScreener.kt`
- `app/src/main/kotlin/com/sanat/trading/screener/marketstructure/MarketStructureScreener.kt`

Their blocking JVM HTTP client was revised to Android-compatible `HttpURLConnection`; the screening calculations and signal rules are preserved. Their command-line infinite/CLI runners were removed because the Android UI provides Run/Stop/Clear controls.

The app uses a light, premium ivory/navy/teal/champagne-gold visual system. It does not use a dark Material theme.

