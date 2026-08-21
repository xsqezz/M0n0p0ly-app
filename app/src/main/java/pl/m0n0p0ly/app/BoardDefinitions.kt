package pl.m0n0p0ly.app

data class RentTable(val base: Int, val monopoly: Int, val houses: List<Int>, val hotel: Int)
data class PropertyDefinition(val index: Int, val name: String, val price: Int, val kind: Kind, val group: String? = null, val rent: RentTable? = null, val houseCost: Int = 0, val hotelCost: Int = 0)
enum class CardDeck { CHANCE, COMMUNITY }
data class GameCard(val id: String, val deck: CardDeck, val title: String, val text: String)

object BoardDefinitions {
    private fun street(index: Int, name: String, price: Int, group: String, rents: List<Int>, house: Int) = PropertyDefinition(index, name, price, Kind.STREET, group, RentTable(rents[0], rents[1], rents.subList(2, 6), rents[6]), house, house)
    val properties = listOf(
        street(1,"Ulica Konopacka",60,"BRĄZOWA",listOf(2,4,10,30,90,160,250),50), street(3,"Ulica Stalowa",60,"BRĄZOWA",listOf(4,8,20,60,180,320,450),50),
        street(7,"Ulica Radzymińska",100,"JASNONIEBIESKA",listOf(6,12,30,90,270,400,550),50), street(8,"Ulica Jagiellońska",100,"JASNONIEBIESKA",listOf(6,12,30,90,270,400,550),50), street(9,"Ulica Targowa",120,"JASNONIEBIESKA",listOf(8,16,40,100,300,450,600),50),
        street(11,"Ulica Płowiecka",140,"RÓŻOWA",listOf(10,20,50,150,450,625,750),100), street(13,"Ulica Marsa",140,"RÓŻOWA",listOf(10,20,50,150,450,625,750),100), street(14,"Ulica Grochowska",140,"RÓŻOWA",listOf(12,24,60,180,500,700,900),100),
        street(16,"Ulica Obozowa",180,"POMARAŃCZOWA",listOf(14,28,70,200,550,750,950),100), street(17,"Ulica Górczewska",180,"POMARAŃCZOWA",listOf(14,28,70,200,550,750,950),100), street(19,"Ulica Wolska",200,"POMARAŃCZOWA",listOf(16,32,80,220,600,800,1000),100),
        street(21,"Ulica Mickiewicza",220,"CZERWONA",listOf(18,36,90,250,700,875,1050),150), street(23,"Ulica Słowackiego",220,"CZERWONA",listOf(18,36,90,250,700,875,1050),150), street(24,"Plac Wilsona",240,"CZERWONA",listOf(20,40,100,300,750,925,1100),150),
        street(26,"Krakowskie Przedmieście",260,"ŻÓŁTA",listOf(22,44,110,330,800,975,1150),150), street(27,"Ulica Świętokrzyska",260,"ŻÓŁTA",listOf(22,44,110,330,800,975,1150),150), street(29,"Nowy Świat",280,"ŻÓŁTA",listOf(24,48,120,360,850,1025,1200),150),
        street(31,"Plac Trzech Krzyży",300,"ZIELONA",listOf(26,52,130,390,900,1100,1275),200), street(32,"Ulica Marszałkowska",300,"ZIELONA",listOf(26,52,130,390,900,1100,1275),200), street(34,"Aleje Jerozolimskie",320,"ZIELONA",listOf(28,56,150,450,1000,1200,1400),200),
        street(37,"Ulica Belwederska",350,"NIEBIESKA",listOf(35,70,175,500,1100,1300,1500),200), street(39,"Aleje Ujazdowskie",400,"NIEBIESKA",listOf(50,100,200,600,1400,1700,2000),200),
        PropertyDefinition(5,"Dworzec Zachodni",200,Kind.STATION), PropertyDefinition(15,"Dworzec Gdański",200,Kind.STATION), PropertyDefinition(25,"Dworzec Wschodni",200,Kind.STATION), PropertyDefinition(35,"Dworzec Centralny",200,Kind.STATION),
        PropertyDefinition(12,"Elektrownia",150,Kind.UTILITY), PropertyDefinition(28,"Wodociągi",150,Kind.UTILITY)
    )
    val byIndex = properties.associateBy { it.index }

    // The photographed Chance card “Łap ten hajs!” is intentionally omitted.
    val chance = listOf(
        GameCard("c1",CardDeck.CHANCE,"Elektrownia lub Wodociągi","Idź do najbliższej elektrowni lub wodociągów. Możesz kupić albo zapłać 10× wynik rzutu."),
        GameCard("c2",CardDeck.CHANCE,"Dworzec Zachodni","Idź do Dworca Zachodniego. Pobierz M200, jeśli przechodzisz przez START."),
        GameCard("c4",CardDeck.CHANCE,"Więzienie","Idź do więzienia. Nie pobierasz M200 za przejście przez START."),
        GameCard("c5",CardDeck.CHANCE,"Plac Wilsona","Idź na Plac Wilsona. Pobierz M200, jeśli przechodzisz przez START."),
        GameCard("c6",CardDeck.CHANCE,"Mandat","Zapłać M15."), GameCard("c7",CardDeck.CHANCE,"Remont generalny","Zapłać M25 za każdy dom i M100 za każdy hotel."),
        GameCard("c8",CardDeck.CHANCE,"Zwrot pożyczki","Otrzymujesz M150."), GameCard("c9",CardDeck.CHANCE,"Najbliższy dworzec","Idź do najbliższego dworca. Jeśli jest własnością, zapłać podwójny czynsz."),
        GameCard("c10",CardDeck.CHANCE,"Aleje Ujazdowskie","Idź na Aleje Ujazdowskie."), GameCard("c11",CardDeck.CHANCE,"Ulica Płowiecka","Idź na Ulicę Płowiecką. Pobierz M200, jeśli przechodzisz przez START."),
        GameCard("c12",CardDeck.CHANCE,"START","Idź na START i pobierz M200."), GameCard("c13",CardDeck.CHANCE,"Wyjście z więzienia","Zachowaj tę kartę. Możesz ją użyć, wymienić albo sprzedać."),
        GameCard("c14",CardDeck.CHANCE,"Prezes zarządu","Zapłać każdemu graczowi M50."), GameCard("c15",CardDeck.CHANCE,"Cofnij się","Cofnij się o 3 pola."), GameCard("c16",CardDeck.CHANCE,"Najbliższy dworzec","Idź do najbliższego dworca. Jeśli jest własnością, zapłać podwójny czynsz.")
    )
    val community = listOf(
        GameCard("s1",CardDeck.COMMUNITY,"Pomyłka banku","Otrzymujesz M200."), GameCard("s2",CardDeck.COMMUNITY,"Zwrot podatku","Otrzymujesz M20."), GameCard("s3",CardDeck.COMMUNITY,"Sprzedaż akcji","Otrzymujesz M50."),
        GameCard("s4",CardDeck.COMMUNITY,"Ubezpieczenie na życie","Otrzymujesz M100."), GameCard("s5",CardDeck.COMMUNITY,"Fundusz wakacyjny","Otrzymujesz M100."), GameCard("s6",CardDeck.COMMUNITY,"Spadek","Otrzymujesz M100."),
        GameCard("s7",CardDeck.COMMUNITY,"Urodziny","Otrzymujesz M10 od każdego gracza."), GameCard("s8",CardDeck.COMMUNITY,"Konkurs piękności","Otrzymujesz M10 za drugie miejsce."),
        GameCard("s9",CardDeck.COMMUNITY,"Usługa konsultingowa","Otrzymujesz M25."), GameCard("s10",CardDeck.COMMUNITY,"Opłata szkolna","Zapłać M50."), GameCard("s11",CardDeck.COMMUNITY,"Konsultacja medyczna","Zapłać M50."),
        GameCard("s12",CardDeck.COMMUNITY,"Remont","Zapłać M40 za każdy dom i M115 za każdy hotel."), GameCard("s13",CardDeck.COMMUNITY,"START","Idź na START i pobierz M200."),
        GameCard("s14",CardDeck.COMMUNITY,"Więzienie","Idź do więzienia. Nie pobierasz M200 za przejście przez START."), GameCard("s15",CardDeck.COMMUNITY,"Wyjście z więzienia","Zachowaj tę kartę. Możesz ją użyć, wymienić albo sprzedać.")
    )
}
