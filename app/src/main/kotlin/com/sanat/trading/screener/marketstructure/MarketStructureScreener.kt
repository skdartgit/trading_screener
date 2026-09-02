package com.sanat.trading.screener.marketstructure

import kotlinx.coroutines.*
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import com.sanat.trading.screener.niftyfilteredstocks.STOCK_LIST

// ==========================================================
// DATA STRUCTURES
// ==========================================================
data class Candle(
    val high: Double,
    val low: Double,
    val close: Double
)

data class MssResult(
    val stock: String,
    val close: Double,
    val prevSwingHigh: Double,
    val prevSwingLow: Double,
    val signal: String
)

class MarketStructureScreener {

    // Complete Watchlist matching your Python script
    val stocks = STOCK_LIST

    // Fetch data with URLEncoder fix
    private fun getData(symbol: String): List<Candle> {
        return try {
            val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8.toString())
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encodedSymbol?interval=1d&range=3mo"

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SanatTrading/1.0")
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return emptyList()
            }

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            connection.disconnect()
            val result = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)

            val highs = quote.getJSONArray("high")
            val lows = quote.getJSONArray("low")
            val closes = quote.getJSONArray("close")

            val candles = mutableListOf<Candle>()
            for (i in 0 until closes.length()) {
                if (!closes.isNull(i) && !highs.isNull(i) && !lows.isNull(i)) {
                    candles.add(
                        Candle(
                            high = highs.getDouble(i),
                            low = lows.getDouble(i),
                            close = closes.getDouble(i)
                        )
                    )
                }
            }
            candles
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Market Structure Shift Calculation (matching rolling window = 20 shift 1)
    fun analyzeStock(stock: String, lookback: Int = 20): MssResult? {
        val df = getData(stock)
        if (df.size < lookback + 1) return null

        val latest = df.last()
        val closePrice = latest.close

        // Rolling max/min shifted by 1 index (excluding latest candle)
        val historicalWindow = df.subList(df.size - 1 - lookback, df.size - 1)
        val prevSwingHigh = historicalWindow.maxOf { it.high }
        val prevSwingLow = historicalWindow.minOf { it.low }

        val signal = when {
            closePrice > prevSwingHigh -> "Bullish Shift"
            closePrice < prevSwingLow -> "Bearish Shift"
            else -> "No Shift"
        }

        return MssResult(
            stock = stock.replace(".NS", ""),
            close = roundTwoDecimals(closePrice),
            prevSwingHigh = roundTwoDecimals(prevSwingHigh),
            prevSwingLow = roundTwoDecimals(prevSwingLow),
            signal = signal
        )
    }

    private fun roundTwoDecimals(valIn: Double): Double = Math.round(valIn * 100.0) / 100.0
}


