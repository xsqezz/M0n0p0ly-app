package pl.m0n0p0ly.app

import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    @Test fun cannotBuySamePropertyTwice() {
        val first = GameEngine.roll(GameState(listOf(PlayerState(0, "A", position = 39), PlayerState(1, "B"))), 1, 1)
        val bought = GameEngine.buy(first)
        assertEquals(0, bought.properties[1]?.ownerId)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, bought.phase)
        assertEquals("A", bought.players[0].name)
    }

    @Test fun decliningPropertyStartsAuctionFromTen() {
        val state = GameState(listOf(PlayerState(0, "A", position = 39), PlayerState(1, "B")))
        val decision = GameEngine.roll(state, 1, 1)
        val auction = GameEngine.declinePurchase(decision)
        assertEquals(TurnPhase.AUCTION, auction.phase)
        assertEquals(0, auction.auction?.highBid)
        assertEquals(2, auction.auction?.order?.size)
    }

    @Test fun jailBlocksRolling() {
        val state = GameState(listOf(PlayerState(0, "A", position = 28), PlayerState(1, "B")), currentPlayer = 0)
        val jailed = GameEngine.roll(state, 1, 1)
        assertTrue(jailed.players[0].inJail)
        val unchanged = GameEngine.roll(jailed, 6, 6)
        assertEquals(jailed.players[0].position, unchanged.players[0].position)
        assertTrue(unchanged.players[0].inJail)
    }

    @Test fun passingStartAddsTwoHundred() {
        val state = GameState(listOf(PlayerState(0, "A", position = 39), PlayerState(1, "B")), currentPlayer = 0)
        val moved = GameEngine.roll(state, 1, 1)
        assertEquals(1700, moved.players[0].money)
        assertEquals(1, moved.players[0].position)
    }
    @Test fun jailDoubleAttemptIsAllowedAndReleasesPlayer() {
        val state = GameState(listOf(PlayerState(0, "A", position = 10, inJail = true), PlayerState(1, "B")), currentPlayer = 0, phase = TurnPhase.JAIL_DECISION)
        val moved = GameEngine.roll(state, 3, 3)
        assertFalse(moved.players[0].inJail)
        assertEquals(16, moved.players[0].position)
    }

    @Test fun chanceFineUsesCardValueAndDoesNotUsePlaceholderAmount() {
        val state = GameState(listOf(PlayerState(0, "A"), PlayerState(1, "B")), currentPlayer = 0, phase = TurnPhase.CARD_RESOLUTION, pendingCard = BoardDefinitions.chance.first { it.id == "c6" })
        val resolved = GameEngine.resolveCard(state)
        assertEquals(1485, resolved.players[0].money)
    }

    @Test fun chanceMoveToStartPaysStartBonusOnce() {
        val state = GameState(listOf(PlayerState(0, "A", position = 20), PlayerState(1, "B")), currentPlayer = 0, phase = TurnPhase.CARD_RESOLUTION, pendingCard = BoardDefinitions.chance.first { it.id == "c12" })
        val resolved = GameEngine.resolveCard(state)
        assertEquals(1700, resolved.players[0].money)
        assertEquals(0, resolved.players[0].position)
    }

    @Test fun buildingRequiresCompleteGroupAndEvenBuild() {
        val properties = mapOf(1 to PropertyState(ownerId = 0), 3 to PropertyState(ownerId = 0))
        val state = GameState(listOf(PlayerState(0, "A"), PlayerState(1, "B")), properties = properties)
        val built = GameEngine.reduce(state, GameAction.BuyHouse(1))
        assertEquals(1, built.properties[1]?.houses)
        val uneven = GameEngine.reduce(built, GameAction.BuyHouse(1))
        assertTrue(uneven.lastMessage.startsWith("BŁĄD"))
    }

    @Test fun mortgageAndRedemptionUseWholeUnitValues() {
        val state = GameState(listOf(PlayerState(0, "A")), properties = mapOf(11 to PropertyState(ownerId = 0)))
        val mortgaged = GameEngine.reduce(state, GameAction.MortgageProperty(11))
        assertEquals(1570, mortgaged.players[0].money)
        assertTrue(mortgaged.properties[11]!!.mortgaged)
        val redeemed = GameEngine.reduce(mortgaged, GameAction.UnmortgageProperty(11))
        assertEquals(1493, redeemed.players[0].money)
        assertFalse(redeemed.properties[11]!!.mortgaged)
    }
}
