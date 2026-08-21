package pl.m0n0p0ly.app

data class PlayerState(val id: Int, val name: String, val money: Int = 1500, val position: Int = 0, val inJail: Boolean = false, val jailTurns: Int = 0, val bankrupt: Boolean = false)
data class PropertyState(val ownerId: Int? = null, val houses: Int = 0, val hotel: Boolean = false, val mortgaged: Boolean = false)
data class AuctionState(val propertyIndex: Int, val highBid: Int = 10, val highestBidder: Int? = null, val order: List<Int>, val currentIndex: Int = 0, val passed: Set<Int> = emptySet())
data class GameState(val players: List<PlayerState>, val properties: Map<Int, PropertyState> = emptyMap(), val currentPlayer: Int = 0, val lastRoll: Int? = null, val lastMessage: String = "Wybierz graczy i rozpocznij grę", val awaitingPurchase: Boolean = false, val auction: AuctionState? = null, val gameOver: Boolean = false)

object GameEngine {
    fun newGame(names: List<String>): GameState = GameState(names.take(5).mapIndexed { i, n -> PlayerState(i, n.ifBlank { "Gracz ${i + 1}" }) }, lastMessage = "Tura ${names.firstOrNull() ?: "Gracz 1"}")

    fun roll(state: GameState, die1: Int, die2: Int): GameState {
        if (state.gameOver || state.players.isEmpty()) return state
        val p = state.players[state.currentPlayer]
        if (p.bankrupt) return nextTurn(state, "Gracz zbankrutował")
        if (p.inJail) return state.copy(lastMessage = "${p.name} jest w więzieniu. Zapłać M50, użyj karty albo spróbuj wyrzucić dublet.")
        val total = die1 + die2
        var moved = p.position + total
        var updated = state.copy(lastRoll = total)
        var player = p
        if (moved >= 40) { moved %= 40; player = player.copy(money = player.money + 200) }
        player = player.copy(position = moved)
        updated = updated.copy(players = updated.players.updated(player), awaitingPurchase = false)
        return land(updated, player, die1, die2)
    }

    fun buy(state: GameState): GameState {
        if (!state.awaitingPurchase) return state.copy(lastMessage = "Nie ma teraz nieruchomości do kupienia")
        val p = state.players[state.currentPlayer]; val field = GameData.fields[p.position]; val price = field.price ?: return state
        val existing = state.properties[p.position]
        if (field.kind == Kind.SPECIAL || existing?.ownerId != null || p.money < price) return state.copy(lastMessage = "Ta nieruchomość nie może być teraz kupiona")
        val next = state.copy(players = state.players.updated(p.copy(money = p.money - price)), properties = state.properties + (p.position to PropertyState(p.id)), awaitingPurchase = false, lastMessage = "${p.name} kupił(a) ${field.name} za M$price")
        return nextTurn(next)
    }

    fun declinePurchase(state: GameState): GameState {
        if (!state.awaitingPurchase) return state.copy(lastMessage = "Nie ma teraz nieruchomości do licytacji")
        val start = state.currentPlayer
        val order = state.players.filter { !it.bankrupt && it.money >= 10 }.sortedBy { (it.id - start + state.players.size) % state.players.size }.map { it.id }
        if (order.isEmpty()) return nextTurn(state.copy(awaitingPurchase = false, lastMessage = "Nikt nie może wziąć udziału w licytacji"))
        return state.copy(awaitingPurchase = false, auction = AuctionState(state.players[state.currentPlayer].position, order = order), currentPlayer = order.first(), lastMessage = "Licytacja od M10: ${state.players[order.first()].name} zaczyna")
    }

    fun bid(state: GameState): GameState {
        val auction = state.auction ?: return state
        val bidder = auction.order[auction.currentIndex]; val amount = if (auction.highestBidder == null) 10 else auction.highBid + 10
        if (state.players[bidder].money < amount) return passAuction(state.copy(lastMessage = "${state.players[bidder].name} nie ma wystarczającej kwoty i pasuje"))
        return advanceAuction(state.copy(auction = auction.copy(highBid = amount, highestBidder = bidder), currentPlayer = bidder, lastMessage = "${state.players[bidder].name} podbija do M$amount"), bidder)
    }

    fun passAuction(state: GameState): GameState {
        val auction = state.auction ?: return state
        val bidder = auction.order[auction.currentIndex]
        return advanceAuction(state.copy(auction = auction.copy(passed = auction.passed + bidder), lastMessage = "${state.players[bidder].name} pasuje"), bidder)
    }

    private fun advanceAuction(state: GameState, actedId: Int): GameState {
        val auction = state.auction ?: return state
        val passed = auction.passed
        val active = auction.order.filter { it !in passed && it != auction.highestBidder }
        if (active.isEmpty()) {
            val winner = auction.highestBidder
            if (winner == null) return nextTurn(state.copy(auction = null, lastMessage = "Nikt nie kupił ${GameData.fields[auction.propertyIndex].name}"))
            val price = auction.highBid; val winnerPlayer = state.players[winner]
            return nextTurn(state.copy(players = state.players.updated(winnerPlayer.copy(money = winnerPlayer.money - price)), properties = state.properties + (auction.propertyIndex to PropertyState(winner)), auction = null, lastMessage = "${winnerPlayer.name} wygrywa licytację za M$price"))
        }
        val next = active.first()
        return state.copy(auction = auction.copy(currentIndex = auction.order.indexOf(next)), currentPlayer = next)
    }

    fun leaveJail(state: GameState): GameState {
        val p = state.players[state.currentPlayer]
        if (!p.inJail || p.money < 50) return state.copy(lastMessage = "Nie można teraz opuścić więzienia")
        return nextTurn(state.copy(players = state.players.updated(p.copy(money = p.money - 50, inJail = false, jailTurns = 0)), lastMessage = "${p.name} zapłacił(a) M50 i wyszedł/wyszła z więzienia"))
    }

    private fun land(state: GameState, p: PlayerState, d1: Int, d2: Int): GameState {
        val field = GameData.fields[p.position]
        if (p.position == 30) return nextTurn(state.copy(players = state.players.updated(p.copy(position = 10, inJail = true, jailTurns = 0)), lastMessage = "${p.name} trafia do więzienia"))
        if (field.price != null && field.kind != Kind.SPECIAL) {
            val prop = state.properties[p.position]
            if (prop?.ownerId == null && p.money >= field.price) return state.copy(awaitingPurchase = true, lastMessage = "${field.name} jest wolna. Możesz ją kupić za M${field.price}.")
            if (prop?.ownerId != null && prop.ownerId != p.id && prop.mortgaged.not()) {
                val rent = rentFor(field, state.properties.count { it.value.ownerId == prop.ownerId && GameData.fields[it.key].kind == field.kind })
                val payer = p.copy(money = p.money - rent); val owner = state.players[prop.ownerId].copy(money = state.players[prop.ownerId].money + rent)
                return nextTurn(state.copy(players = state.players.updated(payer).updated(owner), lastMessage = "${p.name} płaci M$rent czynszu graczowi ${owner.name}"))
            }
        }
        if (p.position == 4) return nextTurn(state.copy(players = state.players.updated(p.copy(money = p.money - 200)), lastMessage = "Podatek Dochodowy: -M200"))
        if (p.position == 38) return nextTurn(state.copy(players = state.players.updated(p.copy(money = p.money - 100)), lastMessage = "Domiar Podatkowy: -M100"))
        return nextTurn(state.copy(lastMessage = "${p.name} staje na polu: ${field.name}"))
    }

    private fun rentFor(field: Field, ownedCount: Int): Int = when (field.kind) { Kind.STATION -> listOf(25,50,100,200)[(ownedCount - 1).coerceIn(0,3)]; Kind.UTILITY -> if (ownedCount > 1) 10 else 4; else -> 2 }
    private fun nextTurn(state: GameState, message: String = state.lastMessage): GameState { val next = (state.currentPlayer + 1) % state.players.size; return state.copy(currentPlayer = next, lastMessage = message, lastRoll = state.lastRoll, awaitingPurchase = false) }
    private fun List<PlayerState>.updated(player: PlayerState) = map { if (it.id == player.id) player else it }
}
