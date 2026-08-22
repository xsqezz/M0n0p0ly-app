package pl.m0n0p0ly.app

import android.net.wifi.WifiManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.random.Random

data class LobbyPlayer(val id: Int, val name: String)

data class JoinPayload(val host: String, val port: Int, val gameId: String) {
    fun asQrText(): String = "mono://join?host=$host&port=$port&game=$gameId"

    companion object {
        fun parse(value: String): JoinPayload? = runCatching {
            val uri = URI(value.trim())
            if (uri.scheme != "mono" || uri.host != "join") return null
            val query = uri.rawQuery.orEmpty().split('&').associate {
                val pair = it.split('=', limit = 2)
                pair[0] to pair.getOrElse(1) { "" }
            }
            val host = query["host"].orEmpty()
            val port = query["port"]?.toIntOrNull() ?: return null
            val game = query["game"].orEmpty()
            if (host.isBlank() || game.isBlank() || port !in 1..65535) null else JoinPayload(host, port, game)
        }.getOrNull()
    }
}

object LanNetwork {
    private const val PROTOCOL_VERSION = 1

    fun localIpv4(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val raw = wifi?.connectionInfo?.ipAddress ?: 0
        if (raw == 0) return null
        return listOf(raw and 0xff, raw shr 8 and 0xff, raw shr 16 and 0xff, raw shr 24 and 0xff).joinToString(".")
    }

    fun encodeHello(name: String, gameId: String) = JSONObject()
        .put("type", "HELLO")
        .put("version", PROTOCOL_VERSION)
        .put("name", name)
        .put("game", gameId)

    fun encodeAction(action: GameAction) = JSONObject()
        .put("type", "ACTION")
        .put("action", actionToJson(action))

    fun encodeLobby(players: List<LobbyPlayer>) = JSONObject()
        .put("type", "LOBBY")
        .put("players", JSONArray().apply { players.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })

    fun encodeAssigned(id: Int) = JSONObject().put("type", "ASSIGNED").put("id", id)

    fun encodeState(state: GameState) = JSONObject()
        .put("type", "STATE")
        .put("state", stateToJson(state))

    fun encodeError(message: String) = JSONObject().put("type", "ERROR").put("message", message)

    fun decodeLobby(json: JSONObject): List<LobbyPlayer> {
        val result = mutableListOf<LobbyPlayer>()
        val players = json.optJSONArray("players") ?: return result
        for (i in 0 until players.length()) {
            val player = players.optJSONObject(i) ?: continue
            result += LobbyPlayer(player.optInt("id"), player.optString("name"))
        }
        return result
    }

    fun decodeAction(json: JSONObject): GameAction? {
        val action = json.optJSONObject("action") ?: return null
        return actionFromJson(action)
    }

    fun decodeState(json: JSONObject): GameState? {
        val state = json.optJSONObject("state") ?: return null
        return stateFromJson(state)
    }

    private fun actionToJson(action: GameAction) = when (action) {
        is GameAction.RollDice -> JSONObject().put("kind", "ROLL").put("first", action.first).put("second", action.second)
        GameAction.BuyProperty -> JSONObject().put("kind", "BUY")
        GameAction.DeclineProperty -> JSONObject().put("kind", "DECLINE")
        GameAction.PlaceBid -> JSONObject().put("kind", "BID")
        GameAction.PassAuction -> JSONObject().put("kind", "PASS_AUCTION")
        GameAction.PayJailFee -> JSONObject().put("kind", "PAY_JAIL")
        GameAction.ResolveCard -> JSONObject().put("kind", "RESOLVE_CARD")
        GameAction.UseJailCard -> JSONObject().put("kind", "USE_JAIL_CARD")
        is GameAction.BuyHouse -> JSONObject().put("kind", "BUY_HOUSE").put("index", action.propertyIndex)
        is GameAction.BuyHotel -> JSONObject().put("kind", "BUY_HOTEL").put("index", action.propertyIndex)
        is GameAction.SellBuilding -> JSONObject().put("kind", "SELL_BUILDING").put("index", action.propertyIndex)
        is GameAction.MortgageProperty -> JSONObject().put("kind", "MORTGAGE").put("index", action.propertyIndex)
        is GameAction.UnmortgageProperty -> JSONObject().put("kind", "UNMORTGAGE").put("index", action.propertyIndex)
        is GameAction.CreateTrade -> JSONObject().put("kind", "CREATE_TRADE").put("offer", tradeToJson(action.offer))
        GameAction.AcceptTrade -> JSONObject().put("kind", "ACCEPT_TRADE")
        GameAction.RejectTrade -> JSONObject().put("kind", "REJECT_TRADE")
    }

    private fun actionFromJson(json: JSONObject): GameAction? = when (json.optString("kind")) {
        "ROLL" -> GameAction.RollDice(json.optInt("first"), json.optInt("second"))
        "BUY" -> GameAction.BuyProperty
        "DECLINE" -> GameAction.DeclineProperty
        "BID" -> GameAction.PlaceBid
        "PASS_AUCTION" -> GameAction.PassAuction
        "PAY_JAIL" -> GameAction.PayJailFee
        "RESOLVE_CARD" -> GameAction.ResolveCard
        "USE_JAIL_CARD" -> GameAction.UseJailCard
        "BUY_HOUSE" -> GameAction.BuyHouse(json.optInt("index", -1))
        "BUY_HOTEL" -> GameAction.BuyHotel(json.optInt("index", -1))
        "SELL_BUILDING" -> GameAction.SellBuilding(json.optInt("index", -1))
        "MORTGAGE" -> GameAction.MortgageProperty(json.optInt("index", -1))
        "UNMORTGAGE" -> GameAction.UnmortgageProperty(json.optInt("index", -1))
        "CREATE_TRADE" -> json.optJSONObject("offer")?.let { GameAction.CreateTrade(tradeFromJson(it)) }
        "ACCEPT_TRADE" -> GameAction.AcceptTrade
        "REJECT_TRADE" -> GameAction.RejectTrade
        else -> null
    }

    private fun tradeToJson(offer: TradeOffer) = JSONObject()
        .put("from", offer.fromId).put("to", offer.toId)
        .put("offeredProperties", JSONArray(offer.offeredPropertyIndexes))
        .put("requestedProperties", JSONArray(offer.requestedPropertyIndexes))
        .put("offeredCash", offer.offeredCash).put("requestedCash", offer.requestedCash)

    private fun tradeFromJson(json: JSONObject): TradeOffer {
        fun ints(key: String) = buildList {
            val array = json.optJSONArray(key) ?: return@buildList
            for (i in 0 until array.length()) add(array.optInt(i))
        }
        return TradeOffer(json.optInt("from", -1), json.optInt("to", -1), ints("offeredProperties"), ints("requestedProperties"), json.optInt("offeredCash"), json.optInt("requestedCash"))
    }

    private fun stateToJson(state: GameState): JSONObject {
        val players = JSONArray().apply {
            state.players.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("money", it.money).put("position", it.position).put("inJail", it.inJail).put("jailAttempts", it.jailAttempts).put("bankrupt", it.bankrupt).put("jailCards", it.getOutOfJailCards)) }
        }
        val properties = JSONObject().apply {
            state.properties.forEach { (index, property) -> put(index.toString(), JSONObject().put("owner", property.ownerId ?: JSONObject.NULL).put("houses", property.houses).put("hotel", property.hotel).put("mortgaged", property.mortgaged)) }
        }
        return JSONObject()
            .put("players", players).put("properties", properties)
            .put("currentPlayer", state.currentPlayer).put("turn", state.turnNumber).put("phase", state.phase.name)
            .put("doubles", state.doublesCount).put("lastDice", state.lastDice?.let { JSONObject().put("first", it.first).put("second", it.second) } ?: JSONObject.NULL)
            .put("movementPath", JSONArray(state.movementPath)).put("lastMessage", state.lastMessage)
            .put("auction", state.auction?.let { auctionToJson(it) } ?: JSONObject.NULL)
            .put("pendingCard", state.pendingCard?.id ?: JSONObject.NULL)
            .put("trade", state.tradeOffer?.let { tradeToJson(it) } ?: JSONObject.NULL)
            .put("history", JSONArray().apply { state.history.forEach { put(JSONObject().put("text", it.text).put("playerId", it.playerId ?: JSONObject.NULL)) } })
            .put("gameOver", state.gameOver)
    }

    private fun stateFromJson(json: JSONObject): GameState? = runCatching {
        val players = buildList {
            val array = json.optJSONArray("players") ?: JSONArray()
            for (i in 0 until array.length()) {
                val p = array.getJSONObject(i)
                add(PlayerState(p.optInt("id"), p.optString("name"), p.optInt("money"), p.optInt("position"), p.optBoolean("inJail"), p.optInt("jailAttempts"), p.optBoolean("bankrupt"), p.optInt("jailCards")))
            }
        }
        if (players.isEmpty()) return@runCatching null
        val properties = mutableMapOf<Int, PropertyState>()
        val propertyJson = json.optJSONObject("properties") ?: JSONObject()
        propertyJson.keys().forEach { key ->
            val value = propertyJson.getJSONObject(key)
            properties[key.toInt()] = PropertyState(if (value.isNull("owner")) null else value.optInt("owner"), value.optInt("houses"), value.optBoolean("hotel"), value.optBoolean("mortgaged"))
        }
        val diceJson = json.optJSONObject("lastDice")
        val dice = diceJson?.let { DiceResult(it.optInt("first"), it.optInt("second")) }
        val path = mutableListOf<Int>().apply { val array = json.optJSONArray("movementPath") ?: JSONArray(); for (i in 0 until array.length()) add(array.optInt(i)) }
        val history = mutableListOf<HistoryEntry>().apply { val array = json.optJSONArray("history") ?: JSONArray(); for (i in 0 until array.length()) { val item = array.getJSONObject(i); add(HistoryEntry(item.optString("text"), if (item.isNull("playerId")) null else item.optInt("playerId"))) } }
        val cardId = json.optString("pendingCard", "")
        val pendingCard = (BoardDefinitions.chance + BoardDefinitions.community).firstOrNull { it.id == cardId }
        GameState(players, properties, json.optInt("currentPlayer"), json.optInt("turn", 1), TurnPhase.valueOf(json.optString("phase", TurnPhase.WAITING_FOR_ROLL.name)), json.optInt("doubles"), dice, path, json.optString("lastMessage"), json.optJSONObject("auction")?.let { auctionFromJson(it) }, pendingCard, json.optJSONObject("trade")?.let { tradeFromJson(it) }, history, json.optBoolean("gameOver"))
    }.getOrNull()

    private fun auctionToJson(auction: AuctionState) = JSONObject().put("property", auction.propertyIndex).put("highBid", auction.highBid).put("highest", auction.highestBidder ?: JSONObject.NULL).put("order", JSONArray(auction.order)).put("current", auction.currentIndex).put("passed", JSONArray(auction.passed.toList()))
    private fun auctionFromJson(json: JSONObject): AuctionState {
        fun ints(key: String) = buildList { val array = json.optJSONArray(key) ?: JSONArray(); for (i in 0 until array.length()) add(array.optInt(i)) }
        return AuctionState(json.optInt("property"), json.optInt("highBid"), if (json.isNull("highest")) null else json.optInt("highest"), ints("order"), json.optInt("current"), ints("passed").toSet())
    }
}

class LanHost(
    private val context: Context,
    private val hostName: String,
    private val onLobby: (List<LobbyPlayer>) -> Unit,
    private val onAssigned: (Int) -> Unit,
    private val onAction: (Int, GameAction) -> Unit,
    private val onError: (String) -> Unit
) {
    private val executor = Executors.newCachedThreadPool()
    private val clients = ConcurrentHashMap<Int, PrintWriter>()
    private val sockets = ConcurrentHashMap<Int, Socket>()
    private val players = Collections.synchronizedList(mutableListOf(LobbyPlayer(0, hostName)))
    private var server: ServerSocket? = null
    private var gameId = ""
    @Volatile private var running = false
    @Volatile private var started = false

    fun start(): JoinPayload? {
        val ip = LanNetwork.localIpv4(context) ?: run { onError("Połącz telefon z Wi‑Fi i spróbuj ponownie."); return null }
        return runCatching {
            gameId = Random.nextInt(100000, 999999).toString()
            server = ServerSocket(0)
            running = true
            executor.execute { acceptLoop() }
            JoinPayload(ip, server!!.localPort, gameId)
        }.onFailure { onError("Nie udało się uruchomić gry LAN: ${it.message}") }.getOrNull()
    }

    fun startGame(state: GameState) {
        started = true
        broadcast(LanNetwork.encodeState(state))
    }

    fun broadcastState(state: GameState) { if (started) broadcast(LanNetwork.encodeState(state)) }

    fun currentPlayers(): List<LobbyPlayer> = synchronized(players) { players.toList() }

    fun stop() {
        running = false
        sockets.values.forEach { runCatching { it.close() } }
        runCatching { server?.close() }
        executor.shutdownNow()
    }

    private fun acceptLoop() {
        while (running) {
            runCatching { server?.accept() }.onSuccess { socket -> socket?.let { accepted -> executor.execute { handleClient(accepted) } } }.onFailure { if (running) onError("Serwer gry został rozłączony.") }
        }
    }

    private fun handleClient(socket: Socket) {
        var id = -1
        try {
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            val hello = JSONObject(reader.readLine() ?: return)
            if (hello.optString("type") != "HELLO" || hello.optString("game") != gameId) { writer.println(LanNetwork.encodeError("Nieprawidłowy kod gry.")); return }
            synchronized(players) {
                if (started || players.size >= 5) { writer.println(LanNetwork.encodeError(if (started) "Gra już się rozpoczęła." else "Gra jest pełna.")); return }
                id = (1..4).firstOrNull { candidate -> players.none { it.id == candidate } } ?: return
                players += LobbyPlayer(id, hello.optString("name").ifBlank { "Gracz $id" }.take(18))
            }
            socket.soTimeout = 0
            clients[id] = writer
            sockets[id] = socket
            writer.println(LanNetwork.encodeAssigned(id))
            writer.println(LanNetwork.encodeLobby(currentPlayers()))
            onLobby(currentPlayers())
            while (running && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (json.optString("type") == "ACTION") LanNetwork.decodeAction(json)?.let { onAction(id, it) }
            }
        } catch (error: Exception) {
            if (running) onError("Połączenie gracza zostało przerwane.")
        } finally {
            if (id >= 0) {
                clients.remove(id)
                sockets.remove(id)
                synchronized(players) { players.removeAll { it.id == id } }
                onLobby(currentPlayers())
            }
            runCatching { socket.close() }
        }
    }

    private fun broadcast(json: JSONObject) { clients.values.forEach { writer -> runCatching { writer.println(json) } } }
}

class LanClient(
    private val payload: JoinPayload,
    private val playerName: String,
    private val onAssigned: (Int) -> Unit,
    private val onLobby: (List<LobbyPlayer>) -> Unit,
    private val onState: (GameState) -> Unit,
    private val onError: (String) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    @Volatile private var running = false

    fun connect() {
        executor.execute {
            try {
                val connected = Socket(payload.host, payload.port)
                socket = connected
                writer = PrintWriter(connected.getOutputStream(), true)
                running = true
                writer?.println(LanNetwork.encodeHello(playerName.take(18), payload.gameId))
                val reader = BufferedReader(InputStreamReader(connected.getInputStream()))
                while (running) {
                    val line = reader.readLine() ?: break
                    val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    when (json.optString("type")) {
                        "ASSIGNED" -> onAssigned(json.optInt("id", -1))
                        "LOBBY" -> onLobby(LanNetwork.decodeLobby(json))
                        "STATE" -> LanNetwork.decodeState(json)?.let(onState)
                        "ERROR" -> onError(json.optString("message"))
                    }
                }
            } catch (error: Exception) {
                if (running) onError("Nie udało się połączyć z hostem. Sprawdź Wi‑Fi i kod QR.")
            }
        }
    }

    fun send(action: GameAction) { executor.execute { writer?.println(LanNetwork.encodeAction(action)) } }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        executor.shutdownNow()
    }
}
