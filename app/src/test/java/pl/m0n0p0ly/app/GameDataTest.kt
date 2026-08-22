package pl.m0n0p0ly.app
import org.junit.Assert.assertEquals
import org.junit.Test
class GameDataTest {
    @Test fun mortgageIsHalfAndRedemptionIsWhole() { assertEquals(75, GameData.mortgage(150)); assertEquals(83, GameData.redemption(150)); assertEquals(110, GameData.redemption(200)) }
    @Test fun boardHasFortyFields() { assertEquals(40, GameData.fields.size) }
    @Test fun boardOrderMatchesPhysicalBoard() {
        val expected = listOf(
            "START", "Ulica Konopacka", "Kasa Społeczna", "Ulica Stalowa", "Podatek Dochodowy", "Dworzec Zachodni",
            "Ulica Radzymińska", "Szansa", "Ulica Jagiellońska", "Ulica Targowa", "Więzienie / Odwiedzający",
            "Ulica Płowiecka", "Elektrownia", "Ulica Marsa", "Ulica Grochowska", "Dworzec Gdański", "Ulica Obozowa",
            "Kasa Społeczna", "Ulica Górczewska", "Ulica Wolska", "Bezpłatny Parking", "Ulica Mickiewicza", "Szansa",
            "Ulica Słowackiego", "Plac Wilsona", "Dworzec Wschodni", "Ulica Świętokrzyska", "Krakowskie Przedmieście",
            "Wodociągi", "Nowy Świat", "Idź do Więzienia", "Plac Trzech Krzyży", "Ulica Marszałkowska", "Kasa Społeczna",
            "Aleje Jerozolimskie", "Dworzec Centralny", "Szansa", "Ulica Belwederska", "Domiar Podatkowy", "Aleje Ujazdowskie"
        )
        assertEquals(expected, GameData.fields.map { it.name })
    }
}
