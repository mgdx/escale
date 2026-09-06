package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tableau des catégories de SPEC.md § 5.7, ligne par ligne.
 *
 * Rien ici ne touche à Android : c'est ce que le § 1 de docs/architecture.md attend d'une règle
 * métier, et c'est ce qui permet d'éprouver la table sans allumer une carte.
 */
class PoiCategoryTest {

  // --- Les quatre catégories de repères -------------------------------------------------------

  @Test
  fun `une mairie est un service public`() {
    assertEquals(PoiCategory.PUBLIC_SERVICES, poiCategory(amenity = "townhall"))
  }

  @Test
  fun `un lieu de culte est un service public, et non une catégorie à lui seul`() {
    // Décision du mainteneur, reprise telle quelle : pas de catégorie « religion ».
    assertEquals(PoiCategory.PUBLIC_SERVICES, poiCategory(amenity = "place_of_worship"))
  }

  @Test
  fun `une bibliothèque relève de l'enseignement`() {
    assertEquals(PoiCategory.EDUCATION, poiCategory(amenity = "library"))
  }

  @Test
  fun `un château relève de la culture, comme un point de vue et un phare`() {
    assertEquals(PoiCategory.CULTURE, poiCategory(historic = "castle"))
    assertEquals(PoiCategory.CULTURE, poiCategory(tourism = "viewpoint"))
    assertEquals(PoiCategory.CULTURE, poiCategory(manMade = "lighthouse"))
  }

  @Test
  fun `une pharmacie relève de la santé publique`() {
    assertEquals(PoiCategory.HEALTHCARE, poiCategory(amenity = "pharmacy"))
  }

  // --- Les huit catégories de commerces et services -------------------------------------------

  @Test
  fun `des toilettes publiques forment leur propre catégorie`() {
    assertEquals(PoiCategory.TOILETS, poiCategory(amenity = "toilets"))
  }

  @Test
  fun `une boulangerie est de l'alimentation`() {
    assertEquals(PoiCategory.FOOD, poiCategory(shop = "bakery"))
  }

  @Test
  fun `un restaurant est de la restauration`() {
    assertEquals(PoiCategory.DINING, poiCategory(amenity = "restaurant"))
  }

  @Test
  fun `un opticien et un vétérinaire sont des commerces de santé`() {
    assertEquals(PoiCategory.HEALTH_SHOPS, poiCategory(shop = "optician"))
    assertEquals(PoiCategory.HEALTH_SHOPS, poiCategory(amenity = "veterinary"))
  }

  @Test
  fun `un distributeur de billets relève de l'argent`() {
    assertEquals(PoiCategory.MONEY, poiCategory(amenity = "atm"))
  }

  @Test
  fun `un camping est un hébergement`() {
    assertEquals(PoiCategory.LODGING, poiCategory(tourism = "camp_site"))
  }

  @Test
  fun `un point d'eau reste un service du quotidien, lui`() {
    // Seules les toilettes en sont sorties : le point d'eau ne suit pas.
    assertEquals(PoiCategory.EVERYDAY_SERVICES, poiCategory(amenity = "drinking_water"))
    assertEquals(PoiCategory.EVERYDAY_SERVICES, poiCategory(shop = "hairdresser"))
  }

  @Test
  fun `les douze catégories sont toutes atteignables`() {
    val reached = POI_TYPES.map { it.category }.toSet() + PoiCategory.OTHER_SHOPS
    assertEquals(PoiCategory.entries.toSet(), reached)
  }

  // --- Les replis ------------------------------------------------------------------------------

  @Test
  fun `une valeur de shop inconnue reste un commerce`() {
    // « Toute autre valeur de `shop` », dernière ligne du tableau de la spec.
    assertEquals(PoiCategory.OTHER_SHOPS, poiCategory(shop = "musical_instrument"))
  }

  @Test
  fun `un amenity hors de toute liste n'est d'aucune catégorie`() {
    assertNull(poiCategory(amenity = "fountain"))
    assertNull(poiCategory(tourism = "aquarium"))
    assertNull(poiCategory())
  }

  @Test
  fun `une vitrine éteinte n'est pas un commerce`() {
    assertNull(poiCategory(shop = "no"))
    assertNull(poiCategory(shop = "vacant"))
  }

  @Test
  fun `un commerce l'emporte sur l'équipement porté par la même entité`() {
    assertEquals(PoiCategory.FOOD, poiCategory(shop = "bakery", amenity = "cafe"))
  }

  // --- Défauts et paliers ----------------------------------------------------------------------

  @Test
  fun `les quatre repères sont allumés d'emblée, les commerces non, sauf les toilettes`() {
    assertEquals(
      PoiCategory.LANDMARKS + PoiCategory.TOILETS,
      PoiCategory.DEFAULT_VISIBLE,
    )
    assertTrue(PoiCategory.LANDMARKS.all { it.kind == PoiKind.LANDMARK })
    assertEquals(4, PoiCategory.LANDMARKS.size)
  }

  @Test
  fun `les toilettes gardent les paliers des commerces`() {
    assertEquals(PoiKind.SHOP, PoiCategory.TOILETS.kind)
  }

  // --- Les clés de traduction -------------------------------------------------------------------

  @Test
  fun `chaque valeur retenue a sa propre clé`() {
    assertEquals(PoiTypeKey.BAKERY, poiTypeKey(shop = "bakery"))
    assertEquals(PoiTypeKey.RESTAURANT, poiTypeKey(amenity = "restaurant"))
    assertEquals(PoiTypeKey.HOTEL, poiTypeKey(tourism = "hotel"))
    assertEquals(PoiTypeKey.MONUMENT, poiTypeKey(historic = "monument"))
    assertEquals(PoiTypeKey.LIGHTHOUSE, poiTypeKey(manMade = "lighthouse"))
  }

  @Test
  fun `les repères se nomment aussi, puisqu'ils sont désormais tapables`() {
    assertEquals(PoiTypeKey.PLACE_OF_WORSHIP, poiTypeKey(amenity = "place_of_worship"))
    assertEquals(PoiTypeKey.TOWNHALL, poiTypeKey(amenity = "townhall"))
    assertEquals(PoiTypeKey.PHARMACY, poiTypeKey(amenity = "pharmacy"))
  }

  @Test
  fun `un commerce que la table ne nomme pas n'a pas de clé, mais garde sa catégorie`() {
    assertNull(poiTypeKey(shop = "musical_instrument"))
    assertEquals(PoiCategory.OTHER_SHOPS, poiCategory(shop = "musical_instrument"))
  }

  @Test
  fun `la casse et les espaces de la tuile ne changent rien`() {
    assertEquals(PoiTypeKey.BAKERY, poiTypeKey(shop = " Bakery "))
    assertEquals(PoiCategory.FOOD, poiCategory(shop = " Bakery "))
  }

  @Test
  fun `la table ne dit jamais deux fois la même chose`() {
    val keys = POI_TYPES.map { it.key }
    assertEquals(keys.size, keys.toSet().size)
    val values = POI_TYPES.map { it.tag to it.value }
    assertEquals(values.size, values.toSet().size)
    assertEquals(PoiTypeKey.entries.toSet(), keys.toSet())
  }

  // --- Les compléments de la fiche ---------------------------------------------------------------

  @Test
  fun `la cuisine complète le type, et se traduit`() {
    // La tuile porte « italian » ; la fiche ne doit pas afficher d'anglais dans une interface en
    // français, d'où la clé plutôt que la valeur brute.
    assertEquals(PoiComplement.Named(PoiDetailKey.ITALIAN), poiComplement(cuisine = "italian"))
  }

  @Test
  fun `une cuisine multiple se réduit à la première`() {
    assertEquals(PoiComplement.Named(PoiDetailKey.PIZZA), poiComplement(cuisine = "pizza;kebab"))
  }

  @Test
  fun `la confession complète le lieu de culte, et prime sur la religion`() {
    assertEquals(
      PoiComplement.Named(PoiDetailKey.CATHOLIC),
      poiComplement(religion = "christian", denomination = "catholic"),
    )
    assertEquals(PoiComplement.Named(PoiDetailKey.MUSLIM), poiComplement(religion = "muslim"))
  }

  @Test
  fun `le distributeur complète la banque, à défaut de mieux`() {
    assertEquals(PoiComplement.CashMachine, poiComplement(atm = true))
    assertEquals(PoiComplement.Named(PoiDetailKey.ITALIAN), poiComplement(cuisine = "italian", atm = true))
  }

  @Test
  fun `oui et non ne sont pas des mots`() {
    assertNull(poiComplement(cuisine = "yes"))
    assertNull(poiComplement(religion = "no"))
    assertNull(poiComplement())
  }

  @Test
  fun `une valeur que la table ne nomme pas se rend lisible, faute de mieux`() {
    // OpenStreetMap compte des centaines de cuisines et il en naît chaque semaine : mieux vaut un
    // mot anglais lisible qu'une valeur brute avec ses tirets bas, et mieux que rien du tout.
    assertEquals(PoiComplement.Unnamed("Molecular"), poiComplement(cuisine = "molecular"))
    assertEquals(PoiComplement.Unnamed("Soul food"), poiComplement(cuisine = "soul_food"))
  }

  @Test
  fun `les valeurs nommées le sont dans les trois étiquettes`() {
    assertEquals(PoiComplement.Named(PoiDetailKey.FISH_AND_CHIPS), poiDetail("fish_and_chips"))
    assertEquals(PoiComplement.Named(PoiDetailKey.GEORGIAN), poiDetail("georgian"))
    assertEquals(PoiComplement.Named(PoiDetailKey.RUSSIAN_ORTHODOX), poiDetail("russian_orthodox"))
    assertNull(poiDetail(null))
    assertNull(poiDetail("  "))
  }

  @Test
  fun `la casse de la tuile ne change rien au complément`() {
    assertEquals(PoiComplement.Named(PoiDetailKey.ITALIAN), poiDetail(" Italian "))
  }

  // --- Un complément qui répète le type n'apprend rien -------------------------------------------

  @Test
  fun `un café dont la cuisine est le café n'affiche pas deux fois le mot`() {
    // Vu sur un Starbucks : « Café · café ». Le complément ne dit rien de plus que le type.
    assertNull(poiComplement(typeKey = PoiTypeKey.CAFE, cuisine = "coffee_shop"))
    assertNull(poiComplement(typeKey = PoiTypeKey.CAFE, cuisine = "cafe"))
    assertNull(poiComplement(typeKey = PoiTypeKey.CAFE, cuisine = "coffee"))
  }

  @Test
  fun `la règle vaut pour tout type dont la cuisine reprend le nom`() {
    // Aucune table à tenir pour ces cas-là : la valeur du complément est celle du type.
    assertNull(poiComplement(typeKey = PoiTypeKey.BAR, cuisine = "bar"))
    assertNull(poiComplement(typeKey = PoiTypeKey.FAST_FOOD, cuisine = "fast_food"))
    assertNull(poiComplement(typeKey = PoiTypeKey.PUB, cuisine = "pub"))
    assertNull(poiComplement(typeKey = PoiTypeKey.RESTAURANT, cuisine = "restaurant"))
    assertNull(poiComplement(typeKey = PoiTypeKey.BAKERY, cuisine = "bakery"))
  }

  @Test
  fun `un complément qui apprend quelque chose reste affiché`() {
    assertEquals(
      PoiComplement.Named(PoiDetailKey.ITALIAN),
      poiComplement(typeKey = PoiTypeKey.RESTAURANT, cuisine = "italian"),
    )
    // Un café qui sert des crêpes n'est pas un pléonasme.
    assertEquals(
      PoiComplement.Named(PoiDetailKey.CREPE),
      poiComplement(typeKey = PoiTypeKey.CAFE, cuisine = "crepe"),
    )
  }

  @Test
  fun `la redondance n'écarte que le complément fautif, pas les suivants`() {
    // La cuisine est écartée, le distributeur prend sa place : la fiche ne perd rien au change.
    assertEquals(
      PoiComplement.CashMachine,
      poiComplement(typeKey = PoiTypeKey.CAFE, cuisine = "cafe", atm = true),
    )
  }

  @Test
  fun `sans type connu, le complément est rendu tel quel`() {
    // Rien à comparer : un lieu que la table ne nomme pas ne peut pas se répéter.
    assertEquals(PoiComplement.Named(PoiDetailKey.ITALIAN), poiComplement(cuisine = "italian"))
  }
}
