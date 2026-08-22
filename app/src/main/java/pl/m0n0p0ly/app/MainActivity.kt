package pl.m0n0p0ly.app

import android.graphics.Bitmap
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { GameTheme { App() } } }
}

private enum class AppMode { SETUP, HOST_LOBBY, JOIN_SCAN, CLIENT_LOBBY, GAME }

@Composable private fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var mode by remember { mutableStateOf(AppMode.SETUP) }
    var state by remember { mutableStateOf<GameState?>(null) }
    var localPlayerId by remember { mutableIntStateOf(0) }
    var hotSeat by remember { mutableStateOf(true) }
    var hostSession by remember { mutableStateOf<LanHost?>(null) }
    var clientSession by remember { mutableStateOf<LanClient?>(null) }
    var hostPayload by remember { mutableStateOf<JoinPayload?>(null) }
    var lobbyPlayers by remember { mutableStateOf(listOf<LobbyPlayer>()) }
    var networkError by remember { mutableStateOf<String?>(null) }

    fun onMain(block: () -> Unit) { mainHandler.post(block) }

    fun startHost(name: String) {
        networkError = null
        lateinit var session: LanHost
        session = LanHost(
            context = context,
            hostName = name,
            onLobby = { players -> onMain { lobbyPlayers = players } },
            onAssigned = { id -> onMain { localPlayerId = id } },
            onAction = { _, action -> onMain {
                val current = state ?: return@onMain
                val trustedAction = if (action is GameAction.RollDice) GameAction.RollDice(Random.nextInt(1, 7), Random.nextInt(1, 7)) else action
                val next = GameEngine.reduce(current, trustedAction)
                state = next
                session.broadcastState(next)
            } },
            onError = { message -> onMain { networkError = message } }
        )
        val payload = session.start()
        if (payload == null) return
        hostSession = session
        hostPayload = payload
        lobbyPlayers = session.currentPlayers()
        localPlayerId = 0
        hotSeat = false
        mode = AppMode.HOST_LOBBY
    }

    fun startClient(payload: JoinPayload, name: String) {
        networkError = null
        lateinit var session: LanClient
        session = LanClient(
            payload = payload,
            playerName = name,
            onAssigned = { id -> onMain { localPlayerId = id } },
            onLobby = { players -> onMain { lobbyPlayers = players } },
            onState = { game -> onMain { state = game; hotSeat = false; mode = AppMode.GAME } },
            onError = { message -> onMain { networkError = message } }
        )
        clientSession = session
        mode = AppMode.CLIENT_LOBBY
        session.connect()
    }

    fun leaveNetwork() {
        hostSession?.stop()
        clientSession?.stop()
        hostSession = null
        clientSession = null
        hostPayload = null
        lobbyPlayers = emptyList()
        state = null
        networkError = null
        mode = AppMode.SETUP
    }

    when (mode) {
        AppMode.SETUP -> SetupScreen(
            onHotSeat = { names -> state = GameEngine.newGame(names); hotSeat = true; localPlayerId = 0; mode = AppMode.GAME },
            onHost = ::startHost,
            onJoin = { mode = AppMode.JOIN_SCAN }
        )
        AppMode.HOST_LOBBY -> HostLobbyScreen(hostPayload, lobbyPlayers, networkError, onStart = {
            hostSession?.let { session ->
                val names = lobbyPlayers.sortedBy { it.id }.map { it.name }
                val game = GameEngine.newGame(names)
                state = game
                mode = AppMode.GAME
                session.startGame(game)
            }
        }, onBack = ::leaveNetwork)
        AppMode.JOIN_SCAN -> JoinScanScreen(networkError, onBack = ::leaveNetwork, onJoin = ::startClient)
        AppMode.CLIENT_LOBBY -> ClientLobbyScreen(lobbyPlayers, networkError, onBack = ::leaveNetwork)
        AppMode.GAME -> state?.let { game ->
            val dispatch: (GameAction) -> Unit = { action ->
                if (hotSeat) {
                    state = GameEngine.reduce(game, action)
                } else if (localPlayerId == game.currentPlayer || action is GameAction.CreateTrade) {
                    hostSession?.let { host ->
                        val next = GameEngine.reduce(game, if (action is GameAction.RollDice) GameAction.RollDice(Random.nextInt(1, 7), Random.nextInt(1, 7)) else action)
                        state = next
                        host.broadcastState(next)
                    } ?: clientSession?.send(action)
                }
            }
            GameScreen(game, dispatch, localPlayerId, hotSeat)
        }
    }
}

@Composable private fun SetupScreen(onHotSeat: (List<String>) -> Unit, onHost: (String) -> Unit, onJoin: () -> Unit) {
    var count by remember { mutableIntStateOf(2) }
    val names = remember { mutableStateListOf("Michał", "Ania", "Kamil", "Ola", "Bartek") }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
            Text("M0N0P0LY", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFF1AA7FF))
            Text("Cyfrowa gra planszowa", color = Color.LightGray, modifier = Modifier.padding(bottom = 24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17191E)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Gracze", style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (2..5).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) } }
                    names.take(count).forEachIndexed { i, _ -> OutlinedTextField(names[i], { names[i] = it }, label = { Text("Gracz ${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) }
                    Button({ onHotSeat(names.take(count).map { it.ifBlank { "Gracz" } }) }, Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("HOT-SEAT — JEDEN TELEFON", fontWeight = FontWeight.Bold) }
                    OutlinedButton({ onHost(names.first().ifBlank { "Host" }) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("UTWÓRZ GRĘ LAN") }
                    OutlinedButton(onJoin, Modifier.fillMaxWidth().padding(top = 5.dp)) { Text("DOŁĄCZ PRZEZ KOD QR") }
                }
            }
        }
    }
}

@Composable
private fun HostLobbyScreen(payload: JoinPayload?, players: List<LobbyPlayer>, error: String?, onStart: () -> Unit, onBack: () -> Unit) {
    val qrBitmap = remember(payload?.asQrText()) { payload?.let(::createQrBitmap) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("GRA LAN", color = Color(0xFF1AA7FF), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Zeskanuj kod na pozostałych telefonach", color = Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
            qrBitmap?.let { Image(it.asImageBitmap(), "Kod QR gry", Modifier.padding(vertical = 18.dp).size(230.dp), contentScale = ContentScale.Fit) }
            Text(payload?.asQrText() ?: "Brak adresu Wi‑Fi", color = Color(0xFF9AA7B5), fontSize = 10.sp, textAlign = TextAlign.Center)
            Text("GRACZE ${players.size} / 5", color = Color(0xFF27D17F), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17191E))) {
                Column(Modifier.padding(14.dp)) { players.sortedBy { it.id }.forEach { player -> Text("${player.id + 1}. ${player.name}", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp)) } }
            }
            error?.let { Text(it, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
            Button(onStart, enabled = players.size >= 2, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("ROZPOCZNIJ GRĘ") }
            TextButton(onBack, modifier = Modifier.padding(top = 4.dp)) { Text("ANULUJ") }
        }
    }
}

@Composable
private fun JoinScanScreen(error: String?, onBack: () -> Unit, onJoin: (JoinPayload, String) -> Unit) {
    var payload by remember { mutableStateOf<JoinPayload?>(null) }
    var name by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val parsed = result.contents?.let(JoinPayload::parse)
        if (parsed == null) scanError = "To nie jest kod gry M0n0p0ly." else { payload = parsed; scanError = null }
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text("DOŁĄCZ DO GRY", color = Color(0xFF1AA7FF), fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Zeskanuj kod QR pokazany na telefonie hosta.", color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Twoja nazwa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({ scanner.launch(ScanOptions().apply { setPrompt("Skieruj aparat na kod QR hosta"); setBeepEnabled(true); setOrientationLocked(true) }) }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (payload == null) "SKANUJ KOD QR" else "ZESKANOWANO — SKANUJ PONOWNIE") }
            payload?.let { Text("Host: ${it.host}:${it.port}", color = Color(0xFF27D17F), modifier = Modifier.padding(top = 12.dp)) }
            (scanError ?: error)?.let { Text(it, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
            Button({ payload?.let { onJoin(it, name.ifBlank { "Gracz" }) } }, enabled = payload != null && name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("DOŁĄCZ DO GRY") }
            TextButton(onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("ANULUJ") }
        }
    }
}

@Composable
private fun ClientLobbyScreen(players: List<LobbyPlayer>, error: String?, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text("OCZEKIWANIE NA HOSTA", color = Color(0xFF1AA7FF), fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Host rozpocznie grę, gdy wszyscy dołączą.", color = Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF17191E))) { Column(Modifier.padding(14.dp)) { players.sortedBy { it.id }.forEach { Text("${it.id + 1}. ${it.name}", color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(vertical = 5.dp)) } } }
            error?.let { Text(it, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
            TextButton(onBack, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)) { Text("ROZŁĄCZ") }
        }
    }
}

private fun createQrBitmap(payload: JoinPayload): Bitmap {
    val size = 640
    val matrix = QRCodeWriter().encode(payload.asQrText(), BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

@Composable private fun GameScreen(state: GameState, dispatch: (GameAction) -> Unit, localPlayerId: Int, hotSeat: Boolean) {
    val current = state.players[state.currentPlayer]
    val canAct = hotSeat || current.id == localPlayerId
    var showProperties by remember { mutableStateOf(false) }
    var showManagement by remember { mutableStateOf(false) }
    var showTrade by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var screenVisible by remember { mutableStateOf(false) }
    var diceAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { screenVisible = true }
    val latestRollMarker = state.history.indexOfLast { it.text.contains("rzucił") }
    LaunchedEffect(latestRollMarker) {
        if (latestRollMarker >= 0) {
            diceAnimating = true
            delay(650)
            diceAnimating = false
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        AnimatedVisibility(
            visible = screenVisible,
            enter = fadeIn(tween(320)) + scaleIn(initialScale = 0.97f, animationSpec = tween(320)),
            modifier = Modifier.fillMaxSize()
        ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("M0N0P0LY", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
            PlayerCards(state)
            TurnPanel(state, latestRollMarker, localPlayerId, hotSeat)
            Board(state)
            CurrentFieldPanel(state)
            when {
                state.tradeOffer != null -> TradePanel(state, dispatch, canAct)
                state.phase == TurnPhase.CARD_RESOLUTION && state.pendingCard != null -> CardPanel(state, dispatch, canAct)
                state.auction != null -> AuctionPanel(state, dispatch, canAct)
                else -> ActionBar(state, dispatch, diceAnimating, localPlayerId, hotSeat, { showProperties = true }, { showManagement = true }, { showTrade = true }, { showHistory = true })
            }
        }
        }
    }
    if (showProperties) PropertyCarouselDialog(state, dispatch, localPlayerId, canAct, managementMode = false) { showProperties = false }
    if (showManagement) PropertyCarouselDialog(state, dispatch, localPlayerId, canAct, managementMode = true) { showManagement = false }
    if (showTrade) TradeDialog(state, dispatch, localPlayerId, canAct) { showTrade = false }
    if (showHistory) HistoryDialog(state) { showHistory = false }
}

@Composable private fun CardPanel(state: GameState, dispatch: (GameAction) -> Unit, canAct: Boolean) { val card = state.pendingCard ?: return; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = if (card.deck == CardDeck.CHANCE) Color(0xFFB76516) else Color(0xFF176B8F)), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp)) { Text(if (card.deck == CardDeck.CHANCE) "SZANSA" else "KASA SPOŁECZNA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp); Text(card.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(vertical = 6.dp)); Text(card.text, color = Color.White, fontSize = 14.sp); Button({ dispatch(GameAction.ResolveCard) }, enabled = canAct, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("WYKONAJ") } } } }

@Composable private fun AuctionPanel(state: GameState, dispatch: (GameAction) -> Unit, canAct: Boolean) { val auction = state.auction ?: return; val bidder = state.players[auction.order[auction.currentIndex]]; val field = GameData.fields[auction.propertyIndex]; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202A36)), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text("LICYTACJA", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Bold); Text(field.name, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Aktualna stawka: M${auction.highBid} • kolej: ${bidder.name}", color = Color.LightGray, fontSize = 12.sp); Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ dispatch(GameAction.PlaceBid) }, enabled = canAct, Modifier.weight(1f)) { Text(if (auction.highestBidder == null) "LICYTUJ M10" else "PODBIJ M${auction.highBid + 10}", fontSize = 10.sp) }; OutlinedButton({ dispatch(GameAction.PassAuction) }, enabled = canAct, Modifier.weight(1f)) { Text("PASUJĘ", fontSize = 10.sp) } } } } }

@Composable private fun TradePanel(state: GameState, dispatch: (GameAction) -> Unit, canAct: Boolean) { val offer = state.tradeOffer ?: return; val from = state.players.first { it.id == offer.fromId }; val to = state.players.first { it.id == offer.toId }; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202A36)), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text("OFERTA HANDLU", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Bold); Text("${from.name} → ${to.name}", fontWeight = FontWeight.Bold); Text("Gotówka: M${offer.offeredCash} za M${offer.requestedCash}", color = Color.LightGray, fontSize = 12.sp); Text("Nieruchomości: ${offer.offeredPropertyIndexes.size} za ${offer.requestedPropertyIndexes.size}", color = Color.LightGray, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { Button({ dispatch(GameAction.AcceptTrade) }, enabled = canAct, Modifier.weight(1f)) { Text("AKCEPTUJ") }; OutlinedButton({ dispatch(GameAction.RejectTrade) }, enabled = canAct, Modifier.weight(1f)) { Text("ODRZUĆ") } } } } }

@Composable private fun PropertiesDialog(state: GameState, close: () -> Unit) { val p = state.players[state.currentPlayer]; val owned = state.properties.filter { it.value.ownerId == p.id }.keys.sorted(); AlertDialog(onDismissRequest = close, title = { Text("MOJE WŁASNOŚCI (${owned.size})") }, text = { if (owned.isEmpty()) Text("${p.name} nie ma jeszcze nieruchomości.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(owned) { index -> val field = GameData.fields[index]; val prop = state.properties[index]!!; Surface(color = tileColor(field.group), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(field.name, color = Color(0xFF17191E), fontWeight = FontWeight.Bold); Text("Cena M${field.price ?: 0} • Hipoteka M${GameData.mortgage(field.price ?: 0)}", color = Color(0xFF303030), fontSize = 11.sp) }; Text(if (prop.mortgaged) "HIPOTEKA" else "AKTYWNA", color = Color(0xFF17191E), fontSize = 10.sp, fontWeight = FontWeight.Bold) } } } } }, confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }) }

@Composable
private fun PropertyCarouselDialog(state: GameState, dispatch: (GameAction) -> Unit, playerId: Int, canAct: Boolean, managementMode: Boolean, close: () -> Unit) {
    val player = state.players.getOrNull(playerId) ?: state.players[state.currentPlayer]
    val owned = state.properties.filter { it.value.ownerId == player.id }.keys.sorted()
    var selectedPage by remember { mutableIntStateOf(0) }
    val page = selectedPage.coerceIn(0, (owned.size - 1).coerceAtLeast(0))

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (managementMode) "ZARZĄDZANIE NIERUCHOMOŚCIAMI" else "MOJE WŁASNOŚCI") },
        text = {
            if (owned.isEmpty()) {
                Text("${player.name} nie ma jeszcze nieruchomości.")
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(owned.size) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    if (owned.size > 1 && totalDrag < -48f) selectedPage = (selectedPage + 1) % owned.size
                                    if (owned.size > 1 && totalDrag > 48f) selectedPage = (selectedPage - 1 + owned.size) % owned.size
                                }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TextButton(enabled = owned.size > 1, onClick = { selectedPage = (page - 1 + owned.size) % owned.size }) { Text("‹", fontSize = 28.sp) }
                        Text("${page + 1} / ${owned.size}", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        TextButton(enabled = owned.size > 1, onClick = { selectedPage = (page + 1) % owned.size }) { Text("›", fontSize = 28.sp) }
                    }
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { it } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally { -it / 3 } + fadeOut(tween(160)))
                            } else {
                                (slideInHorizontally { -it } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally { it / 3 } + fadeOut(tween(160)))
                            }
                        },
                        label = "property_card_transition"
                    ) { visiblePage ->
                        owned.getOrNull(visiblePage)?.let { index -> FullPropertyCard(state, index, managementMode, canAct, dispatch) }
                    }
                    Text("Przesuń kartę w lewo lub w prawo", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }
    )
}

@Composable
private fun FullPropertyCard(state: GameState, index: Int, managementMode: Boolean, canAct: Boolean, dispatch: (GameAction) -> Unit) {
    val definition = BoardDefinitions.byIndex[index] ?: return
    val property = state.properties[index] ?: return
    val ownerName = property.ownerId?.let { state.players.getOrNull(it)?.name } ?: "BANK"
    val accent = when (definition.kind) {
        Kind.STREET -> tileColor(definition.group)
        Kind.STATION -> Color(0xFFB9BEC4)
        Kind.UTILITY -> Color(0xFFA8DADC)
        Kind.SPECIAL -> Color(0xFFEFE7D8)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EBDD)),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth().height(24.dp).background(accent, RoundedCornerShape(4.dp)))
            Text(definition.name.uppercase(), color = Color(0xFF17191E), fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("CENA ZAKUPU  M${definition.price}", color = Color(0xFF17191E), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("WŁAŚCICIEL: $ownerName", color = Color(0xFF3D3A36), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Divider(color = Color(0xFFB6AA99))

            when (definition.kind) {
                Kind.STREET -> {
                    val rent = definition.rent ?: return@Column
                    Text("CZYNSZ", color = Color(0xFF17191E), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    RentLine("Bez budynków", "M${rent.base}")
                    RentLine("Pełna grupa", "M${rent.monopoly}")
                    rent.houses.forEachIndexed { houseCount, amount -> RentLine("${houseCount + 1} ${if (houseCount == 0) "DOM" else "DOMY"}", "M$amount") }
                    RentLine("HOTEL", "M${rent.hotel}")
                    Divider(color = Color(0xFFB6AA99))
                    Text("DOM  M${definition.houseCost}   •   HOTEL  M${definition.hotelCost}", color = Color(0xFF17191E), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Kind.STATION -> {
                    Text("CZYNSZ WEDŁUG LICZBY DWORCÓW", color = Color(0xFF17191E), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    listOf(25, 50, 100, 200).forEachIndexed { count, rent -> RentLine("${count + 1} dworzec${if (count == 0) "" else "e"}", "M$rent") }
                }
                Kind.UTILITY -> {
                    Text("CZYNSZ WEDŁUG WYNIKU RZUTU", color = Color(0xFF17191E), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    RentLine("1 obiekt", "4 × wynik rzutu")
                    RentLine("2 obiekty", "10 × wynik rzutu")
                }
                Kind.SPECIAL -> Unit
            }

            Text("HIPOTEKA: M${GameData.mortgage(definition.price)}   •   SPŁATA: M${GameData.redemption(definition.price)}", color = Color(0xFF3D3A36), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (property.mortgaged) Text("POD HIPOTEKĄ — CZYNSZ NIE JEST POBIERANY", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            else Text(if (property.hotel) "AKTUALNIE: HOTEL" else "AKTUALNIE: ${property.houses} DOMÓW", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            if (managementMode) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (definition.kind == Kind.STREET && property.houses < 4 && !property.hotel && !property.mortgaged) Button({ dispatch(GameAction.BuyHouse(index)) }, enabled = canAct, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("DOM", fontSize = 10.sp) }
                    if (definition.kind == Kind.STREET && property.houses == 4 && !property.hotel && !property.mortgaged) Button({ dispatch(GameAction.BuyHotel(index)) }, enabled = canAct, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("HOTEL", fontSize = 10.sp) }
                    if (definition.kind == Kind.STREET && (property.houses > 0 || property.hotel)) OutlinedButton({ dispatch(GameAction.SellBuilding(index)) }, enabled = canAct, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("SPRZEDAJ", fontSize = 9.sp) }
                }
                if (property.mortgaged) Button({ dispatch(GameAction.UnmortgageProperty(index)) }, enabled = canAct, modifier = Modifier.fillMaxWidth()) { Text("SPŁAĆ HIPOTEKĘ") }
                else if (property.houses == 0 && !property.hotel) OutlinedButton({ dispatch(GameAction.MortgageProperty(index)) }, enabled = canAct, modifier = Modifier.fillMaxWidth()) { Text("ZASTAW NIERUCHOMOŚĆ") }
            }
        }
    }
}

@Composable
private fun RentLine(label: String, amount: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF3D3A36), fontSize = 11.sp)
        Text(amount, color = Color(0xFF17191E), fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable private fun TurnPanel(state: GameState, rollMarker: Int, localPlayerId: Int, hotSeat: Boolean) {
    val current = state.players[state.currentPlayer]
    val isLocalTurn = hotSeat && current.id == 0 || !hotSeat && current.id == localPlayerId
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11151B)), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TURA ${state.turnNumber}", color = Color(0xFF8B9CAF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                AnimatedContent(targetState = state.phase to current.name, label = "turn_message") { (phase, playerName) ->
                    Column {
                        Text(if (phase == TurnPhase.AUCTION) "LICYTACJA" else if (isLocalTurn) "TWOJA TURA" else "TURA ${playerGenitive(playerName).uppercase()}", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Text(when {
                            current.inJail -> "Więzienie: wyrzuć dublet, zapłać M50 albo użyj karty"
                            isLocalTurn -> "Rzuć kośćmi, aby się poruszyć"
                            hotSeat -> "Przekaż telefon ${playerDative(playerName)}"
                            else -> "Poczekaj na ruch gracza"
                        }, color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DiceBox(state.lastDice?.first, rollMarker)
                DiceBox(state.lastDice?.second, rollMarker)
            }
        }
    }
}

@Composable private fun DiceBox(value: Int?, animationKey: Int) {
    var displayValue by remember(animationKey) { mutableIntStateOf(value ?: 0) }
    var rolling by remember(animationKey) { mutableStateOf(false) }
    LaunchedEffect(animationKey) {
        if (value == null) {
            displayValue = 0
            rolling = false
        } else {
            rolling = true
            repeat(9) {
                displayValue = Random.nextInt(1, 7)
                delay(55)
            }
            displayValue = value
            rolling = false
        }
    }
    val rotation by animateFloatAsState(if (rolling) 8f else 0f, tween(120), label = "dice_rotation")
    Surface(color = Color(0xFFF2F3F5), shape = RoundedCornerShape(7.dp), modifier = Modifier.size(35.dp).rotate(rotation)) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (value == null && !rolling) "–" else displayValue.toString(), color = Color(0xFF17191E), fontSize = 19.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable private fun PlayerCards(state: GameState) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { state.players.forEach { p -> val groups = state.properties.filter { it.value.ownerId == p.id }.keys.mapNotNull { BoardDefinitions.byIndex[it]?.group }.distinct(); Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (p.id == state.currentPlayer) Color(0xFF102B3D) else Color(0xFF17191E)), shape = RoundedCornerShape(10.dp), border = if (p.id == state.currentPlayer) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1AA7FF)) else null) { Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(tokenIcon(p.id), fontSize = 20.sp); Text(p.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("M${p.money}", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(playerLocationLabel(p), fontSize = 7.5.sp, lineHeight = 8.sp, color = Color.Gray, maxLines = 2, textAlign = TextAlign.Center); Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 3.dp)) { groups.take(6).forEach { Box(Modifier.size(7.dp).background(tileColor(it), RoundedCornerShape(2.dp))) } } } } } } }

@Composable private fun Board(state: GameState) { val order = listOf(0,1,2,3,4,5,6,7,8,9,10,39,38,37,36,35,34,33,32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,16,15,14,13,12,11); Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0E1319)).border(2.dp, Color(0xFF34404D), RoundedCornerShape(14.dp)).padding(3.dp)) { Column(Modifier.fillMaxSize()) { for (r in 0..9) Row(Modifier.weight(1f)) { for (c in 0..9) { val index = when { r == 0 -> order[c]; r == 9 -> order[30 - c]; c == 0 -> order[39 - r]; c == 9 -> order[10 + r]; else -> -1 }; if (index >= 0) BoardTile(index, state, tileTextRotation(r, c)) else Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(Color(0xFF202731))) } } }; BoardCenter() } }

private fun tileTextRotation(row: Int, column: Int): Float = when {
    row == 0 -> 45f
    row == 9 -> -45f
    column == 0 -> -45f
    column == 9 -> 45f
    else -> 0f
}

@Composable private fun BoardCenter() { Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(0.8f).align(Alignment.Center).background(Color(0xFF151B23), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF3B4856), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("MONOPOLY", color = Color(0xFFFF5263), fontSize = 18.sp, fontWeight = FontWeight.Black); Text("CYFROWA EDYCJA", color = Color(0xFF9AA7B5), fontSize = 8.sp, letterSpacing = 1.sp); Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) { DeckCard(Color(0xFFD87C1D), "?"); DeckCard(Color(0xFF2D91C8), "▣") } } } } }

@Composable private fun DeckCard(color: Color, symbol: String) { Box(Modifier.size(38.dp, 48.dp).rotate(-8f).background(color, RoundedCornerShape(4.dp)).border(2.dp, Color.White, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Text(symbol, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black) } }

@Composable private fun RowScope.BoardTile(index: Int, state: GameState, rotation: Float) { val f = GameData.fields[index]; val players = state.players.filter { it.position == index && !it.bankrupt }; val owner = state.properties[index]?.ownerId; val icon = tileIcon(index); val label = boardLabel(index, f); val labelRotation = if (icon == null) rotation else 0f; Column(Modifier.weight(1f).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(4.dp)).background(boardTileColor(f.group)).padding(horizontal = 1.dp, vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) { if (icon == null) BoardTileLabel(label, labelRotation) else Box(Modifier.fillMaxWidth().height(25.dp), contentAlignment = Alignment.Center) { BoardTileIcon(icon) }; TilePlayers(players, owner); f.price?.let { Text("M$it", fontSize = 6.sp, color = Color(0xFFE9EEF3), maxLines = 1, modifier = Modifier.height(7.dp)) } }
}

@Composable
private fun BoardTileLabel(label: String, rotation: Float) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val longestLine = label.lines().maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
        val tileSize = minOf(maxWidth.value, maxHeight.value)
        val diagonalWidth = tileSize * 1.28f
        val baseSize = when {
            longestLine >= 14 -> 6.0f
            longestLine >= 12 -> 6.2f
            longestLine >= 10 -> 6.8f
            longestLine >= 8 -> 7.2f
            else -> 7.8f
        }
        val availableWidth = if (rotation == 0f) maxWidth.value else diagonalWidth
        val fittedSize = minOf(baseSize * 0.70f, availableWidth / (longestLine * 0.55f)).coerceIn(3.8f, 5.5f)
        Text(
            text = label,
            fontSize = fittedSize.sp,
            lineHeight = (fittedSize + 1.1f).sp,
            maxLines = 2,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.1f).sp,
            color = Color(0xFFF1F5F9),
            modifier = Modifier
                .requiredWidth(if (rotation == 0f) maxWidth else diagonalWidth.dp)
                .rotate(rotation)
        )
    }
}

private enum class TileIcon { WATER, POWER, TRAIN, CHANCE, CHEST }
private fun tileIcon(index: Int): TileIcon? = when (index) { 7, 22, 36 -> TileIcon.CHANCE; 2, 17, 33 -> TileIcon.CHEST; 5, 15, 25, 35 -> TileIcon.TRAIN; 12 -> TileIcon.POWER; 28 -> TileIcon.WATER; else -> null }
@Composable private fun TilePlayers(players: List<PlayerState>, owner: Int?) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.height(16.dp)) { players.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { row.forEach { Text(tokenIcon(it.id), fontSize = 10.sp, lineHeight = 10.sp) } } }; if (owner != null) Text("◆${owner + 1}", fontSize = 5.5.sp, color = Color(0xFFE9EEF3), lineHeight = 5.5.sp) } }

@Composable private fun BoardTileIcon(icon: TileIcon) {
    if (icon == TileIcon.CHANCE) {
        Text("?", color = Color(0xFFB76516), fontSize = 23.sp, fontWeight = FontWeight.Black)
        return
    }
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val dark = Color(0xFFE8EEF4)
        val lineWidth = w * 0.09f
        val line = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            TileIcon.WATER -> {
                drawLine(dark, Offset(w * .12f, h * .35f), Offset(w * .68f, h * .35f), lineWidth, StrokeCap.Round)
                drawLine(dark, Offset(w * .68f, h * .35f), Offset(w * .68f, h * .62f), lineWidth, StrokeCap.Round)
                drawLine(dark, Offset(w * .55f, h * .18f), Offset(w * .82f, h * .18f), lineWidth, StrokeCap.Round)
                drawLine(dark, Offset(w * .68f, h * .18f), Offset(w * .68f, h * .35f), lineWidth, StrokeCap.Round)
                val drop = Path().apply { moveTo(w * .68f, h * .62f); cubicTo(w * .50f, h * .78f, w * .58f, h * .95f, w * .68f, h * .95f); cubicTo(w * .79f, h * .95f, w * .86f, h * .78f, w * .68f, h * .62f); close() }
                drawPath(drop, Color(0xFF2D9CDB))
            }
            TileIcon.POWER -> {
                drawCircle(Color(0xFFFFD54F), radius = w * .27f, center = Offset(w * .5f, h * .35f))
                drawCircle(dark, radius = w * .27f, center = Offset(w * .5f, h * .35f), style = line)
                drawLine(dark, Offset(w * .39f, h * .58f), Offset(w * .61f, h * .58f), lineWidth)
                drawLine(dark, Offset(w * .42f, h * .72f), Offset(w * .58f, h * .72f), lineWidth)
                drawLine(Color(0xFFFFB300), Offset(w * .48f, h * .08f), Offset(w * .43f, h * .28f), lineWidth)
            }
            TileIcon.TRAIN -> {
                drawRoundRect(dark, topLeft = Offset(w * .12f, h * .34f), size = androidx.compose.ui.geometry.Size(w * .76f, h * .34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f, w * .06f))
                drawRect(Color(0xFFECEFF1), topLeft = Offset(w * .22f, h * .42f), size = androidx.compose.ui.geometry.Size(w * .18f, h * .12f))
                drawRect(Color(0xFFECEFF1), topLeft = Offset(w * .48f, h * .42f), size = androidx.compose.ui.geometry.Size(w * .18f, h * .12f))
                drawLine(dark, Offset(w * .72f, h * .34f), Offset(w * .72f, h * .18f), lineWidth)
                drawLine(dark, Offset(w * .64f, h * .18f), Offset(w * .82f, h * .18f), lineWidth)
                drawCircle(dark, radius = w * .11f, center = Offset(w * .3f, h * .78f))
                drawCircle(dark, radius = w * .11f, center = Offset(w * .7f, h * .78f))
            }
            TileIcon.CHEST -> {
                drawRoundRect(Color(0xFFB9782A), topLeft = Offset(w * .12f, h * .3f), size = androidx.compose.ui.geometry.Size(w * .76f, h * .48f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f, w * .06f))
                drawLine(Color(0xFFFFD166), Offset(w * .12f, h * .48f), Offset(w * .88f, h * .48f), lineWidth)
                drawRoundRect(Color(0xFFFFD166), topLeft = Offset(w * .44f, h * .45f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .02f, w * .02f))
            }
            TileIcon.CHANCE -> Unit
        }
    }
}

@Composable private fun CurrentFieldPanel(state: GameState) { val p = state.players[state.currentPlayer]; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17191E)), shape = RoundedCornerShape(12.dp)) { AnimatedContent(targetState = p.position, label = "current_field_transition") { position -> val f = GameData.fields[position]; val definition = BoardDefinitions.byIndex[position]; val property = state.properties[position]; val ownerName = property?.ownerId?.let { state.players.getOrNull(it)?.name }; val rightInfo = when { position == 0 -> "+M200"; definition != null && ownerName == null -> "M${definition.price}"; else -> null }; Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("AKTUALNE POLE", color = Color.Gray, fontSize = 10.sp); Text(f.name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(when { position == 0 -> "Przejście przez START: +M200"; ownerName != null -> "Właściciel: $ownerName • Czynsz: M${definition?.rent?.base ?: "—"}"; definition != null -> "Wolna nieruchomość • Cena M${definition.price}"; else -> "Pole specjalne" }, color = Color.LightGray, fontSize = 12.sp, maxLines = 2) }; if (rightInfo != null) Text(rightInfo, color = Color(0xFF27D17F), fontWeight = FontWeight.Bold, fontSize = 18.sp) } } } }

@Composable private fun ManagementDialog(state: GameState, update: (GameState) -> Unit, close: () -> Unit) { val p = state.players[state.currentPlayer]; val owned = state.properties.filter { it.value.ownerId == p.id }.keys.sorted(); AlertDialog(onDismissRequest = close, title = { Text("BUDYNKI I HIPOTEKI") }, text = { if (owned.isEmpty()) Text("${p.name} nie ma jeszcze nieruchomości.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(owned) { index -> val definition = BoardDefinitions.byIndex[index] ?: return@items; val property = state.properties[index]!!; Card(colors = CardDefaults.cardColors(containerColor = tileColor(definition.group)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text(definition.name, color = Color(0xFF17191E), fontWeight = FontWeight.Bold); Text(if (property.hotel) "HOTEL" else "DOMY: ${property.houses}", color = Color(0xFF17191E), fontSize = 11.sp); Text(if (property.mortgaged) "POD HIPOTEKĄ" else "AKTYWNA", color = Color(0xFF17191E), fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { if (definition.kind == Kind.STREET) { if (property.houses < 4 && !property.hotel) TextButton({ update(GameEngine.reduce(state, GameAction.BuyHouse(index))) }) { Text("DOM") }; if (property.houses == 4 && !property.hotel) TextButton({ update(GameEngine.reduce(state, GameAction.BuyHotel(index))) }) { Text("HOTEL") }; if (property.houses > 0 || property.hotel) TextButton({ update(GameEngine.reduce(state, GameAction.SellBuilding(index))) }) { Text("SPRZEDAJ") } }; if (property.mortgaged) TextButton({ update(GameEngine.reduce(state, GameAction.UnmortgageProperty(index))) }) { Text("SPŁAĆ") } else TextButton({ update(GameEngine.reduce(state, GameAction.MortgageProperty(index))) }) { Text("HIPOTEKA") } } } } } } }, confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }) }

@Composable private fun HistoryDialog(state: GameState, close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("HISTORIA GRY") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { items(state.history.reversed()) { entry -> Text(entry.text, color = Color.DarkGray, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) } } }, confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }) }

@Composable private fun TradeDialog(state: GameState, dispatch: (GameAction) -> Unit, playerId: Int, canAct: Boolean, close: () -> Unit) { val p = state.players.getOrNull(playerId) ?: state.players[state.currentPlayer]; val targets = state.players.filter { it.id != p.id && !it.bankrupt }; var targetId by remember { mutableIntStateOf(targets.firstOrNull()?.id ?: -1) }; var offeredCashText by remember { mutableStateOf("0") }; var requestedCashText by remember { mutableStateOf("0") }; val offered = remember { mutableStateListOf<Int>() }; val requested = remember { mutableStateListOf<Int>() }; val own = state.properties.filter { it.value.ownerId == p.id && !it.value.mortgaged && it.value.houses == 0 && !it.value.hotel }.keys.sorted(); val targetProps = state.properties.filter { it.value.ownerId == targetId && !it.value.mortgaged && it.value.houses == 0 && !it.value.hotel }.keys.sorted(); AlertDialog(onDismissRequest = close, title = { Text("NOWA OFERTA HANDLU") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Dla kogo?", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { targets.forEach { target -> FilterChip(selected = target.id == targetId, onClick = { targetId = target.id }, label = { Text(target.name.take(8), fontSize = 11.sp) }) } }; OutlinedTextField(offeredCashText, { offeredCashText = it.filter(Char::isDigit) }, label = { Text("Oferowana gotówka") }, singleLine = true); OutlinedTextField(requestedCashText, { requestedCashText = it.filter(Char::isDigit) }, label = { Text("Żądana gotówka") }, singleLine = true); Text("Twoje nieruchomości", fontWeight = FontWeight.Bold); own.forEach { index -> FilterChip(selected = index in offered, onClick = { if (index in offered) offered.remove(index) else offered.add(index) }, label = { Text(GameData.fields[index].name, fontSize = 11.sp) }) }; Text("Nieruchomości gracza", fontWeight = FontWeight.Bold); targetProps.forEach { index -> FilterChip(selected = index in requested, onClick = { if (index in requested) requested.remove(index) else requested.add(index) }, label = { Text(GameData.fields[index].name, fontSize = 11.sp) }) } } }, confirmButton = { TextButton(onClick = { dispatch(GameAction.CreateTrade(TradeOffer(p.id, targetId, offered.toList(), requested.toList(), offeredCashText.toIntOrNull() ?: 0, requestedCashText.toIntOrNull() ?: 0))); close() }, enabled = targetId >= 0 && canAct) { Text("WYŚLIJ OFERTĘ") } }, dismissButton = { TextButton(onClick = close) { Text("ANULUJ") } }) }

@Composable
private fun ActionBar(state: GameState, dispatch: (GameAction) -> Unit, diceAnimating: Boolean, localPlayerId: Int, hotSeat: Boolean, onProperties: () -> Unit, onManagement: () -> Unit, onTrade: () -> Unit, onHistory: () -> Unit) {
    val p = state.players[state.currentPlayer]
    val canAct = hotSeat || p.id == localPlayerId
    AnimatedContent(targetState = state.phase, label = "action_bar_transition") { phase ->
        val propertyDecision = phase == TurnPhase.PROPERTY_DECISION
        val jailDecision = phase == TurnPhase.JAIL_DECISION
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button({ dispatch(GameAction.RollDice(Random.nextInt(1, 7), Random.nextInt(1, 7))) }, enabled = !diceAnimating && canAct && (phase == TurnPhase.WAITING_FOR_ROLL && !p.inJail || jailDecision) && !p.bankrupt, modifier = Modifier.weight(1.3f), contentPadding = PaddingValues(vertical = 12.dp)) {
                Text(if (diceAnimating) "LOSOWANIE…" else if (jailDecision) "RZUT NA WYJŚCIE" else if (!canAct) "POCZEKAJ" else "RZUT KOŚĆMI", fontSize = 10.sp, textAlign = TextAlign.Center)
            }
            if (propertyDecision) {
                Button({ dispatch(GameAction.BuyProperty) }, enabled = canAct && p.money >= (BoardDefinitions.byIndex[p.position]?.price ?: Int.MAX_VALUE), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("KUP", fontSize = 10.sp) }
                OutlinedButton({ dispatch(GameAction.DeclineProperty) }, enabled = canAct, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("NIE / AUKCJA", fontSize = 10.sp) }
            } else if (jailDecision) {
                OutlinedButton({ dispatch(GameAction.PayJailFee) }, enabled = canAct && p.money >= 50, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("ZAPŁAĆ M50", fontSize = 10.sp) }
                if (p.getOutOfJailCards > 0) OutlinedButton({ dispatch(GameAction.UseJailCard) }, enabled = canAct, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("KARTA (${p.getOutOfJailCards})", fontSize = 9.sp) }
            } else {
                OutlinedButton(onProperties, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("WŁASNOŚCI", fontSize = 8.sp) }
                OutlinedButton(onManagement, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("ZARZĄDZAJ", fontSize = 8.sp) }
                OutlinedButton(onTrade, enabled = canAct, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("HANDEL", fontSize = 8.sp) }
                OutlinedButton(onHistory, modifier = Modifier.weight(0.8f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("HISTORIA", fontSize = 8.sp) }
            }
        }
    }
}

private fun boardLabel(index: Int, field: Field): String = when (index) {
    0 -> "START"
    2, 17, 33 -> "KASA\nSPOŁECZNA"
    4 -> "PODATEK\nDOCHODOWY"
    7, 22, 36 -> "SZANSA"
    10 -> "WIĘZIENIE\nODWIEDZAJĄCY"
    20 -> "BEZPŁATNY\nPARKING"
    30 -> "IDŹ DO\nWIĘZIENIA"
    38 -> "DOMIAR\nPODATKOWY"
    1 -> "KONOPACKA"
    3 -> "STALOWA"
    6 -> "RADZYMIŃSKA"
    8 -> "JAGIELLOŃSKA"
    9 -> "TARGOWA"
    11 -> "PŁOWIECKA"
    13 -> "MARSA"
    14 -> "GROCHOWSKA"
    16 -> "OBOZOWA"
    18 -> "GÓRCZEWSKA"
    19 -> "WOLSKA"
    21 -> "MICKIEWICZA"
    23 -> "SŁOWACKIEGO"
    24 -> "WILSONA"
    26 -> "ŚWIĘTOKRZYSKA"
    27 -> "KRAKOWSKIE\nPRZEDMIEŚCIE"
    29 -> "NOWY ŚWIAT"
    31 -> "TRZECH\nKRZYŻY"
    32 -> "MARSZAŁKOWSKA"
    34 -> "ALEJE\nJEROZOLIMSKIE"
    37 -> "BELWEDERSKA"
    39 -> "ALEJE\nUJAZDOWSKIE"
    else -> field.name
}

private fun playerLocationLabel(player: PlayerState): String = when (player.position) {
    0 -> "START"
    2, 17, 33 -> "KASA\nSPOŁECZNA"
    4 -> "PODATEK"
    7, 22, 36 -> "SZANSA"
    10, 30 -> "WIĘZIENIE"
    20 -> "PARKING"
    38 -> "DOMIAR"
    else -> GameData.fields[player.position].name.removePrefix("Ulica ").removePrefix("Aleje ").removePrefix("Plac ").replace("Krakowskie Przedmieście", "KRAKOWSKIE\nPRZEDMIEŚCIE")
}

private fun playerGenitive(name: String): String = when (name.trim().lowercase()) {
    "michał" -> "Michała"
    "ania" -> "Ani"
    "kamil" -> "Kamila"
    "ola" -> "Oli"
    "bartek" -> "Bartka"
    else -> name
}

private fun playerDative(name: String): String = when (name.trim().lowercase()) {
    "michał" -> "Michałowi"
    "ania" -> "Ani"
    "kamil" -> "Kamilowi"
    "ola" -> "Oli"
    "bartek" -> "Bartkowi"
    else -> name
}

private fun tokenIcon(id: Int) = listOf("🚗", "🐧", "🎩", "🐴", "🦖")[id % 5]
private fun tokenColor(id: Int) = listOf(Color(0xFFE63946), Color(0xFF457B9D), Color(0xFFF4A261), Color(0xFFB5179E), Color(0xFF2A9D8F))[id % 5]
private fun boardTileColor(group: String?) = when (group) {
    "BRĄZOWA" -> Color(0xFF5A4034)
    "JASNONIEBIESKA" -> Color(0xFF245A68)
    "RÓŻOWA" -> Color(0xFF713B5B)
    "POMARAŃCZOWA" -> Color(0xFF7A4A25)
    "CZERWONA" -> Color(0xFF7C353B)
    "ŻÓŁTA" -> Color(0xFF74611E)
    "ZIELONA" -> Color(0xFF315D43)
    "NIEBIESKA" -> Color(0xFF294D73)
    else -> Color(0xFF252C35)
}
private fun tileColor(group: String?) = when (group) { "BRĄZOWA" -> Color(0xFFB98B6B); "JASNONIEBIESKA" -> Color(0xFFA8DADC); "RÓŻOWA" -> Color(0xFFF2A7C3); "POMARAŃCZOWA" -> Color(0xFFF4A261); "CZERWONA" -> Color(0xFFE76F51); "ŻÓŁTA" -> Color(0xFFE9C46A); "ZIELONA" -> Color(0xFF8BC48B); "NIEBIESKA" -> Color(0xFF72A0C1); else -> Color(0xFFEFE7D8) }
