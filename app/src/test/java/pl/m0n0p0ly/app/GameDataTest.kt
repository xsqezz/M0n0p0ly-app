package pl.m0n0p0ly.app
import org.junit.Assert.assertEquals
import org.junit.Test
class GameDataTest { @Test fun mortgageIsHalfAndRedemptionIsWhole() { assertEquals(75, GameData.mortgage(150)); assertEquals(83, GameData.redemption(150)); assertEquals(110, GameData.redemption(200)) } @Test fun boardHasFortyFields() { assertEquals(40, GameData.fields.size) } }
