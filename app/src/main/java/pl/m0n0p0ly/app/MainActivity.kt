package pl.m0n0p0ly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("M0N0P0LY", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
            PlayerCards(state)
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11151B)), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("TWOJA TURA", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Bold); Text(if (current.inJail) "Jesteś w więzieniu" else "Rzuć kośćmi, aby się poruszyć", color = Color.LightGray, fontSize = 13.sp) }; Text(state.lastRoll?.toString() ?: "–", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black) }
            }
            Board(state)
            CurrentFieldPanel(state)
            if (state.auction != null) AuctionPanel(state, update) else ActionBar(state, update) { showProperties = true }
        }
    }
    if (showProperties) PropertiesDialog(state) { showProperties = false }
}

@Composable private fun AuctionPanel(state: GameState, update: (GameState) -> Unit) { val auction = state.auction ?: return; val bidder = state.players[auction.order[auction.currentIndex]]; val field = GameData.fields[auction.propertyIndex]; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202A36)), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text("LICYTACJA", color = Color(0xFF1AA7FF), fontWeight = FontWeight.Bold); Text(field.name, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Aktualna stawka: M${auction.highBid} • kolej: ${bidder.name}", color = Color.LightGray, fontSize = 12.sp); Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ update(GameEngine.bid(state)) }, Modifier.weight(1f)) { Text(if (auction.highestBidder == null) "LICYTUJ M10" else "PODBIJ M${auction.highBid + 10}", fontSize = 10.sp) }; OutlinedButton({ update(GameEngine.passAuction(state)) }, Modifier.weight(1f)) { Text("PASUJĘ", fontSize = 10.sp) } } } } }

@Composable private fun PropertiesDialog(state: GameState, close: () -> Unit) { val p = state.players[state.currentPlayer]; val owned = state.properties.filter { it.value.ownerId == p.id }.keys.sorted(); AlertDialog(onDismissRequest = close, title = { Text("MOJE WŁASNOŚCI (${owned.size})") }, text = { if (owned.isEmpty()) Text("${p.name} nie ma jeszcze nieruchomości.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(owned) { index -> val field = GameData.fields[index]; val prop = state.properties[index]!!; Surface(color = tileColor(field.group), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(field.name, color = Color(0xFF17191E), fontWeight = FontWeight.Bold); Text("Cena M${field.price ?: 0} • Hipoteka M${GameData.mortgage(field.price ?: 0)}", color = Color(0xFF303030), fontSize = 11.sp) }; Text(if (prop.mortgaged) "HIPOTEKA" else "AKTYWNA", color = Color(0xFF17191E), fontSize = 10.sp, fontWeight = FontWeight.Bold) } } } } }, confirmButton = { TextButton(onClick = close) { Text("ZAMKNIJ") } }) }

@Composable private fun PlayerCards(state: GameState) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { state.players.forEach { p -> Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (p.id == state.currentPlayer) Color(0xFF102B3D) else Color(0xFF17191E)), shape = RoundedCornerShape(10.dp), border = if (p.id == state.currentPlayer) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1AA7FF)) else null) { Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("●", color = tokenColor(p.id), fontSize = 18.sp); Text(p.name.take(8), fontSize = 10.sp, maxLines = 1); Text("M${p.money}", fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(if (p.inJail) "WIĘZIENIE" else "POLE ${p.position}", fontSize = 8.sp, color = Color.Gray) } } } } }

@Composable private fun Board(state: GameState) { val order = listOf(0,1,2,3,4,5,6,7,8,9,10,39,38,37,36,35,34,33,32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,16,15,14,13,12,11); Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFDED4C2)).border(2.dp, Color(0xFF5C6068), RoundedCornerShape(14.dp)).padding(3.dp)) { for (r in 0..9) Row(Modifier.weight(1f)) { for (c in 0..9) { val index = when { r == 0 -> order[c]; r == 9 -> order[30 - c]; c == 0 -> order[39 - r]; c == 9 -> order[10 + r]; else -> -1 }; if (index >= 0) BoardTile(index, state) else Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(Color(0xFFEDE5D7))) } } } }

@Composable private fun RowScope.BoardTile(index: Int, state: GameState) { val f = GameData.fields[index]; val players = state.players.filter { it.position == index && !it.bankrupt }; val owner = state.properties[index]?.ownerId; Column(Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(tileColor(f.group), RoundedCornerShape(4.dp)).padding(horizontal = 2.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(f.name.replace("Ulica ", "").replace("Aleje ", "").replace("Plac ", "").replace("Krakowskie Przedmieście", "Krak. Przedmieście").take(15), fontSize = 8.sp, lineHeight = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color(0xFF17191E), modifier = Modifier.weight(1f).fillMaxWidth()); Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) { players.forEach { Text("●", color = tokenColor(it.id), fontSize = 12.sp) }; if (owner != null) Text("◆${owner + 1}", fontSize = 8.sp, color = Color(0xFF17191E)) }; f.price?.let { Text("M$it", fontSize = 8.sp, color = Color(0xFF17191E), maxLines = 1) } } }

@Composable private fun CurrentFieldPanel(state: GameState) { val p = state.players[state.currentPlayer]; val f = GameData.fields[p.position]; Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17191E)), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("AKTUALNE POLE", color = Color.Gray, fontSize = 10.sp); Text(f.name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(if (f.price != null) "Cena M${f.price}" else "Pole specjalne", color = Color.LightGray, fontSize = 12.sp) }; Text("${p.money} M", color = Color(0xFF27D17F), fontWeight = FontWeight.Bold) } } }

@Composable private fun ActionBar(state: GameState, update: (GameState) -> Unit, onProperties: () -> Unit) { val p = state.players[state.currentPlayer]; Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button({ val d1=Random.nextInt(1,7); val d2=Random.nextInt(1,7); update(GameEngine.roll(state,d1,d2)) }, enabled = !p.inJail && !state.awaitingPurchase && !p.bankrupt, modifier = Modifier.weight(1.3f), contentPadding = PaddingValues(vertical = 12.dp)) { Text(if (p.inJail) "WIĘZIENIE" else "RZUT KOŚĆMI", fontSize = 10.sp, textAlign = TextAlign.Center) }; if (state.awaitingPurchase) { Button({ update(GameEngine.buy(state)) }, enabled = p.money >= (GameData.fields[p.position].price ?: Int.MAX_VALUE), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("KUP", fontSize = 10.sp) }; OutlinedButton({ update(GameEngine.declinePurchase(state)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("NIE / AUKCJA", fontSize = 10.sp) } } else if (p.inJail) { OutlinedButton({ update(GameEngine.leaveJail(state)) }, enabled = p.money >= 50, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("ZAPŁAĆ M50", fontSize = 10.sp) } } else { OutlinedButton(onProperties, modifier = Modifier.weight(0.8f), contentPadding = PaddingValues(vertical = 12.dp)) { Text("WŁASNOŚCI", fontSize = 9.sp) } } }
}

private fun tokenColor(id: Int) = listOf(Color(0xFFE63946), Color(0xFF457B9D), Color(0xFFF4A261), Color(0xFFB5179E), Color(0xFF2A9D8F))[id % 5]
private fun tileColor(group: String?) = when (group) { "BRĄZOWA" -> Color(0xFFB98B6B); "JASNONIEBIESKA" -> Color(0xFFA8DADC); "RÓŻOWA" -> Color(0xFFF2A7C3); "POMARAŃCZOWA" -> Color(0xFFF4A261); "CZERWONA" -> Color(0xFFE76F51); "ŻÓŁTA" -> Color(0xFFE9C46A); "ZIELONA" -> Color(0xFF8BC48B); "NIEBIESKA" -> Color(0xFF72A0C1); else -> Color(0xFFEFE7D8) }
