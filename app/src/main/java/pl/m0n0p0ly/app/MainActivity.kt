package pl.m0n0p0ly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

class MainActivity : ComponentActivity() { override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { M0n0p0lyApp() } } }

@Composable fun M0n0p0lyApp() {
    var screen by remember { mutableStateOf("home") }; var players by remember { mutableStateOf(listOf("Gracz 1")) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2A9D8F), secondary = Color(0xFFE07A5F))) {
        Surface(Modifier.fillMaxSize()) { when(screen) {
            "home" -> Column(Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("M0n0p0ly", style=MaterialTheme.typography.displaySmall); Text("Wersja testowa 0.1", modifier=Modifier.padding(8.dp)); Button({ screen="setup" }, Modifier.fillMaxWidth()) { Text("Utwórz grę") }; OutlinedButton({ screen="join" }, Modifier.fillMaxWidth()) { Text("Dołącz do gry") } }
            "join" -> Setup(true) { screen="game" }
            "setup" -> Setup(false) { screen="game" }
            else -> GameScreen(players)
        } }
    }
}

@Composable fun Setup(join: Boolean, start: () -> Unit) { var name by remember { mutableStateOf("") }; Column(Modifier.padding(24.dp).fillMaxSize()) { Text(if(join) "Dołącz do gry" else "Utwórz grę", style=MaterialTheme.typography.headlineMedium); OutlinedTextField(name,{name=it},label={Text("Twoja nazwa")},modifier=Modifier.fillMaxWidth().padding(vertical=16.dp)); if(join) OutlinedTextField("",{},label={Text("Kod sesji")},modifier=Modifier.fillMaxWidth()); Button(start,enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Przejdź do gry")} } }

@Composable fun GameScreen(players: List<String>) { var pos by remember { mutableIntStateOf(0) }; var money by remember { mutableIntStateOf(1500) }; var dice by remember { mutableStateOf<Int?>(null) }; var turn by remember { mutableIntStateOf(0) }; val field=GameData.fields[pos]; Column(Modifier.padding(12.dp)) { Text("Tura: ${players[turn % players.size]}", style=MaterialTheme.typography.titleLarge); Text("Saldo: M$money", style=MaterialTheme.typography.headlineSmall); Card(Modifier.fillMaxWidth().padding(vertical=12.dp)){ Column(Modifier.padding(16.dp)){Text("${pos}. ${field.name}", style=MaterialTheme.typography.titleLarge); field.price?.let{Text("Cena: M$it")}; dice?.let{Text("Rzut: $it")}}}; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({ val d=Random.nextInt(1,7)+Random.nextInt(1,7); dice=d; val old=pos; pos=(pos+d)%40; if(old+d>=40) money+=200; turn++ },Modifier.weight(1f)){Text("Rzuć kośćmi")}; OutlinedButton({ if(field.price!=null && money>=field.price) money-=field.price },Modifier.weight(1f)){Text("Kup")}}; Text("Plansza",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=16.dp)); LazyColumn { itemsIndexed(GameData.fields){i,f -> Text("$i  ${f.name}${f.price?.let{" • M$it"} ?: ""}",Modifier.fillMaxWidth().padding(5.dp).background(if(i==pos) Color(0xFFDFF3F1) else Color.Transparent))} } } }
