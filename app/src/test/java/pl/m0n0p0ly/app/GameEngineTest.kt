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
}
