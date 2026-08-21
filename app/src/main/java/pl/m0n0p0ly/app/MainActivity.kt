package pl.m0n0p0ly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { GameTheme { App() } } }
}

@Composable private fun App() {
    var started by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<GameState?>(null) }
    if (!started) SetupScreen { names -> state = GameEngine.newGame(names); started = true }
    state?.let { game -> GameScreen(game) { state = it } }
}

@Composable private fun SetupScreen(start: (List<String>) -> Unit) {
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
                    Button({ start(names.take(count).map { it.ifBlank { "Gracz" } }) }, Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("ROZPOCZNIJ GRĘ", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable private fun GameScreen(state: GameState, update: (GameState) -> Unit) {
    val current = state.players[state.currentPlayer]
    var showProperties by remember { mutableStateOf(false) }
    var showManagement by remember { mutableStateOf(false) }
    var showTrade by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("M0N0P0LY", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
            PlayerCards(state)
            TurnPanel(state)
            Board(state)
            CurrentFieldPanel(state)
            when {
                state.tradeOffer != null -> TradePanel(state, update)
                state.phase == TurnPhase.CARD_RESOLUTION && state.pendingCard != null -> CardPanel(state, update)
                state.auction != null -> AuctionPanel(state, update)
                else -> ActionBar(state, update, { showProperties = true }, { showManagement = true }, { showTrade = true }, { showHistory = true })
            }
        }
    }
    if (showProperties) PropertiesDialog(state) { showProperties = false }
    if (showManagement) ManagementDialog(state, update) { showManagement = false }
    if (showTrade) TradeDialog(state, update) { showTrade = false }
    if (showHistory) HistoryDialog(state) { showHistory = false }
}

@Composable private fun CardPanel(state: GameState, update: (GameState) -> Unit) { val card = state.pendingCard ?: return; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = if (card.deck == CardDeck.CHANCE) Color(0xFFB76516) else Color(0xFF176B8F)), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp)) { Text(if (card.deck == CardDeck.CHANCE) "SZANSA" else "KASA SPOŁECZNA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp); Text(card.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(vertical = 6.dp)); Text(card.text, color = Color.White, fontSize = 14.sp); Button({ update(GameEngine.reduce(state, GameAction.ResolveCard)) }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("WYKONAJ") } } } }

@Composable private fun AuctionPanel(state: GameState, update: (GameState) -> Unit) { val auction = state.auction ?: return; val bidder = state.players[auction.order[auction.currentIndex]]; val field = GameData.fields[auction.propertyIndex]; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202A36)), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text("LICYTACJA", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Bold); Text(field.name, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Aktualna stawka: M${auction.highBid} • kolej: ${bidder.name}", color = Color.LightGray, fontSize = 12.sp); Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ update(GameEngine.bid(state)) }, Modifier.weight(1f)) { Text(if (auction.highestBidder == null) "LICYTUJ M10" else "PODBIJ M${auction.highBid + 10}", fontSize = 10.sp) }; OutlinedButton({ update(GameEngine.passAuction(state)) }, Modifier.weight(1f)) { Text("PASUJĘ", fontSize = 10.sp) } } } } }

@Composable private fun TradePanel(state: GameState, update: (GameState) -> Unit) { val offer = state.tradeOffer ?: return; val from = state.players.first { it.id == offer.fromId }; val to = state.players.first { it.id == offer.toId }; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202A36)), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text("OFERTA HANDLU", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Bold); Text("${from.name} → ${to.name}", fontWeight = FontWeight.Bold); Text("Gotówka: M${offer.offeredCash} za M${offer.requestedCash}", color = Color.LightGray, fontSize = 12.sp); Text("Nieruchomości: ${offer.offeredPropertyIndexes.size} za ${offer.requestedPropertyIndexes.size}", color = Color.LightGray, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { Button({ update(GameEngine.reduce(state, GameAction.AcceptTrade)) }, Modifier.weight(1f)) { Text("AKCEPTUJ") }; OutlinedButton({ update(GameEngine.reduce(state, GameAction.RejectTrade)) }, Modifier.weight(1f)) { Text("ODRZUĆ") } } } } }

@Composable private fun PropertiesDialog(state: GameState, close: () -> Unit) { val p = state.players[state.currentPlayer]; val owned = state.properties.filter { it.value.ownerId == p.id }.keys.sorted(); AlertDialog(onDismissRequest = close, title = { Text("MOJE WŁASNOŚCI (${owned.size})") }, text = { if (owned.isEmpty()) Text("${p.name} nie ma jeszcze nieruchomości.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(owned) { index -> val field = GameData.fields[index]; val prop = state.properties[index]!!; Surface(color = tileColor(field.group), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(field.name, color = Color(0xFF17191E), fontWeight = FontWeight.Bold); Text("Cena M${field.price ?: 0} • Hipoteka M${GameData.mortgage(field.price ?: 0)}", color = Color(0xFF303030), fontSize = 11.sp) }; Text(if (prop.mortgaged) "HIPOTEKA" else "AKTYWNA", color = Color(0xFF17191E), fontSize = 10.sp, fontWeight = FontWeight.Bold) } } } } }, confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }) }

@Composable private fun TurnPanel(state: GameState) {
    val current = state.players[state.currentPlayer]
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11151B)), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TURA ${state.turnNumber}", color = Color(0xFF8B9CAF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (state.phase == TurnPhase.AUCTION) "LICYTACJA" else if (current.id == 0) "TWOJA TURA" else "TURA ${playerGenitive(current.name).uppercase()}", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text(when {
                    current.inJail -> "Więzienie: wyrzuć dublet, zapłać M50 albo użyj karty"
                    current.id == 0 -> "Rzuć kośćmi, aby się poruszyć"
                    else -> "Przekaż telefon ${playerDative(current.name)}"
                }, color = Color.LightGray, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DiceBox(state.lastDice?.first)
                DiceBox(state.lastDice?.second)
            }
        }
    }
}

@Composable private fun DiceBox(value: Int?) { Surface(color = Color(0xFFF2F3F5), shape = RoundedCornerShape(6.dp), modifier = Modifier.size(31.dp)) { Box(contentAlignment = Alignment.Center) { Text(value?.toString() ?: "–", color = Color(0xFF17191E), fontSize = 18.sp, fontWeight = FontWeight.Black) } } }

@Composable private fun PlayerCards(state: GameState) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { state.players.forEach { p -> val groups = state.properties.filter { it.value.ownerId == p.id }.keys.mapNotNull { BoardDefinitions.byIndex[it]?.group }.distinct(); Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (p.id == state.currentPlayer) Color(0xFF102B3D) else Color(0xFF17191E)), shape = RoundedCornerShape(10.dp), border = if (p.id == state.currentPlayer) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1AA7FF)) else null) { Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(tokenIcon(p.id), fontSize = 20.sp); Text(p.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("M${p.money}", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(playerLocationLabel(p), fontSize = 7.5.sp, lineHeight = 8.sp, color = Color.Gray, maxLines = 2, textAlign = TextAlign.Center); Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 3.dp)) { groups.take(6).forEach { Box(Modifier.size(7.dp).background(tileColor(it), RoundedCornerShape(2.dp))) } } } } } } }

@Composable private fun Board(state: GameState) { val order = listOf(0,1,2,3,4,5,6,7,8,9,10,39,38,37,36,35,34,33,32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,16,15,14,13,12,11); Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFDED4C2)).border(2.dp, Color(0xFF5C6068), RoundedCornerShape(14.dp)).padding(3.dp)) { Column(Modifier.fillMaxSize()) { for (r in 0..9) Row(Modifier.weight(1f)) { for (c in 0..9) { val index = when { r == 0 -> order[c]; r == 9 -> order[30 - c]; c == 0 -> order[39 - r]; c == 9 -> order[10 + r]; else -> -1 }; if (index >= 0) BoardTile(index, state) else Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(Color(0xFFEDE5D7))) } } }; BoardCenter() } }

@Composable private fun BoardCenter() { Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(0.8f).align(Alignment.Center).background(Color(0xFFD8CCB8), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFB4A58F), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("MONOPOLY", color = Color(0xFFB01925), fontSize = 18.sp, fontWeight = FontWeight.Black); Text("CYFROWA EDYCJA", color = Color(0xFF6F665A), fontSize = 8.sp, letterSpacing = 1.sp); Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) { DeckCard(Color(0xFFD87C1D), "?"); DeckCard(Color(0xFF2D91C8), "▣") } } } } }

@Composable private fun DeckCard(color: Color, symbol: String) { Box(Modifier.size(38.dp, 48.dp).rotate(-8f).background(color, RoundedCornerShape(4.dp)).border(2.dp, Color.White, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Text(symbol, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black) } }

@Composable private fun RowScope.BoardTile(index: Int, state: GameState) { val f = GameData.fields[index]; val players = state.players.filter { it.position == index && !it.bankrupt }; val owner = state.properties[index]?.ownerId; val icon = tileIcon(index); Column(Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(tileColor(f.group), RoundedCornerShape(4.dp)).padding(horizontal = 1.dp, vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) { if (icon == null) Text(boardLabel(index, f), fontSize = tileFontSize(boardLabel(index, f)), lineHeight = 6.5.sp, maxLines = 3, overflow = TextOverflow.Clip, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color(0xFF17191E), modifier = Modifier.fillMaxWidth().height(21.dp)) else Box(Modifier.fillMaxWidth().height(21.dp), contentAlignment = Alignment.Center) { BoardTileIcon(icon) }; TilePlayers(players, owner); f.price?.let { Text("M$it", fontSize = 6.sp, color = Color(0xFF17191E), maxLines = 1, modifier = Modifier.height(8.dp)) } }
}

private enum class TileIcon { WATER, POWER, TRAIN, CHANCE, CHEST }
private fun tileIcon(index: Int): TileIcon? = when (index) { 6, 22, 36 -> TileIcon.CHANCE; 2, 18, 33 -> TileIcon.CHEST; 5, 15, 25, 35 -> TileIcon.TRAIN; 12 -> TileIcon.POWER; 28 -> TileIcon.WATER; else -> null }
private fun tileFontSize(label: String) = when { label.length > 18 -> 5.sp; label.length > 13 -> 5.5.sp; else -> 6.2.sp }

@Composable private fun TilePlayers(players: List<PlayerState>, owner: Int?) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.height(11.dp)) { players.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { row.forEach { Text(tokenIcon(it.id), fontSize = 7.sp) } } }; if (owner != null) Text("◆${owner + 1}", fontSize = 5.5.sp, color = Color(0xFF17191E)) } }

@Composable private fun BoardTileIcon(icon: TileIcon) {
    if (icon == TileIcon.CHANCE) {
        Text("?", color = Color(0xFFB76516), fontSize = 19.sp, fontWeight = FontWeight.Black)
        return
    }
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val dark = Color(0xFF263238)
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
                drawRoundRect(Color(0xFF8D5524), topLeft = Offset(w * .12f, h * .3f), size = androidx.compose.ui.geometry.Size(w * .76f, h * .48f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f, w * .06f))
                drawLine(Color(0xFFFFD166), Offset(w * .12f, h * .48f), Offset(w * .88f, h * .48f), lineWidth)
                drawRoundRect(Color(0xFFFFD166), topLeft = Offset(w * .44f, h * .45f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .02f, w * .02f))
            }
            TileIcon.CHANCE -> Unit
        }
    }
}

@Composable private fun CurrentFieldPanel(state: GameState) { val p = state.players[state.currentPlayer]; val f = GameData.fields[p.position]; val definition = BoardDefinitions.byIndex[p.position]; val property = state.properties[p.position]; val ownerName = property?.ownerId?.let { state.players.getOrNull(it)?.name }; val rightInfo = when { p.position == 0 -> "+M200"; definition != null -> "M${definition.price}"; else -> null }; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17191E)), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("AKTUALNE POLE", color = Color.Gray, fontSize = 10.sp); Text(f.name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(when { p.position == 0 -> "Przejście przez START: +M200"; ownerName != null -> "Właściciel: $ownerName • Czynsz: M${definition?.rent?.base ?: "—"}"; definition != null -> "Wolna nieruchomość • Cena M${definition.price}"; else -> "Pole specjalne" }, color = Color.LightGray, fontSize = 12.sp, maxLines = 2) }; if (rightInfo != null) Text(rightInfo, color = Color(0xFF27D17F), fontWeight = FontWeight.Bold, fontSize = 18.sp) } } }

@Composable private fun ManagementDialog(state: GameState, update: (GameState) -> Unit, close: () -> Unit) { val p = state.players[state.currentPlayer]; val owned = state.properties.filter { it.value.ownerId == p.id }.keys.sorted(); AlertDialog(onDismissRequest = close, title = { Text("BUDYNKI I HIPOTEKI") }, text = { if (owned.isEmpty()) Text("${p.name} nie ma jeszcze nieruchomości.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(owned) { index -> val definition = BoardDefinitions.byIndex[index] ?: return@items; val property = state.properties[index]!!; Card(colors = CardDefaults.cardColors(containerColor = tileColor(definition.group)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text(definition.name, color = Color(0xFF17191E), fontWeight = FontWeight.Bold); Text(if (property.hotel) "HOTEL" else "DOMY: ${property.houses}", color = Color(0xFF17191E), fontSize = 11.sp); Text(if (property.mortgaged) "POD HIPOTEKĄ" else "AKTYWNA", color = Color(0xFF17191E), fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { if (definition.kind == Kind.STREET) { if (property.houses < 4 && !property.hotel) TextButton({ update(GameEngine.reduce(state, GameAction.BuyHouse(index))) }) { Text("DOM") }; if (property.houses == 4 && !property.hotel) TextButton({ update(GameEngine.reduce(state, GameAction.BuyHotel(index))) }) { Text("HOTEL") }; if (property.houses > 0 || property.hotel) TextButton({ update(GameEngine.reduce(state, GameAction.SellBuilding(index))) }) { Text("SPRZEDAJ") } }; if (property.mortgaged) TextButton({ update(GameEngine.reduce(state, GameAction.UnmortgageProperty(index))) }) { Text("SPŁAĆ") } else TextButton({ update(GameEngine.reduce(state, GameAction.MortgageProperty(index))) }) { Text("HIPOTEKA") } } } } } } }, confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }) }

@Composable private fun HistoryDialog(state: GameState, close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("HISTORIA GRY") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { items(state.history.reversed()) { entry -> Text(entry.text, color = Color.DarkGray, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) } } }, confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }) }

@Composable private fun TradeDialog(state: GameState, update: (GameState) -> Unit, close: () -> Unit) { val p = state.players[state.currentPlayer]; val targets = state.players.filter { it.id != p.id && !it.bankrupt }; var targetId by remember { mutableIntStateOf(targets.firstOrNull()?.id ?: -1) }; var offeredCashText by remember { mutableStateOf("0") }; var requestedCashText by remember { mutableStateOf("0") }; val offered = remember { mutableStateListOf<Int>() }; val requested = remember { mutableStateListOf<Int>() }; val own = state.properties.filter { it.value.ownerId == p.id && !it.value.mortgaged && it.value.houses == 0 && !it.value.hotel }.keys.sorted(); val targetProps = state.properties.filter { it.value.ownerId == targetId && !it.value.mortgaged && it.value.houses == 0 && !it.value.hotel }.keys.sorted(); AlertDialog(onDismissRequest = close, title = { Text("NOWA OFERTA HANDLU") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Dla kogo?", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { targets.forEach { target -> FilterChip(selected = target.id == targetId, onClick = { targetId = target.id }, label = { Text(target.name.take(8), fontSize = 11.sp) }) } }; OutlinedTextField(offeredCashText, { offeredCashText = it.filter(Char::isDigit) }, label = { Text("Oferowana gotówka") }, singleLine = true); OutlinedTextField(requestedCashText, { requestedCashText = it.filter(Char::isDigit) }, label = { Text("Żądana gotówka") }, singleLine = true); Text("Twoje nieruchomości", fontWeight = FontWeight.Bold); own.forEach { index -> FilterChip(selected = index in offered, onClick = { if (index in offered) offered.remove(index) else offered.add(index) }, label = { Text(GameData.fields[index].name, fontSize = 11.sp) }) }; Text("Nieruchomości gracza", fontWeight = FontWeight.Bold); targetProps.forEach { index -> FilterChip(selected = index in requested, onClick = { if (index in requested) requested.remove(index) else requested.add(index) }, label = { Text(GameData.fields[index].name, fontSize = 11.sp) }) } } }, confirmButton = { TextButton(onClick = { update(GameEngine.reduce(state, GameAction.CreateTrade(TradeOffer(p.id, targetId, offered.toList(), requested.toList(), offeredCashText.toIntOrNull() ?: 0, requestedCashText.toIntOrNull() ?: 0)))); close() }, enabled = targetId >= 0) { Text("WYŚLIJ OFERTĘ") } }, dismissButton = { TextButton(onClick = close) { Text("ANULUJ") } }) }

@Composable private fun ActionBar(state: GameState, update: (GameState) -> Unit, onProperties: () -> Unit, onManagement: () -> Unit, onTrade: () -> Unit, onHistory: () -> Unit) { val p = state.players[state.currentPlayer]; val propertyDecision = state.phase == TurnPhase.PROPERTY_DECISION; val jailDecision = state.phase == TurnPhase.JAIL_DECISION; Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { Button({ val d1=Random.nextInt(1,7); val d2=Random.nextInt(1,7); update(GameEngine.roll(state,d1,d2)) }, enabled = (state.phase == TurnPhase.WAITING_FOR_ROLL && !p.inJail || jailDecision) && !p.bankrupt, modifier = Modifier.weight(1.3f), contentPadding = PaddingValues(vertical = 12.dp)) { Text(if (jailDecision) "RZUT NA WYJŚCIE" else "RZUT KOŚĆMI", fontSize = 10.sp, textAlign = TextAlign.Center) }; if (propertyDecision) { Button({ update(GameEngine.buy(state)) }, enabled = p.money >= (BoardDefinitions.byIndex[p.position]?.price ?: Int.MAX_VALUE), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("KUP", fontSize = 10.sp) }; OutlinedButton({ update(GameEngine.declinePurchase(state)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("NIE / AUKCJA", fontSize = 10.sp) } } else if (jailDecision) { OutlinedButton({ update(GameEngine.leaveJail(state)) }, enabled = p.money >= 50, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("ZAPŁAĆ M50", fontSize = 10.sp) }; if (p.getOutOfJailCards > 0) OutlinedButton({ update(GameEngine.reduce(state, GameAction.UseJailCard)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("KARTA (${p.getOutOfJailCards})", fontSize = 9.sp) } } else { OutlinedButton(onProperties, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("WŁASNOŚCI", fontSize = 8.sp) }; OutlinedButton(onManagement, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("ZARZĄDZAJ", fontSize = 8.sp) }; OutlinedButton(onTrade, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("HANDEL", fontSize = 8.sp) }; OutlinedButton(onHistory, modifier = Modifier.weight(0.8f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("HISTORIA", fontSize = 8.sp) } } }
}

private fun boardLabel(index: Int, field: Field): String = when (index) {
    0 -> "START"
    2, 18, 33 -> "KASA\nSPOŁECZNA"
    4 -> "PODATEK\nDOCHODOWY"
    6, 22, 36 -> "SZANSA"
    10 -> "WIĘZIENIE\nODWIEDZAJĄCY"
    20 -> "BEZPŁATNY\nPARKING"
    30 -> "IDŹ DO\nWIĘZIENIA"
    38 -> "DOMIAR\nPODATKOWY"
    1 -> "KONOPACKA"
    3 -> "STALOWA"
    7 -> "RADZYMIŃSKA"
    8 -> "JAGIELLOŃSKA"
    9 -> "TARGOWA"
    11 -> "PŁOWIECKA"
    13 -> "MARSA"
    14 -> "GROCHOWSKA"
    16 -> "OBOZOWA"
    17 -> "GÓRCZEWSKA"
    19 -> "WOLSKA"
    21 -> "MICKIEWICZA"
    23 -> "SŁOWACKIEGO"
    24 -> "WILSONA"
    26 -> "KRAKOWSKIE\nPRZEDMIEŚCIE"
    27 -> "ŚWIĘTOKRZYSKA"
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
    2, 18, 33 -> "KASA\nSPOŁECZNA"
    4 -> "PODATEK"
    6, 22, 36 -> "SZANSA"
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
private fun tileColor(group: String?) = when (group) { "BRĄZOWA" -> Color(0xFFB98B6B); "JASNONIEBIESKA" -> Color(0xFFA8DADC); "RÓŻOWA" -> Color(0xFFF2A7C3); "POMARAŃCZOWA" -> Color(0xFFF4A261); "CZERWONA" -> Color(0xFFE76F51); "ŻÓŁTA" -> Color(0xFFE9C46A); "ZIELONA" -> Color(0xFF8BC48B); "NIEBIESKA" -> Color(0xFF72A0C1); else -> Color(0xFFEFE7D8) }
