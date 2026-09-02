package com.sanat.trading.screener.swing

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
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class SwingResult(
    val stock: String,
    val close: Double,
    val dma50: Double,
    val dma200: Double,
    val rsi: Double,
    val adx: Double,
    val atrPct: Double,
    val bbWidthPct: Double,
    val rvol: Double,
    val breakoutPct: Double,
    val consolidationPct: Double,
    val rr: Double,
    val signal: String,
    val score: Int,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double
)

class KotlinSwingScreener {

    // Threshold Parameters matching Python script
    private val consolidationDays = 30
    private val maxConsolidationRange = 10.0
    private val minRvol = 1.8
    private val minAdx = 20.0
    private val maxAtrPercent = 5.0
    private val minTurnover = 5e7
    private val minBreakout = 1.0
    private val maxBreakout = 5.0
    private val minRr = 2.0

    // Complete Watchlist matching Python File
    private val stocks = STOCK_LIST

    // ==========================================================
    // SAFE FETCH FUNCTION WITH URL ENCODING
    // ==========================================================
    private fun getData(symbol: String, period: String = "1y", interval: String = "1d"): List<Candle> {
        return try {
            val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8.toString())
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encodedSymbol?interval=$interval&range=$period"
            
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

            val opens = quote.getJSONArray("open")
            val highs = quote.getJSONArray("high")
            val lows = quote.getJSONArray("low")
            val closes = quote.getJSONArray("close")
            val volumes = quote.getJSONArray("volume")

            val candles = mutableListOf<Candle>()
            for (i in 0 until closes.length()) {
                if (!closes.isNull(i) && !opens.isNull(i) && !highs.isNull(i) && !lows.isNull(i) && !volumes.isNull(i)) {
                    candles.add(
                        Candle(
                            open = opens.getDouble(i),
                            high = highs.getDouble(i),
                            low = lows.getDouble(i),
                            close = closes.getDouble(i),
                            volume = volumes.getLong(i)
                        )
                    )
                }
            }
            candles
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==========================================================
    // TECHNICAL INDICATORS CALCULATORS
    // ==========================================================
    private fun calculateRsi(closes: List<Double>, period: Int = 14): List<Double> {
        val rsiList = MutableList(closes.size) { 0.0 }
        if (closes.size <= period) return rsiList

        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gains += diff else losses -= diff
        }

        var avgGain = gains / period
        var avgLoss = losses / period
        rsiList[period] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)))

        for (i in period + 1 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0

            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            rsiList[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)))
        }
        return rsiList
    }

    private fun calculateAdx(candles: List<Candle>, period: Int = 14): List<Double> {
        val adxList = MutableList(candles.size) { 0.0 }
        if (candles.size <= period * 2) return adxList

        val tr = mutableListOf<Double>()
        val plusDm = mutableListOf<Double>()
        val minusDm = mutableListOf<Double>()

        for (i in 1 until candles.size) {
            val h = candles[i].high
            val l = candles[i].low
            val prevC = candles[i - 1].close

            val tr1 = h - l
            val tr2 = Math.abs(h - prevC)
            val tr3 = Math.abs(l - prevC)
            tr.add(maxOf(tr1, tr2, tr3))

            val upMove = h - candles[i - 1].high
            val downMove = candles[i - 1].low - l

            plusDm.add(if (upMove > downMove && upMove > 0) upMove else 0.0)
            minusDm.add(if (downMove > upMove && downMove > 0) downMove else 0.0)
        }

        val dx = mutableListOf<Double>()
        for (i in period until tr.size) {
            val atr = tr.subList(i - period, i).average()
            val pDi = 100.0 * (plusDm.subList(i - period, i).average() / atr)
            val mDi = 100.0 * (minusDm.subList(i - period, i).average() / atr)
            val sumDi = pDi + mDi
            val dxVal = if (sumDi != 0.0) (Math.abs(pDi - mDi) / sumDi) * 100.0 else 0.0
            dx.add(dxVal)
        }

        for (i in period until dx.size) {
            val adxVal = dx.subList(i - period, i).average()
            val indexInCandles = i + period + 1
            if (indexInCandles < candles.size) {
                adxList[indexInCandles] = adxVal
            }
        }
        return adxList
    }

    // ==========================================================
    // SCREENER PIPELINE
    // ==========================================================
    suspend fun runScreener(): List<SwingResult> = coroutineScope {
        println("Downloading NIFTY data...\n")
        val nifty = getData("^NSEI")
        if (nifty.size < 200) {
            println("Failed to download NIFTY benchmark data.")
            return@coroutineScope emptyList()
        }

        val niftyCloses = nifty.map { it.close }
        val nifty50Dma = niftyCloses.takeLast(50).average()
        val nifty200Dma = niftyCloses.takeLast(200).average()
        val niftyLatest = niftyCloses.last()

        val marketBullish = (niftyLatest > nifty50Dma) && (nifty50Dma > nifty200Dma)
        val niftyRef60 = niftyCloses[niftyCloses.size - 60]
        val niftyReturn = ((niftyLatest - niftyRef60) / niftyRef60) * 100.0

        println("Benchmarking setup against NIFTY (Market Bullish: $marketBullish)...")

        stocks.map { stock ->
            async(Dispatchers.IO) {
                analyzeStock(stock, marketBullish, niftyReturn)
            }
        }.awaitAll().filterNotNull()
    }

    private fun analyzeStock(stock: String, marketBullish: Boolean, niftyReturn: Double): SwingResult? {
        try {
            val df = getData(stock, period = "1y", interval = "1d")
            if (df.size < 250) return null

            val weekly = getData(stock, period = "2y", interval = "1wk")
            if (weekly.size < 30) return null

            val closes = df.map { it.close }
            val highs = df.map { it.high }
            val lows = df.map { it.low }
            val volumes = df.map { it.volume }

            // Moving Averages
            val dma50 = closes.takeLast(50).average()
            val dma200 = closes.takeLast(200).average()
            val dma200Prev20 = closes.subList(closes.size - 220, closes.size - 20).average()
            val dma200Slope = dma200 - dma200Prev20

            // Technical Indicators
            val rsiList = calculateRsi(closes)
            val rsi = rsiList.last()
            val rsi5DaysAgo = rsiList[rsiList.size - 6]

            val adxList = calculateAdx(df)
            val adx = adxList.last()

            // ATR Calculation
            val trueRanges = mutableListOf<Double>()
            for (i in 1 until df.size) {
                val tr1 = highs[i] - lows[i]
                val tr2 = Math.abs(highs[i] - closes[i - 1])
                val tr3 = Math.abs(lows[i] - closes[i - 1])
                trueRanges.add(maxOf(tr1, tr2, tr3))
            }
            val atr = trueRanges.takeLast(14).average()

            // Bollinger Bands (20 SMA)
            val last20Closes = closes.takeLast(20)
            val sma20 = last20Closes.average()
            val stdDev = Math.sqrt(last20Closes.map { Math.pow(it - sma20, 2.0) }.average())
            val upperBB = sma20 + (2 * stdDev)
            val lowerBB = sma20 - (2 * stdDev)
            val bbWidth = ((upperBB - lowerBB) / sma20) * 100.0

            // Weekly Trend (30 WMA)
            val weekly30Wma = weekly.map { it.close }.takeLast(30).average()
            val weeklyLatestClose = weekly.last().close
            val weeklyUptrend = weeklyLatestClose > weekly30Wma

            // Latest Candle Data
            val latest = df.last()
            val close = latest.close
            val openPrice = latest.open
            val high = latest.high
            val low = latest.low
            val volume = latest.volume

            // Filters & Conditions
            val bullishAlignment = (close > dma50) && (dma50 > dma200)
            val rising200Dma = dma200Slope > 0
            val strongTrend = adx > minAdx

            // Consolidation Range
            val recentCloses = closes.takeLast(consolidationDays)
            val highestClose = recentCloses.maxOrNull() ?: close
            val lowestClose = recentCloses.minOrNull() ?: close
            val consolidationRange = ((highestClose - lowestClose) / lowestClose) * 100.0
            val consolidated = consolidationRange <= maxConsolidationRange

            val tightVolatility = bbWidth < 10.0

            // Breakout Analysis
            val recentHighs = highs.takeLast(consolidationDays)
            val resistance = recentHighs.subList(0, recentHighs.size - 1).maxOrNull() ?: high
            val breakoutPercent = ((close - resistance) / resistance) * 100.0
            val breakoutConfirmed = close > (resistance * 1.01)
            val validBreakout = breakoutPercent in minBreakout..maxBreakout

            val strongCandle = (close > openPrice) && ((close - low) > ((high - low) * 0.6))

            // Volume Filters
            val avgRecentVolume = volumes.takeLast(consolidationDays).average()
            val oldVolumes = volumes.subList(volumes.size - 90, volumes.size - 30)
            val avgOldVolume = oldVolumes.average()
            val volumeDryup = avgRecentVolume < (avgOldVolume * 0.8)

            val avg20Volume = volumes.takeLast(20).average()
            val rvol = if (avg20Volume != 0.0) volume / avg20Volume else 0.0
            val highRvol = rvol >= minRvol

            val goodRsi = (rsi > 55) && (rsi < 75) && (rsi > rsi5DaysAgo)

            // Relative Strength vs NIFTY
            val stockRef60 = closes[closes.size - 60]
            val stockReturn = ((close - stockRef60) / stockRef60) * 100.0
            val relativeStrength = stockReturn > niftyReturn

            val atrPercent = (atr / close) * 100.0
            val lowVolatility = atrPercent < maxAtrPercent

            val turnoverList = df.takeLast(20).map { it.close * it.volume }
            val avgTurnover = turnoverList.average()
            val liquidStock = avgTurnover > minTurnover

            val high52Week = highs.takeLast(252).maxOrNull() ?: high
            val nearHigh = close >= (high52Week * 0.85)

            // Risk-Reward
            val stopLoss = roundTwoDecimals(close - (1.5 * atr))
            val target1 = roundTwoDecimals(close + (2.0 * atr))
            val target2 = roundTwoDecimals(close + (4.0 * atr))
            val risk = close - stopLoss
            val reward = target2 - close
            val rrRatio = if (risk > 0) reward / risk else 0.0
            val goodRr = rrRatio >= minRr

            // Signal Decision Tree
            val signal = when {
                bullishAlignment && rising200Dma && strongTrend && consolidated && tightVolatility &&
                breakoutConfirmed && validBreakout && strongCandle && volumeDryup && highRvol &&
                goodRsi && relativeStrength && weeklyUptrend && lowVolatility && liquidStock &&
                nearHigh && marketBullish && goodRr -> "ENTRY"

                bullishAlignment && rising200Dma && weeklyUptrend -> "HOLD"
                (close < dma50) || (close < dma200) -> "EXIT"
                else -> "NO SIGNAL"
            }

            // Weighted Scoring System
            var score = 0
            if (bullishAlignment) score += 2
            if (breakoutConfirmed) score += 2
            if (highRvol) score += 2
            if (relativeStrength) score += 2
            if (consolidated) score += 1
            if (weeklyUptrend) score += 1
            if (strongTrend) score += 1
            if (marketBullish) score += 1
            if (strongCandle) score += 1
            if (nearHigh) score += 1

            return SwingResult(
                stock = stock.replace(".NS", ""),
                close = roundTwoDecimals(close),
                dma50 = roundTwoDecimals(dma50),
                dma200 = roundTwoDecimals(dma200),
                rsi = roundTwoDecimals(rsi),
                adx = roundTwoDecimals(adx),
                atrPct = roundTwoDecimals(atrPercent),
                bbWidthPct = roundTwoDecimals(bbWidth),
                rvol = roundTwoDecimals(rvol),
                breakoutPct = roundTwoDecimals(breakoutPercent),
                consolidationPct = roundTwoDecimals(consolidationRange),
                rr = roundTwoDecimals(rrRatio),
                signal = signal,
                score = score,
                stopLoss = stopLoss,
                target1 = target1,
                target2 = target2
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun roundTwoDecimals(valIn: Double): Double = Math.round(valIn * 100.0) / 100.0
}


