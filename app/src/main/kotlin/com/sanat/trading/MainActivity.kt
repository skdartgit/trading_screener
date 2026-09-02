package com.sanat.trading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sanat.trading.screener.intraday.KotlinStockScreener
import com.sanat.trading.screener.intraday.SignalResult
import com.sanat.trading.screener.marketstructure.MarketStructureScreener
import com.sanat.trading.screener.marketstructure.MssResult
import com.sanat.trading.screener.swing.KotlinSwingScreener
import com.sanat.trading.screener.swing.SwingResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Ivory = Color(0xFFF7F3EA)
private val Porcelain = Color(0xFFFFFCF6)
private val Navy = Color(0xFF13283B)
private val Teal = Color(0xFF176B73)
private val Gold = Color(0xFFC79A4A)
private val Ink = Color(0xFF243746)
private val Muted = Color(0xFF66747E)
private val Green = Color(0xFF197A55)
private val Red = Color(0xFFB44C4C)
private val Amber = Color(0xFF9A711D)

private val PremiumLight = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Navy,
    background = Ivory,
    onBackground = Ink,
    surface = Porcelain,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE7DD),
    onSurfaceVariant = Muted
)

private val TimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

enum class Screen(val title: String) {
    INTRADAY("Intraday Screener"),
    SWING("Swing Screener"),
    MSS("Market Structure Screener"),
    CONTROL("Control Screeners")
}

data class IntradayState(
    val time: String? = null,
    val buying: List<SignalResult> = emptyList(),
    val selling: List<SignalResult> = emptyList(),
    val running: Boolean = false,
    val error: String? = null
)

data class SwingState(
    val time: String? = null,
    val results: List<SwingResult> = emptyList(),
    val running: Boolean = false,
    val error: String? = null
)

data class MssState(
    val time: String? = null,
    val bullish: List<MssResult> = emptyList(),
    val bearish: List<MssResult> = emptyList(),
    val noShift: List<MssResult> = emptyList(),
    val running: Boolean = false,
    val error: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            SanatTradingApp()
        }
    }
}

@Composable
fun SanatTradingApp() {
    MaterialTheme(colorScheme = PremiumLight) {

        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()

        var authenticated by remember { mutableStateOf(false) }

        var screen by remember { mutableStateOf(Screen.INTRADAY) }
        var menuOpen by remember { mutableStateOf(false) }
        var intraday by remember { mutableStateOf(IntradayState()) }
        var swing by remember { mutableStateOf(SwingState()) }
        var mss by remember { mutableStateOf(MssState()) }
        var intradayJob by remember { mutableStateOf<Job?>(null) }
        var swingJob by remember { mutableStateOf<Job?>(null) }
        var mssJob by remember { mutableStateOf<Job?>(null) }

        fun now() = LocalDateTime.now().format(TimeFormatter)

        fun runIntraday() {
            intradayJob?.cancel()
            intraday = intraday.copy(running = true, error = null, time = now())
            intradayJob = scope.launch {
                try {
                    val all = withContext(Dispatchers.IO) { KotlinStockScreener().fetchRealtimeData() }
                    val buying = all.filter {
                        (it.ohOl == "OL" || it.ohOl == "ONL") &&
                            it.buySell == "Buy" && it.gapStatus == "Btn"
                    }.map {
                        SignalResult(it.symbol, it.changePct, it.volumePct, it.ohOl)
                    }.sortedByDescending { it.volumePct }

                    val selling = all.filter {
                        (it.ohOl == "OH" || it.ohOl == "ONH") &&
                            it.buySell == "Sell" && it.gapStatus == "Btn"
                    }.map {
                        SignalResult(it.symbol, it.changePct, it.volumePct, it.ohOl)
                    }.sortedByDescending { it.volumePct }

                    intraday = intraday.copy(
                        buying = buying,
                        selling = selling,
                        running = false,
                        error = if (all.isEmpty()) "No market data was returned." else null
                    )
                } catch (_: CancellationException) {
                    intraday = intraday.copy(running = false)
                } catch (e: Exception) {
                    intraday = intraday.copy(running = false, error = e.message ?: "Unexpected error")
                }
            }
        }

        fun runSwing() {
            swingJob?.cancel()
            swing = swing.copy(running = true, error = null, time = now())
            swingJob = scope.launch {
                try {
                    val raw = withContext(Dispatchers.IO) { KotlinSwingScreener().runScreener() }
                    val priority = mapOf("ENTRY" to 1, "HOLD" to 2, "EXIT" to 3, "NO SIGNAL" to 4)
                    val sorted = raw.sortedWith(
                        compareBy<SwingResult> { priority[it.signal] ?: 5 }
                            .thenByDescending { it.score }
                    )
                    swing = swing.copy(
                        results = sorted,
                        running = false,
                        error = if (sorted.isEmpty()) "No valid stocks processed." else null
                    )
                } catch (_: CancellationException) {
                    swing = swing.copy(running = false)
                } catch (e: Exception) {
                    swing = swing.copy(running = false, error = e.message ?: "Unexpected error")
                }
            }
        }

        fun runMss() {
            mssJob?.cancel()
            mss = mss.copy(running = true, error = null, time = now())
            mssJob = scope.launch {
                try {
                    val screener = MarketStructureScreener()
                    val results = coroutineScope {
                        screener.stocks.map { stock ->
                            async(Dispatchers.IO) { screener.analyzeStock(stock) }
                        }.awaitAll().filterNotNull()
                    }
                    mss = mss.copy(
                        bullish = results.filter { it.signal == "Bullish Shift" },
                        bearish = results.filter { it.signal == "Bearish Shift" },
                        noShift = results.filter { it.signal == "No Shift" },
                        running = false,
                        error = if (results.isEmpty()) "No valid stocks processed." else null
                    )
                } catch (_: CancellationException) {
                    mss = mss.copy(running = false)
                } catch (e: Exception) {
                    mss = mss.copy(running = false, error = e.message ?: "Unexpected error")
                }
            }
        }

        fun stopIntraday() { intradayJob?.cancel(); intradayJob = null; intraday = intraday.copy(running = false) }
        fun stopSwing() { swingJob?.cancel(); swingJob = null; swing = swing.copy(running = false) }
        fun stopMss() { mssJob?.cancel(); mssJob = null; mss = mss.copy(running = false) }
        fun clearIntraday() { stopIntraday(); intraday = IntradayState() }
        fun clearSwing() { stopSwing(); swing = SwingState() }
        fun clearMss() { stopMss(); mss = MssState() }

        if (!authenticated) {
            PinAuthenticationScreen(
                onAuthenticated = {
                    authenticated = true
                }
            )
        } else {
            Scaffold(
                topBar = {
                Header(
                    menuOpen = menuOpen,
                    onMenu = { menuOpen = !menuOpen },
                    screen = screen
                )
            },
            bottomBar = { Footer() },
            containerColor = Ivory
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Ivory)
            ) {
                when (screen) {
                    Screen.INTRADAY -> IntradayPage(
                        state = intraday,
                        onRun = { runIntraday() },
                        onStop = { stopIntraday() },
                        onClear = { clearIntraday() }
                    )
                    Screen.SWING -> SwingPage(
                        state = swing,
                        onRun = { runSwing() },
                        onStop = { stopSwing() },
                        onClear = { clearSwing() }
                    )
                    Screen.MSS -> MssPage(
                        state = mss,
                        onRun = { runMss() },
                        onStop = { stopMss() },
                        onClear = { clearMss() }
                    )
                    Screen.CONTROL -> ControlPage(
                        intraday = intraday,
                        swing = swing,
                        mss = mss,
                        onIntraday = {
                            screen = Screen.INTRADAY
                            runIntraday()
                        },
                        onSwing = {
                            screen = Screen.SWING
                            runSwing()
                        },
                        onMss = {
                            screen = Screen.MSS
                            runMss()
                        },
                        onStopIntraday = { stopIntraday() },
                        onStopSwing = { stopSwing() },
                        onStopMss = { stopMss() },
                        onClearIntraday = { clearIntraday() },
                        onClearSwing = { clearSwing() },
                        onClearMss = { clearMss() }
                        onResetPin = {
                            authenticated = false
                        }
                    )
                }

                AnimatedVisibility(
                    visible = menuOpen,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    NavigationDrawerPanel(
                        selected = screen,
                        onSelect = {
                            screen = it
                            menuOpen = false
                        }
                    )
                }
            }
          }  
        }
    }
}

@Composable
private fun Header(menuOpen: Boolean, onMenu: () -> Unit, screen: Screen) {
    Surface(color = Navy, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Open navigation",
                    tint = Gold
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Sanat's Trading",
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    screen.title,
                    color = Color(0xFFD8C89C),
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp
                )
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Gold)
            )
        }
    }
}

@Composable
private fun NavigationDrawerPanel(selected: Screen, onSelect: (Screen) -> Unit) {
    val configuration = LocalConfiguration.current
    val width = (configuration.screenWidthDp * 0.56f).dp
    Card(
        modifier = Modifier
            .width(width)
            .heightIn(max = 390.dp)
            .padding(start = 8.dp, top = 8.dp),
        shape = RoundedCornerShape(0.dp, 18.dp, 18.dp, 18.dp),
        colors = CardDefaults.cardColors(containerColor = Porcelain),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                "NAVIGATION",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Screen.entries.forEach { item ->
                val active = item == selected
                Surface(
                    onClick = { onSelect(item) },
                    color = if (active) Color(0xFFE5F0ED) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) Gold else Color(0xFFB8C0C3))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.title,
                            color = if (active) Teal else Ink,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Footer() {
    Surface(color = Navy, shadowElevation = 8.dp) {
        Text(
            "@ 2026 Built & Developed by Sanat Dey",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            textAlign = TextAlign.Center,
            color = Color(0xFFE8DAB7),
            fontFamily = FontFamily.Serif,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun PageShell(
    title: String,
    subtitle: String,
    time: String?,
    running: Boolean,
    error: String?,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = Color(0xFFD8E2E3), fontSize = 12.sp, lineHeight = 18.sp)
                if (time != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("Last run: $time", color = Color(0xFFE6D3A2), fontSize = 12.sp)
                }
            }
        }

        if (error != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFEC))
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(16.dp),
                    color = Red,
                    fontSize = 13.sp
                )
            }
        }

        if (running) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE9F3F1))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Teal
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Screener is running…", color = Teal, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        content()

        ActionButtons(
            running = running,
            onRun = onRun,
            onStop = onStop,
            onClear = onClear
        )
    }
}

@Composable
private fun ActionButtons(
    running: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onRun,
            enabled = !running,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Run", fontWeight = FontWeight.Bold) }

        Button(
            onClick = onStop,
            enabled = running,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF1D7D2),
                contentColor = Red
            ),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Stop", fontWeight = FontWeight.Bold) }

        Button(
            onClick = onClear,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE7E1D4),
                contentColor = Ink
            ),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Clear", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun IntradayPage(
    state: IntradayState,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit
) {
    PageShell(
        title = "Intraday Screener",
        subtitle = "Live market signal engine using the uploaded IntradayScreener.kt logic.",
        time = state.time,
        running = state.running,
        error = state.error,
        onRun = onRun,
        onStop = onStop,
        onClear = onClear
    ) {
        SignalSection("BUYING SIGNALS", state.buying, true)
        SignalSection("SELLING SIGNALS", state.selling, false)
    }
}

@Composable
private fun SignalSection(title: String, rows: List<SignalResult>, positive: Boolean) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Porcelain),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (positive) Green else Red)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (rows.isEmpty()) {
                Text(
                    if (title.startsWith("BUY")) "No Buying Signals" else "No Selling Signals",
                    color = Muted,
                    fontSize = 13.sp
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0ECE3), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text("Stock", Modifier.weight(1.3f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Change %", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Volume %", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("OH/OL", Modifier.weight(.8f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(row.symbol, Modifier.weight(1.3f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("%.2f".format(row.changePct), Modifier.weight(1f), fontSize = 12.sp)
                        Text("%.2f".format(row.volumePct), Modifier.weight(1f), fontSize = 12.sp)
                        Text(row.ohOl, Modifier.weight(.8f), color = if (positive) Green else Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = Color(0xFFE8E2D7))
                }
            }
        }
    }
}

@Composable
private fun SwingPage(
    state: SwingState,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit
) {
    PageShell(
        title = "Swing Screener",
        subtitle = "Advanced institutional swing scanner with the original DMA, RSI, ADX, ATR, Bollinger, RVOL, breakout and risk/reward logic.",
        time = state.time,
        running = state.running,
        error = state.error,
        onRun = onRun,
        onStop = onStop,
        onClear = onClear
    ) {
        if (state.results.isEmpty() && !state.running) {
            EmptyState("Run the screener to display the SwingScreener.kt results.")
        } else {
            state.results.forEach { SwingCard(it) }
            val best = state.results.filter { it.signal == "ENTRY" && it.score >= 10 }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1ED))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("BEST BREAKOUT CANDIDATES", color = Teal, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (best.isEmpty()) {
                        Text(
                            "No strong breakout candidates meeting all institutional filters today.",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    } else {
                        best.forEach {
                            Text(
                                "🔥 ${it.stock} | Entry Price: ₹${it.close} | SL: ₹${it.stopLoss} | Target 1: ₹${it.target1} | Target 2: ₹${it.target2} | Score: ${it.score}",
                                color = Ink,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwingCard(r: SwingResult) {
    val signalColor = when (r.signal) {
        "ENTRY" -> Green
        "EXIT" -> Red
        "HOLD" -> Amber
        else -> Muted
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Porcelain),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.stock, color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text(r.signal, color = signalColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Text("Score ${r.score}", color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Metric("Close", "%.2f".format(r.close))
                Metric("50DMA", "%.2f".format(r.dma50))
                Metric("200DMA", "%.2f".format(r.dma200))
                Metric("RSI", "%.2f".format(r.rsi))
                Metric("ADX", "%.2f".format(r.adx))
                Metric("RVOL", "%.2f".format(r.rvol))
                Metric("Break%", "%.2f".format(r.breakoutPct))
                Metric("RR", "%.2f".format(r.rr))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "ATR ${r.atrPct}%  •  BB Width ${r.bbWidthPct}%  •  Consolidation ${r.consolidationPct}%  •  SL ₹${r.stopLoss}  •  T1 ₹${r.target1}  •  T2 ₹${r.target2}",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Surface(
        color = Color(0xFFF0ECE3),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            Modifier
                .widthIn(min = 72.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(label, color = Muted, fontSize = 9.sp)
            Text(value, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MssPage(
    state: MssState,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit
) {
    PageShell(
        title = "Market Structure Screener",
        subtitle = "20-session market-structure shift detection, grouped exactly as Bullish Shift, Bearish Shift and No Shift.",
        time = state.time,
        running = state.running,
        error = state.error,
        onRun = onRun,
        onStop = onStop,
        onClear = onClear
    ) {
        if (state.bullish.isEmpty() && state.bearish.isEmpty() && state.noShift.isEmpty() && !state.running) {
            EmptyState("Run the screener to display the MarketStructureScreener.kt results.")
        } else {
            MssSection("BULLISH STRUCTURE SHIFT", state.bullish, Green)
            MssSection("BEARISH STRUCTURE SHIFT", state.bearish, Red)
            MssSection("NO STRUCTURE SHIFT", state.noShift, Muted)
        }
    }
}

@Composable
private fun MssSection(title: String, rows: List<MssResult>, color: Color) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Porcelain),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("${rows.size}", color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Text("None found.", color = Muted, fontSize = 13.sp)
            } else {
                rows.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Metric("Stock", r.stock)
                        Metric("Close", "%.2f".format(r.close))
                        Metric("Prev High", "%.2f".format(r.prevSwingHigh))
                        Metric("Prev Low", "%.2f".format(r.prevSwingLow))
                        Metric("Signal", r.signal)
                    }
                    HorizontalDivider(color = Color(0xFFE8E2D7))
                }
            }
        }
    }
}

@Composable
private fun ControlPage(
    intraday: IntradayState,
    swing: SwingState,
    mss: MssState,
    onIntraday: () -> Unit,
    onSwing: () -> Unit,
    onMss: () -> Unit,
    onStopIntraday: () -> Unit,
    onStopSwing: () -> Unit,
    onStopMss: () -> Unit,
    onClearIntraday: () -> Unit,
    onClearSwing: () -> Unit,
    onClearMss: () -> Unit,
    onResetPin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Control Screeners",
            color = Navy,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp
        )
        Text(
            "Run, stop or clear each screener independently. Stop preserves the last completed output.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        ControlCard("Intraday Screener", intraday.running, intraday.time, onIntraday, onStopIntraday, onClearIntraday)
        ControlCard("Swing Screener", swing.running, swing.time, onSwing, onStopSwing, onClearSwing)
        ControlCard("Market Structure Screener", mss.running, mss.time, onMss, onStopMss, onClearMss)
        Spacer(Modifier.height(8.dp))

        PinResetCard(
            onResetPin = onResetPin
        )
    }
}

@Composable
private fun ControlCard(
    title: String,
    running: Boolean,
    time: String?,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Porcelain),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text(if (running) "RUNNING" else "READY", color = if (running) Teal else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (time != null) {
                Spacer(Modifier.height(5.dp))
                Text("Last run: $time", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            ActionButtons(running, onRun, onStop, onClear)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Porcelain)
    ) {
        Text(message, Modifier.padding(18.dp), color = Muted, fontSize = 13.sp)
    }
}


@Composable
private fun PinAuthenticationScreen(
    onAuthenticated: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Ivory
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Porcelain
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "Sanat's Trading",
                        color = Navy,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 27.sp,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "SECURE ACCESS",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.8.sp
                    )

                    Spacer(Modifier.height(28.dp))

                    Text(
                        "Enter your 6-digit PIN",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                pin = it
                                error = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text("PIN")
                        },
                        placeholder = {
                            Text("••••••")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        isError = error != null,
                        supportingText = {
                            if (error != null) {
                                Text(
                                    error!!,
                                    color = Red
                                )
                            } else {
                                Text("Enter exactly 6 digits")
                            }
                        }
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (!PinManager.isValidPin(pin)) {
                                error = "PIN must contain exactly 6 digits."
                            } else if (PinManager.verifyPin(context, pin)) {
                                pin = ""
                                error = null
                                onAuthenticated()
                            } else {
                                error = "Incorrect PIN. Please try again."
                                pin = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = pin.length == 6,
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Teal
                        )
                    ) {
                        Text(
                            "Unlock App",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                "Protected Screener Access",
                color = Muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PinResetCard(
    onResetPin: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var showDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF7E8)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                "Security",
                color = Navy,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Change the 6-digit authentication PIN used to enter the app.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    showDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Navy
                )
            ) {
                Text(
                    "Reset PIN",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDialog) {
        PinResetDialog(
            onDismiss = {
                showDialog = false
            },
            onPinChanged = {
                showDialog = false
                onResetPin()
            }
        )
    }
}

@Composable
private fun PinResetDialog(
    onDismiss: () -> Unit,
    onPinChanged: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Reset PIN",
                color = Navy,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {

                Text(
                    "Create a new 6-digit PIN.",
                    color = Muted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            newPin = it
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("New PIN")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    )
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            confirmPin = it
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("Confirm PIN")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    isError = error != null
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        error!!,
                        color = Red,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {

                    when {
                        !PinManager.isValidPin(newPin) -> {
                            error = "New PIN must contain exactly 6 digits."
                        }

                        newPin != confirmPin -> {
                            error = "PINs do not match."
                        }

                        else -> {
                            PinManager.setPin(context, newPin)

                            newPin = ""
                            confirmPin = ""
                            error = null

                            onPinChanged()
                        }
                    }
                },
                enabled = newPin.length == 6 && confirmPin.length == 6,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal
                )
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
