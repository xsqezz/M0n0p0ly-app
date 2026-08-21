package pl.m0n0p0ly.app

enum class TurnPhase { WAITING_FOR_ROLL, JAIL_DECISION, MOVING, PROPERTY_DECISION, AUCTION, CARD_RESOLUTION, WAITING_FOR_END_TURN, GAME_OVER }

sealed interface GameAction {
    data class RollDice(val first: Int, val second: Int) : GameAction
    data object BuyProperty : GameAction
    data object DeclineProperty : GameAction
    data object PlaceBid : GameAction
    data object PassAuction : GameAction
    data object PayJailFee : GameAction
    data object ResolveCard : GameAction
    data object UseJailCard : GameAction
    data class BuyHouse(val propertyIndex: Int) : GameAction
    data class BuyHotel(val propertyIndex: Int) : GameAction
    data class SellBuilding(val propertyIndex: Int) : GameAction
    data class MortgageProperty(val propertyIndex: Int) : GameAction
    data class UnmortgageProperty(val propertyIndex: Int) : GameAction
}

data class PlayerState(val id: Int, val name: String, val money: Int = 1500, val position: Int = 0, val inJail: Boolean = false, val jailAttempts: Int = 0, val bankrupt: Boolean = false, val getOutOfJailCards: Int = 0)
data class PropertyState(val ownerId: Int? = null, val houses: Int = 0, val hotel: Boolean = false, val mortgaged: Boolean = false)
data class DiceResult(val first: Int, val second: Int) { val total get() = first + second; val doubles get() = first == second }
data class AuctionState(val propertyIndex: Int, val highBid: Int = 0, val highestBidder: Int? = null, val order: List<Int>, val currentIndex: Int = 0, val passed: Set<Int> = emptySet())
data class HistoryEntry(val text: String, val playerId: Int? = null)
data class GameState(val players: List<PlayerState>, val properties: Map<Int, PropertyState> = emptyMap(), val currentPlayer: Int = 0, val turnNumber: Int = 1, val phase: TurnPhase = TurnPhase.WAITING_FOR_ROLL, val doublesCount: Int = 0, val lastDice: DiceResult? = null, val movementPath: List<Int> = emptyList(), val lastMessage: String = "Wybierz graczy i rozpocznij grę", val auction: AuctionState? = null, val pendingCard: GameCard? = null, val history: List<HistoryEntry> = emptyList(), val gameOver: Boolean = false)

object GameEngine {
    fun newGame(names: List<String>): GameState { val players = names.take(5).mapIndexed { i, name -> PlayerState(i, name.ifBlank { "Gracz ${i + 1}" }) }; return GameState(players, lastMessage = "TURA 1 • ${players.first().name}", history = listOf(HistoryEntry("Gra rozpoczęta"))) }
    fun reduce(state: GameState, action: GameAction): GameState = when (action) { is GameAction.RollDice -> roll(state, action.first, action.second); GameAction.BuyProperty -> buy(state); GameAction.DeclineProperty -> declinePurchase(state); GameAction.PlaceBid -> bid(state); GameAction.PassAuction -> passAuction(state); GameAction.PayJailFee -> leaveJail(state); GameAction.ResolveCard -> resolveCard(state); GameAction.UseJailCard -> useJailCard(state); is GameAction.BuyHouse -> buyHouse(state, action.propertyIndex); is GameAction.BuyHotel -> buyHotel(state, action.propertyIndex); is GameAction.SellBuilding -> sellBuilding(state, action.propertyIndex); is GameAction.MortgageProperty -> mortgageProperty(state, action.propertyIndex); is GameAction.UnmortgageProperty -> unmortgageProperty(state, action.propertyIndex) }

    fun roll(state: GameState, die1: Int, die2: Int): GameState {
        if (die1 !in 1..6 || die2 !in 1..6) return reject(state, "Nieprawidłowy wynik kości")
        val player = state.players[state.currentPlayer]
        if (state.phase == TurnPhase.JAIL_DECISION) return jailRoll(state, player, die1, die2)
        if (state.phase != TurnPhase.WAITING_FOR_ROLL) return reject(state, "Najpierw zakończ bieżącą akcję")
        if (player.inJail) return state.copy(phase = TurnPhase.JAIL_DECISION, lastMessage = "${player.name} jest w więzieniu")
        val dice = DiceResult(die1, die2); val doubles = if (dice.doubles) state.doublesCount + 1 else 0
        if (doubles >= 3) return sendToJail(state.copy(lastDice = dice, doublesCount = doubles), player, "Trzy dublety z rzędu — więzienie")
        var destination = player.position; val path = buildList { repeat(dice.total) { destination = (destination + 1) % 40; add(destination) } }; val crossedStart = path.any { it == 0 }
        val moved = player.copy(position = destination, money = player.money + if (crossedStart) 200 else 0)
        val moving = state.copy(players = state.players.replace(moved), lastDice = dice, movementPath = path, doublesCount = doubles, phase = TurnPhase.MOVING, lastMessage = "${player.name} rzucił(a) ${die1} + ${die2}", history = state.history.add("${player.name} rzucił(a) $die1 + $die2", player.id))
        return resolveLanding(moving)
    }

    fun buy(state: GameState): GameState {
        if (state.phase != TurnPhase.PROPERTY_DECISION) return reject(state, "Nie można teraz kupować")
        val p = state.players[state.currentPlayer]; val definition = BoardDefinitions.byIndex[p.position] ?: return reject(state, "To pole nie jest nieruchomością")
        if (state.properties[p.position]?.ownerId != null) return reject(state, "Ta nieruchomość ma już właściciela")
        if (p.money < definition.price) return reject(state, "Brakuje pieniędzy na zakup")
        return state.copy(players = state.players.replace(p.copy(money = p.money - definition.price)), properties = state.properties + (p.position to PropertyState(p.id)), phase = TurnPhase.WAITING_FOR_ROLL, lastMessage = "${p.name} kupił(a) ${definition.name} za M${definition.price}", history = state.history.add("${p.name} kupił(a) ${definition.name}", p.id)).let { if (it.doublesCount > 0) it else endTurn(it, it.lastMessage) }
    }

    fun declinePurchase(state: GameState): GameState {
        if (state.phase != TurnPhase.PROPERTY_DECISION) return reject(state, "Nie ma teraz nieruchomości do licytacji")
        val p = state.players[state.currentPlayer]; val order = state.players.filter { !it.bankrupt && it.money >= 10 }.sortedBy { (it.id - p.id + state.players.size) % state.players.size }.map { it.id }
        if (order.isEmpty()) return endTurn(state, "Nikt nie może wziąć udziału w licytacji")
        return state.copy(phase = TurnPhase.AUCTION, auction = AuctionState(p.position, order = order), currentPlayer = order.first(), lastMessage = "Licytacja od M10: ${state.players[order.first()].name}", history = state.history.add("Rozpoczęto licytację: ${GameData.fields[p.position].name}"))
    }

    fun bid(state: GameState): GameState { val auction = state.auction ?: return reject(state, "Brak aktywnej licytacji"); val bidder = auction.order[auction.currentIndex]; val amount = if (auction.highBid == 0) 10 else auction.highBid + 10; if (state.players[bidder].money < amount) return passAuction(state, "${state.players[bidder].name} nie ma środków i pasuje"); val updated = state.copy(auction = auction.copy(highBid = amount, highestBidder = bidder), lastMessage = "${state.players[bidder].name} podbija do M$amount", history = state.history.add("${state.players[bidder].name} licytuje M$amount", bidder)); return moveAuctionToNext(updated) }
    fun passAuction(state: GameState, message: String? = null): GameState { val auction = state.auction ?: return reject(state, "Brak aktywnej licytacji"); val bidder = auction.order[auction.currentIndex]; return moveAuctionToNext(state.copy(auction = auction.copy(passed = auction.passed + bidder), lastMessage = message ?: "${state.players[bidder].name} pasuje")) }

    fun leaveJail(state: GameState): GameState { if (state.phase != TurnPhase.JAIL_DECISION) return reject(state, "Nie jesteś teraz w fazie więzienia"); val p = state.players[state.currentPlayer]; if (!p.inJail || p.money < 50) return reject(state, "Nie można zapłacić za wyjście z więzienia"); return state.copy(players = state.players.replace(p.copy(money = p.money - 50, inJail = false, jailAttempts = 0)), phase = TurnPhase.WAITING_FOR_ROLL, lastMessage = "${p.name} zapłacił(a) M50 — rzuć kośćmi", history = state.history.add("${p.name} zapłacił(a) M50 za wyjście z więzienia", p.id)) }
    fun useJailCard(state: GameState): GameState { if (state.phase != TurnPhase.JAIL_DECISION) return reject(state, "Nie jesteś teraz w fazie więzienia"); val p = state.players[state.currentPlayer]; if (!p.inJail || p.getOutOfJailCards == 0) return reject(state, "Brak karty wyjścia z więzienia"); return state.copy(players = state.players.replace(p.copy(inJail = false, jailAttempts = 0, getOutOfJailCards = p.getOutOfJailCards - 1)), phase = TurnPhase.WAITING_FOR_ROLL, lastMessage = "${p.name} użył(a) karty wyjścia z więzienia", history = state.history.add("${p.name} użył(a) karty wyjścia z więzienia", p.id)) }

    fun buyHouse(state: GameState, propertyIndex: Int): GameState {
        val p = state.players[state.currentPlayer]
        val property = BoardDefinitions.byIndex[propertyIndex] ?: return reject(state, "To pole nie jest nieruchomością")
        val current = state.properties[propertyIndex] ?: return reject(state, "Najpierw kup tę nieruchomość")
        if (!canManage(state) || property.kind != Kind.STREET || current.ownerId != p.id) return reject(state, "Nie można teraz kupić domu")
        if (!groupComplete(state, property, p.id) || current.mortgaged || current.hotel || current.houses >= 4) return reject(state, "Brak kompletu albo nieruchomość nie może być zabudowana")
        val groupStates = BoardDefinitions.properties.filter { it.group == property.group }.map { state.properties[it.index]?.houses ?: 0 }
        if (current.houses > groupStates.minOrNull()!!) return reject(state, "Buduj równomiernie w całej grupie")
        if (p.money < property.houseCost) return reject(state, "Brakuje pieniędzy na dom")
        val updated = state.copy(players = state.players.replace(p.copy(money = p.money - property.houseCost)), properties = state.properties + (propertyIndex to current.copy(houses = current.houses + 1)), lastMessage = "${p.name} kupił(a) dom na ${property.name}", history = state.history.add("${p.name} kupił(a) dom na ${property.name}", p.id))
        return updated
    }

    fun buyHotel(state: GameState, propertyIndex: Int): GameState {
        val p = state.players[state.currentPlayer]
        val property = BoardDefinitions.byIndex[propertyIndex] ?: return reject(state, "To pole nie jest nieruchomością")
        val current = state.properties[propertyIndex] ?: return reject(state, "Najpierw kup tę nieruchomość")
        if (!canManage(state) || property.kind != Kind.STREET || current.ownerId != p.id || !groupComplete(state, property, p.id)) return reject(state, "Nie można teraz kupić hotelu")
        if (current.mortgaged || current.hotel || current.houses != 4) return reject(state, "Hotel wymaga czterech domów")
        if (p.money < property.hotelCost) return reject(state, "Brakuje pieniędzy na hotel")
        return state.copy(players = state.players.replace(p.copy(money = p.money - property.hotelCost)), properties = state.properties + (propertyIndex to current.copy(hotel = true, houses = 0)), lastMessage = "${p.name} kupił(a) hotel na ${property.name}", history = state.history.add("${p.name} kupił(a) hotel na ${property.name}", p.id))
    }

    fun sellBuilding(state: GameState, propertyIndex: Int): GameState {
        val p = state.players[state.currentPlayer]
        val property = BoardDefinitions.byIndex[propertyIndex] ?: return reject(state, "To pole nie jest nieruchomością")
        val current = state.properties[propertyIndex] ?: return reject(state, "Brak własności")
        if (!canManage(state) || property.kind != Kind.STREET || current.ownerId != p.id) return reject(state, "Nie można teraz sprzedawać budynku")
        val groupMax = BoardDefinitions.properties.filter { it.group == property.group }.maxOf { state.properties[it.index]?.houses ?: 0 }
        if (current.hotel) {
            val refund = property.hotelCost / 2
            return state.copy(players = state.players.replace(p.copy(money = p.money + refund)), properties = state.properties + (propertyIndex to current.copy(hotel = false, houses = 4)), lastMessage = "${p.name} sprzedał(a) hotel na ${property.name}", history = state.history.add("${p.name} sprzedał(a) hotel na ${property.name}", p.id))
        }
        if (current.houses == 0 || current.houses < groupMax) return reject(state, "Sprzedawaj budynki równomiernie")
        val refund = property.houseCost / 2
        return state.copy(players = state.players.replace(p.copy(money = p.money + refund)), properties = state.properties + (propertyIndex to current.copy(houses = current.houses - 1)), lastMessage = "${p.name} sprzedał(a) dom na ${property.name}", history = state.history.add("${p.name} sprzedał(a) dom na ${property.name}", p.id))
    }

    fun mortgageProperty(state: GameState, propertyIndex: Int): GameState {
        val p = state.players[state.currentPlayer]
        val property = BoardDefinitions.byIndex[propertyIndex] ?: return reject(state, "To pole nie jest nieruchomością")
        val current = state.properties[propertyIndex] ?: return reject(state, "Brak własności")
        if (!canManage(state) || current.ownerId != p.id || current.mortgaged) return reject(state, "Nie można zastawić tej nieruchomości")
        if (current.houses > 0 || current.hotel) return reject(state, "Najpierw sprzedaj wszystkie budynki")
        val value = GameData.mortgage(property.price)
        return state.copy(players = state.players.replace(p.copy(money = p.money + value)), properties = state.properties + (propertyIndex to current.copy(mortgaged = true)), lastMessage = "${p.name} zastawił(a) ${property.name} za M$value", history = state.history.add("${p.name} zastawił(a) ${property.name}", p.id))
    }

    fun unmortgageProperty(state: GameState, propertyIndex: Int): GameState {
        val p = state.players[state.currentPlayer]
        val property = BoardDefinitions.byIndex[propertyIndex] ?: return reject(state, "To pole nie jest nieruchomością")
        val current = state.properties[propertyIndex] ?: return reject(state, "Brak własności")
        if (!canManage(state) || current.ownerId != p.id || !current.mortgaged) return reject(state, "Ta nieruchomość nie jest pod hipoteką")
        val cost = GameData.redemption(property.price)
        if (p.money < cost) return reject(state, "Brakuje pieniędzy na spłatę hipoteki")
        return state.copy(players = state.players.replace(p.copy(money = p.money - cost)), properties = state.properties + (propertyIndex to current.copy(mortgaged = false)), lastMessage = "${p.name} spłacił(a) hipotekę ${property.name} za M$cost", history = state.history.add("${p.name} spłacił(a) hipotekę ${property.name}", p.id))
    }

    private fun canManage(state: GameState) = state.phase == TurnPhase.WAITING_FOR_ROLL || state.phase == TurnPhase.WAITING_FOR_END_TURN
    private fun groupComplete(state: GameState, property: PropertyDefinition, ownerId: Int) = property.group != null && BoardDefinitions.properties.filter { it.group == property.group }.all { state.properties[it.index]?.ownerId == ownerId }
    private fun jailRoll(state: GameState, p: PlayerState, die1: Int, die2: Int): GameState { val dice = DiceResult(die1, die2); if (dice.doubles) return roll(state.copy(players = state.players.replace(p.copy(inJail = false, jailAttempts = 0)), phase = TurnPhase.WAITING_FOR_ROLL), die1, die2); val attempts = p.jailAttempts + 1; if (attempts >= 3) { val released = p.copy(inJail = false, jailAttempts = 0, money = p.money - 50); return roll(state.copy(players = state.players.replace(released), phase = TurnPhase.WAITING_FOR_ROLL, lastMessage = "Trzecia próba nieudana — zapłać M50"), die1, die2) }; return endTurn(state.copy(players = state.players.replace(p.copy(jailAttempts = attempts)), lastDice = dice), "${p.name} nie wyrzucił(a) dubletu (${attempts}/3)") }

    private fun resolveLanding(state: GameState): GameState { val p = state.players[state.currentPlayer]; val field = GameData.fields[p.position]; if (p.position == 30) return sendToJail(state, p, "${p.name} trafia do więzienia"); if (p.position == 4) return settle(state, p, -200, "Podatek Dochodowy"); if (p.position == 38) return settle(state, p, -100, "Domiar Podatkowy"); if (field.name == "Szansa") return drawCard(state, CardDeck.CHANCE); if (field.name == "Kasa Społeczna") return drawCard(state, CardDeck.COMMUNITY); val property = BoardDefinitions.byIndex[p.position]; if (property != null) { val existing = state.properties[p.position]; if (existing?.ownerId == null) return state.copy(phase = TurnPhase.PROPERTY_DECISION, lastMessage = "${property.name} jest wolna — kup albo uruchom licytację"); if (existing.ownerId != p.id && !existing.mortgaged) return settleRent(state, p, property, existing) }; return finishMovement(state, "${p.name} staje na polu ${field.name}") }

    private fun settleRent(state: GameState, payer: PlayerState, property: PropertyDefinition, propertyState: PropertyState): GameState { val owner = state.players[propertyState.ownerId!!]; val count = state.properties.count { it.value.ownerId == owner.id && BoardDefinitions.byIndex[it.key]?.kind == property.kind }; val rent = when (property.kind) { Kind.STATION -> listOf(25,50,100,200)[(count - 1).coerceIn(0,3)]; Kind.UTILITY -> if (count > 1) (state.lastDice?.total ?: 0) * 10 else (state.lastDice?.total ?: 0) * 4; Kind.STREET -> property.rent!!.let { if (propertyState.hotel) it.hotel else if (propertyState.houses > 0) it.houses[propertyState.houses - 1] else if (count == BoardDefinitions.properties.count { d -> d.group == property.group && state.properties[d.index]?.ownerId == owner.id }) it.monopoly else it.base }; else -> 0 }; val paid = payer.copy(money = payer.money - rent); val received = owner.copy(money = owner.money + rent); return finishMovement(state.copy(players = state.players.replace(paid).replace(received), lastMessage = "${payer.name} płaci M$rent czynszu dla ${owner.name}", history = state.history.add("${payer.name} zapłacił(a) M$rent czynszu ${owner.name}", payer.id)), state.lastMessage) }
    private fun settle(state: GameState, p: PlayerState, amount: Int, reason: String): GameState = finishMovement(state.copy(players = state.players.replace(p.copy(money = p.money + amount)), lastMessage = "$reason: ${if (amount < 0) "-" else "+"}M${kotlin.math.abs(amount)}", history = state.history.add("${p.name} zapłacił(a) ${kotlin.math.abs(amount)} za $reason", p.id)), state.lastMessage)
    private fun drawCard(state: GameState, deck: CardDeck): GameState { val cards = if (deck == CardDeck.CHANCE) BoardDefinitions.chance else BoardDefinitions.community; val card = cards[(state.history.count { it.text.contains("kartę") } + state.currentPlayer) % cards.size]; return state.copy(phase = TurnPhase.CARD_RESOLUTION, pendingCard = card, lastMessage = "${state.players[state.currentPlayer].name} dobiera kartę", history = state.history.add("${state.players[state.currentPlayer].name} dobiera kartę", state.currentPlayer)) }
    fun resolveCard(state: GameState): GameState {
        val card = state.pendingCard ?: return reject(state, "Brak karty do wykonania")
        val p = state.players[state.currentPlayer]
        val cleared = state.copy(pendingCard = null)
        return when (card.id) {
            "c1" -> moveByCard(cleared, nearestAhead(p.position, listOf(12, 28)), card.title)
            "c2" -> moveByCard(cleared, 5, card.title)
            "c4", "s14" -> sendToJail(cleared, p, "${p.name} idzie do więzienia z karty")
            "c5" -> moveByCard(cleared, 24, card.title)
            "c9", "c16" -> moveByCard(cleared, nearestAhead(p.position, listOf(5, 15, 25, 35)), card.title)
            "c10" -> moveByCard(cleared, 39, card.title)
            "c11" -> moveByCard(cleared, 11, card.title)
            "c12", "s13" -> moveByCard(cleared, 0, card.title)
            "c15" -> moveByCard(cleared, (p.position + 40 - 3) % 40, card.title, backwards = true)
            "c13", "s15" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(getOutOfJailCards = p.getOutOfJailCards + 1))), card.title)
            "c6" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money - 15))), "${card.title}: -M15")
            "c7" -> {
                val owned = cleared.properties.filterValues { it.ownerId == p.id }
                val houses = owned.values.sumOf { it.houses }
                val hotels = owned.values.count { it.hotel }
                finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money - houses * 25 - hotels * 100))), "${card.title}: -M${houses * 25 + hotels * 100}")
            }
            "c8" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money + 150))), "${card.title}: +M150")
            "c14" -> {
                val otherPlayers = cleared.players.filter { it.id != p.id && !it.bankrupt }
                val updated = cleared.players.map { player ->
                    when {
                        player.id == p.id -> player.copy(money = player.money - otherPlayers.size * 50)
                        player in otherPlayers -> player.copy(money = player.money + 50)
                        else -> player
                    }
                }
                finishCard(cleared.copy(players = updated), "${card.title}: zapłać każdemu M50")
            }
            "s1" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money + 200))), "${card.title}: +M200")
            "s2" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money + 20))), "${card.title}: +M20")
            "s3" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money + 50))), "${card.title}: +M50")
            "s4", "s5", "s6" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money + 100))), "${card.title}: +M100")
            "s7" -> {
                val otherPlayers = cleared.players.filter { it.id != p.id && !it.bankrupt }
                val updated = cleared.players.map { player ->
                    when {
                        player.id == p.id -> player.copy(money = player.money + otherPlayers.size * 10)
                        player in otherPlayers -> player.copy(money = player.money - 10)
                        else -> player
                    }
                }
                finishCard(cleared.copy(players = updated), "${card.title}: +M${otherPlayers.size * 10}")
            }
            "s8" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money + 10))), "${card.title}: +M10")
            "s9" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money + 25))), "${card.title}: +M25")
            "s10", "s11" -> finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money - 50))), "${card.title}: -M50")
            "s12" -> {
                val owned = cleared.properties.filterValues { it.ownerId == p.id }
                val houses = owned.values.sumOf { it.houses }
                val hotels = owned.values.count { it.hotel }
                finishCard(cleared.copy(players = cleared.players.replace(p.copy(money = p.money - houses * 40 - hotels * 115))), "${card.title}: -M${houses * 40 + hotels * 115}")
            }
            else -> finishCard(cleared, card.title)
        }
    }

    private fun finishCard(state: GameState, message: String): GameState {
        val p = state.players[state.currentPlayer]
        return finishMovement(state.copy(lastMessage = message, history = state.history.add("${p.name} wykonał(a) kartę: $message", p.id)), message)
    }

    private fun moveByCard(state: GameState, destination: Int, message: String, backwards: Boolean = false): GameState {
        val p = state.players[state.currentPlayer]
        val crossedStart = !backwards && destination <= p.position && destination != 0 || (!backwards && destination == 0 && p.position != 0)
        val moved = p.copy(position = destination, money = p.money + if (crossedStart) 200 else 0)
        val next = state.copy(players = state.players.replace(moved), movementPath = listOf(destination), lastMessage = "$message — ${GameData.fields[destination].name}", history = state.history.add("${p.name}: $message", p.id))
        return resolveLanding(next)
    }

    private fun nearestAhead(position: Int, targets: List<Int>): Int = targets.minBy { (it - position + 40) % 40 }.let { if (it == position) targets.first() else it }

    private fun sendToJail(state: GameState, p: PlayerState, message: String) = endTurn(state.copy(players = state.players.replace(p.copy(position = 10, inJail = true, jailAttempts = 0)), phase = TurnPhase.WAITING_FOR_ROLL), message)
    private fun finishMovement(state: GameState, message: String): GameState = if (state.doublesCount > 0) state.copy(phase = TurnPhase.WAITING_FOR_ROLL, lastMessage = "$message • dublet, rzuć ponownie") else endTurn(state, message)
    private fun endTurn(state: GameState, message: String): GameState { val next = (state.currentPlayer + 1) % state.players.size; val nextP = state.players[next]; return state.copy(currentPlayer = next, turnNumber = if (next == 0) state.turnNumber + 1 else state.turnNumber, phase = if (nextP.inJail) TurnPhase.JAIL_DECISION else TurnPhase.WAITING_FOR_ROLL, doublesCount = 0, movementPath = emptyList(), auction = null, lastMessage = "TURA ${state.turnNumber} • ${nextP.name}: $message") }
    private fun moveAuctionToNext(state: GameState): GameState { val a = state.auction!!; val active = a.order.filter { it !in a.passed && it != a.highestBidder }; if (active.isEmpty()) { val winner = a.highestBidder ?: return endTurn(state.copy(auction = null, phase = TurnPhase.WAITING_FOR_ROLL), "Nikt nie wygrał licytacji"); val player = state.players[winner]; return endTurn(state.copy(players = state.players.replace(player.copy(money = player.money - a.highBid)), properties = state.properties + (a.propertyIndex to PropertyState(winner)), auction = null, phase = TurnPhase.WAITING_FOR_ROLL), "${player.name} wygrał(a) licytację za M${a.highBid}") }; val next = active.first(); return state.copy(currentPlayer = next, auction = a.copy(currentIndex = a.order.indexOf(next))) }
    private fun reject(state: GameState, message: String) = state.copy(lastMessage = "BŁĄD: $message")
    private fun finishIfNoMoney(state: GameState) = state
    private fun List<PlayerState>.replace(player: PlayerState) = map { if (it.id == player.id) player else it }
    private fun List<HistoryEntry>.add(text: String, playerId: Int? = null) = (this + HistoryEntry(text, playerId)).takeLast(60)
}
