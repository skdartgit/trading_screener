package com.sanat.trading.screener.intraday

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.sanat.trading.screener.niftyfilteredstocks.STOCK_LIST

// ==============================
// DATA MODELS
// ==============================
data class StockRow(
    val symbol: String,
    val currentPrice: Double,
    val todayOpen: Double,
    val todayHigh: Double,
    val todayLow: Double,
    val prevHigh: Double,
    val prevLow: Double,
    val highOpenPct: Double,
    val lowOpenPct: Double,
    val changePct: Double,
    val volumePct: Double,
    val buySell: String,
    val ohOl: String,
    val gapStatus: String
)

data class SignalResult(
    val symbol: String,
    val changePct: Double,
    val volumePct: Double,
    val ohOl: String
)

class KotlinStockScreener {

    // Complete Watchlist of 220+ NIFTY Stocks
    private val stocks = STOCK_LIST

    // ==============================
    // FETCH SINGLE STOCK DATA
    // ==============================
    private fun fetchStockData(stock: String): StockRow? {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$stock?interval=1d&range=35d"

        return try {
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
                return null
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            connection.disconnect()
            val result = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)

            val opens = quote.getJSONArray("open")
            val highs = quote.getJSONArray("high")
            val lows = quote.getJSONArray("low")
            val closes = quote.getJSONArray("close")
            val volumes = quote.getJSONArray("volume")

            val openList = mutableListOf<Double>()
            val highList = mutableListOf<Double>()
            val lowList = mutableListOf<Double>()
            val closeList = mutableListOf<Double>()
            val volumeList = mutableListOf<Long>()

            // Extract valid non-null rows
            for (i in 0 until closes.length()) {
                if (!closes.isNull(i) && !opens.isNull(i) && !highs.isNull(i) && !lows.isNull(i) && !volumes.isNull(i)) {
                    openList.add(opens.getDouble(i))
                    highList.add(highs.getDouble(i))
                    lowList.add(lows.getDouble(i))
                    closeList.add(closes.getDouble(i))
                    volumeList.add(volumes.getLong(i))
                }
            }

            // Need minimum 2 rows
            if (closeList.size < 2) return null

            val currentPrice = closeList.last()
            val todayOpen = openList.last()
            val todayHigh = highList.last()
            val todayLow = lowList.last()
            val currentVolume = volumeList.last()

            val prevHigh = highList[highList.size - 2]
            val prevLow = lowList[lowList.size - 2]
            val prevClose = closeList[closeList.size - 2]

            // Monthly Avg Volume (excluding current day, taking last 30)
            val pastVolumes = volumeList.dropLast(1).takeLast(30)
            val avgMonthVolume = if (pastVolumes.isNotEmpty()) pastVolumes.average() else 0.0

            val volumePercentage = if (avgMonthVolume != 0.0) {
                ((currentVolume - avgMonthVolume) / avgMonthVolume) * 100.0
            } else 0.0

            val changePercentage = ((currentPrice - prevClose) / prevClose) * 100.0

            // BUY / SELL logic
            val buySell = when {
                currentPrice > prevHigh -> "Buy"
                currentPrice < prevLow -> "Sell"
                else -> ""
            }

            // HIGH / LOW vs OPEN %
            val highOpenPct = ((todayHigh - todayOpen) / todayOpen) * 100.0
            val lowOpenPct = ((todayLow - todayOpen) / todayOpen) * 100.0

            val highStatus = when {
                highOpenPct == 0.0 -> "OH"
                highOpenPct > 0.0 && highOpenPct < 0.3 -> "ONH"
                else -> ""
            }

            val lowStatus = when {
                lowOpenPct == 0.0 -> "OL"
                lowOpenPct > -0.3 && lowOpenPct < 0.0 -> "ONL"
                else -> ""
            }

            val ohOl = if (highStatus.isNotEmpty()) highStatus else lowStatus

            // GAP STATUS logic
            val gapStatus = when {
                todayOpen > prevHigh -> "Gup"
                todayOpen < prevLow -> "Gdn"
                todayOpen in prevLow..prevHigh -> "Btn"
                else -> ""
            }

            StockRow(
                symbol = stock.replace(".NS", ""),
                currentPrice = roundTwoDecimals(currentPrice),
                todayOpen = roundTwoDecimals(todayOpen),
                todayHigh = roundTwoDecimals(todayHigh),
                todayLow = roundTwoDecimals(todayLow),
                prevHigh = roundTwoDecimals(prevHigh),
                prevLow = roundTwoDecimals(prevLow),
                highOpenPct = roundTwoDecimals(highOpenPct),
                lowOpenPct = roundTwoDecimals(lowOpenPct),
                changePct = roundTwoDecimals(changePercentage),
                volumePct = roundTwoDecimals(volumePercentage),
                buySell = buySell,
                ohOl = ohOl,
                gapStatus = gapStatus
            )
        } catch (e: Exception) {
            null
        }
    }

    // Concurrent Downloading using Coroutines
    suspend fun fetchRealtimeData(): List<StockRow> = coroutineScope {
        stocks.map { stock ->
            async(Dispatchers.IO) {
                fetchStockData(stock)
            }
        }.awaitAll().filterNotNull()
    }

    private fun roundTwoDecimals(value: Double): Double {
        return Math.round(value * 100.0) / 100.0
    }
}


