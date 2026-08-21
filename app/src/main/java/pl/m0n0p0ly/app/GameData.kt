package pl.m0n0p0ly.app

data class Field(val name: String, val price: Int? = null, val kind: Kind = Kind.SPECIAL, val group: String? = null)
enum class Kind { STREET, STATION, UTILITY, SPECIAL }

object GameData {
    val fields = listOf(
        Field("START"), Field("Ulica Konopacka",60,Kind.STREET,"BRĄZOWA"), Field("Kasa Społeczna"), Field("Ulica Stalowa",60,Kind.STREET,"BRĄZOWA"), Field("Podatek Dochodowy"), Field("Dworzec Zachodni",200,Kind.STATION), Field("Szansa"), Field("Ulica Radzymińska",100,Kind.STREET,"JASNONIEBIESKA"), Field("Ulica Jagiellońska",100,Kind.STREET,"JASNONIEBIESKA"), Field("Ulica Targowa",120,Kind.STREET,"JASNONIEBIESKA"), Field("Więzienie / Odwiedzający"), Field("Ulica Płowiecka",140,Kind.STREET,"RÓŻOWA"), Field("Elektrownia",150,Kind.UTILITY), Field("Ulica Marsa",140,Kind.STREET,"RÓŻOWA"), Field("Ulica Grochowska",140,Kind.STREET,"RÓŻOWA"), Field("Dworzec Gdański",200,Kind.STATION), Field("Ulica Obozowa",180,Kind.STREET,"POMARAŃCZOWA"), Field("Ulica Górczewska",180,Kind.STREET,"POMARAŃCZOWA"), Field("Kasa Społeczna"), Field("Ulica Wolska",200,Kind.STREET,"POMARAŃCZOWA"), Field("Bezpłatny Parking"), Field("Ulica Mickiewicza",220,Kind.STREET,"CZERWONA"), Field("Szansa"), Field("Ulica Słowackiego",220,Kind.STREET,"CZERWONA"), Field("Plac Wilsona",240,Kind.STREET,"CZERWONA"), Field("Dworzec Wschodni",200,Kind.STATION), Field("Krakowskie Przedmieście",260,Kind.STREET,"ŻÓŁTA"), Field("Ulica Świętokrzyska",260,Kind.STREET,"ŻÓŁTA"), Field("Wodociągi",150,Kind.UTILITY), Field("Nowy Świat",280,Kind.STREET,"ŻÓŁTA"), Field("Idź do Więzienia"), Field("Plac Trzech Krzyży",300,Kind.STREET,"ZIELONA"), Field("Ulica Marszałkowska",300,Kind.STREET,"ZIELONA"), Field("Kasa Społeczna"), Field("Aleje Jerozolimskie",320,Kind.STREET,"ZIELONA"), Field("Dworzec Centralny",200,Kind.STATION), Field("Szansa"), Field("Ulica Belwederska",350,Kind.STREET,"NIEBIESKA"), Field("Domiar Podatkowy"), Field("Aleje Ujazdowskie",400,Kind.STREET,"NIEBIESKA")
    )
    fun mortgage(price: Int) = price / 2
    fun redemption(price: Int) = (mortgage(price) * 110 + 99) / 100
}
