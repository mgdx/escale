package io.github.mgdx.escale.core.model

/**
 * Ce qu'une catégorie de points d'intérêt montre, et à quelle échelle (SPEC.md § 5.7).
 *
 * Les deux familles ne se règlent pas de la même façon : les **repères** aident à s'orienter et
 * sont affichés d'emblée dès le zoom 15, tandis que les **commerces et services** densifient la
 * carte et ne s'affichent qu'à la demande, à partir du zoom 16.
 */
enum class PoiKind {
  /** Services publics, enseignement, monuments, santé : les repères de la v1. */
  LANDMARK,

  /** Commerces et services, plus denses et plus nombreux. */
  SHOP,
}

/**
 * Les douze catégories de points d'intérêt de la carte (SPEC.md § 5.6 et § 5.7).
 *
 * Chacune se règle séparément dans l'écran « Couches de la carte », et chacune est **une couche**
 * de la feuille de style qu'on allume ou qu'on éteint — jamais une couche qu'on ajoute ou qu'on
 * retire (SPEC.md § 5.7, règle 8).
 *
 * [visibleByDefault] reprend le partage de SPEC.md § 5.7 : les repères sont là au premier
 * lancement, les commerces attendent qu'on les demande. Les toilettes publiques font exception,
 * et c'est délibéré : elles se cherchent, elles ne s'explorent pas.
 *
 * Les noms d'entrées sont **enregistrés tels quels** dans les réglages : les renommer effacerait
 * le choix des usagers.
 */
enum class PoiCategory(val kind: PoiKind, val visibleByDefault: Boolean) {
  /** Mairies, bureaux de poste, police, pompiers, lieux de culte. */
  PUBLIC_SERVICES(PoiKind.LANDMARK, visibleByDefault = true),

  /** Écoles, universités, bibliothèques. */
  EDUCATION(PoiKind.LANDMARK, visibleByDefault = true),

  /** Théâtres, cinémas, châteaux, monuments, points de vue. */
  CULTURE(PoiKind.LANDMARK, visibleByDefault = true),

  /** Hôpitaux, cabinets médicaux, pharmacies. */
  HEALTHCARE(PoiKind.LANDMARK, visibleByDefault = true),

  /** La seule couche de cette section affichée d’emblée. */
  TOILETS(PoiKind.SHOP, visibleByDefault = true),

  /** Supermarchés, boulangeries, boucheries, marchés. */
  FOOD(PoiKind.SHOP, visibleByDefault = false),

  /** Restaurants, cafés, bars, pubs. */
  DINING(PoiKind.SHOP, visibleByDefault = false),

  /** Drogueries, opticiens, vétérinaires. */
  HEALTH_SHOPS(PoiKind.SHOP, visibleByDefault = false),

  /** Banques et distributeurs de billets. */
  MONEY(PoiKind.SHOP, visibleByDefault = false),

  /** Hôtels, auberges, campings. */
  LODGING(PoiKind.SHOP, visibleByDefault = false),

  /** Coiffeurs, laveries, boîtes aux lettres, points d’eau, location de véhicules. */
  EVERYDAY_SERVICES(PoiKind.SHOP, visibleByDefault = false),

  /** Tous les autres commerces portés par les tuiles. */
  OTHER_SHOPS(PoiKind.SHOP, visibleByDefault = false),

  ;

  companion object {
    /** Ce qu'une carte montre à qui n'a jamais rien réglé. */
    val DEFAULT_VISIBLE: Set<PoiCategory> = entries.filter { it.visibleByDefault }.toSet()

    /** Les quatre catégories que le réglage unique « points d'intérêt » commandait autrefois. */
    val LANDMARKS: Set<PoiCategory> = entries.filter { it.kind == PoiKind.LANDMARK }.toSet()
  }
}

/** L'étiquette OpenStreetMap qui porte la valeur d'un lieu. */
enum class PoiTag {
  SHOP,
  AMENITY,
  TOURISM,
  HISTORIC,
  MAN_MADE,
}

/**
 * La clé de traduction du type d'un lieu : une entrée par valeur OpenStreetMap retenue.
 *
 * Les traductions vivent dans `:app`, qui associe chaque entrée à sa chaîne — une association
 * exhaustive, vérifiée par un test, là où un `when` aurait demandé soixante-dix branches.
 */
enum class PoiTypeKey {
  TOWNHALL,
  COURTHOUSE,
  POST_OFFICE,
  POLICE,
  FIRE_STATION,
  COMMUNITY_CENTRE,
  PLACE_OF_WORSHIP,
  SCHOOL,
  UNIVERSITY,
  COLLEGE,
  LIBRARY,
  THEATRE,
  CINEMA,
  ARTS_CENTRE,
  CASTLE,
  MONUMENT,
  MEMORIAL,
  FORT,
  RUINS,
  ARCHAEOLOGICAL_SITE,
  BATTLEFIELD,
  ARTWORK,
  VIEWPOINT,
  LIGHTHOUSE,
  HOSPITAL,
  CLINIC,
  DOCTORS,
  DENTIST,
  PHARMACY,
  TOILETS,
  SUPERMARKET,
  CONVENIENCE,
  BAKERY,
  BUTCHER,
  GREENGROCER,
  ALCOHOL,
  BEVERAGES,
  KIOSK,
  GENERAL,
  DEPARTMENT_STORE,
  MALL,
  MARKETPLACE,
  RESTAURANT,
  FAST_FOOD,
  CAFE,
  PUB,
  BAR,
  BIERGARTEN,
  VETERINARY,
  CHEMIST,
  OPTICIAN,
  BANK,
  ATM,
  HOTEL,
  MOTEL,
  HOSTEL,
  GUEST_HOUSE,
  BED_AND_BREAKFAST,
  CAMP_SITE,
  DRINKING_WATER,
  POST_BOX,
  TELEPHONE,
  CAR_RENTAL,
  CAR_SHARING,
  BICYCLE_RENTAL,
  VENDING_MACHINE,
  HAIRDRESSER,
  LAUNDRY,
  DRY_CLEANING,
  TRAVEL_AGENCY,
}

/** Une valeur OpenStreetMap que la carte sait afficher et nommer (SPEC.md § 5.7). */
data class PoiType(val tag: PoiTag, val value: String, val key: PoiTypeKey, val category: PoiCategory)

/**
 * La table des catégories de SPEC.md § 5.7, dans l'ordre où la spec les énonce.
 *
 * Source unique : les libellés de l'interface, le filtre de chacune des douze couches de la
 * feuille de style et le réglage de l'écran « Couches de la carte » en découlent tous, et des
 * tests comparent les feuilles à cette liste.
 */
val POI_TYPES: List<PoiType> = listOf(
  PoiType(PoiTag.AMENITY, "townhall", PoiTypeKey.TOWNHALL, PoiCategory.PUBLIC_SERVICES),
  PoiType(PoiTag.AMENITY, "courthouse", PoiTypeKey.COURTHOUSE, PoiCategory.PUBLIC_SERVICES),
  PoiType(PoiTag.AMENITY, "post_office", PoiTypeKey.POST_OFFICE, PoiCategory.PUBLIC_SERVICES),
  PoiType(PoiTag.AMENITY, "police", PoiTypeKey.POLICE, PoiCategory.PUBLIC_SERVICES),
  PoiType(PoiTag.AMENITY, "fire_station", PoiTypeKey.FIRE_STATION, PoiCategory.PUBLIC_SERVICES),
  PoiType(PoiTag.AMENITY, "community_centre", PoiTypeKey.COMMUNITY_CENTRE, PoiCategory.PUBLIC_SERVICES),
  PoiType(PoiTag.AMENITY, "place_of_worship", PoiTypeKey.PLACE_OF_WORSHIP, PoiCategory.PUBLIC_SERVICES),
  PoiType(PoiTag.AMENITY, "school", PoiTypeKey.SCHOOL, PoiCategory.EDUCATION),
  PoiType(PoiTag.AMENITY, "university", PoiTypeKey.UNIVERSITY, PoiCategory.EDUCATION),
  PoiType(PoiTag.AMENITY, "college", PoiTypeKey.COLLEGE, PoiCategory.EDUCATION),
  PoiType(PoiTag.AMENITY, "library", PoiTypeKey.LIBRARY, PoiCategory.EDUCATION),
  PoiType(PoiTag.AMENITY, "theatre", PoiTypeKey.THEATRE, PoiCategory.CULTURE),
  PoiType(PoiTag.AMENITY, "cinema", PoiTypeKey.CINEMA, PoiCategory.CULTURE),
  PoiType(PoiTag.AMENITY, "arts_centre", PoiTypeKey.ARTS_CENTRE, PoiCategory.CULTURE),
  PoiType(PoiTag.HISTORIC, "castle", PoiTypeKey.CASTLE, PoiCategory.CULTURE),
  PoiType(PoiTag.HISTORIC, "monument", PoiTypeKey.MONUMENT, PoiCategory.CULTURE),
  PoiType(PoiTag.HISTORIC, "memorial", PoiTypeKey.MEMORIAL, PoiCategory.CULTURE),
  PoiType(PoiTag.HISTORIC, "fort", PoiTypeKey.FORT, PoiCategory.CULTURE),
  PoiType(PoiTag.HISTORIC, "ruins", PoiTypeKey.RUINS, PoiCategory.CULTURE),
  PoiType(PoiTag.HISTORIC, "archaeological_site", PoiTypeKey.ARCHAEOLOGICAL_SITE, PoiCategory.CULTURE),
  PoiType(PoiTag.HISTORIC, "battlefield", PoiTypeKey.BATTLEFIELD, PoiCategory.CULTURE),
  PoiType(PoiTag.TOURISM, "artwork", PoiTypeKey.ARTWORK, PoiCategory.CULTURE),
  PoiType(PoiTag.TOURISM, "viewpoint", PoiTypeKey.VIEWPOINT, PoiCategory.CULTURE),
  PoiType(PoiTag.MAN_MADE, "lighthouse", PoiTypeKey.LIGHTHOUSE, PoiCategory.CULTURE),
  PoiType(PoiTag.AMENITY, "hospital", PoiTypeKey.HOSPITAL, PoiCategory.HEALTHCARE),
  PoiType(PoiTag.AMENITY, "clinic", PoiTypeKey.CLINIC, PoiCategory.HEALTHCARE),
  PoiType(PoiTag.AMENITY, "doctors", PoiTypeKey.DOCTORS, PoiCategory.HEALTHCARE),
  PoiType(PoiTag.AMENITY, "dentist", PoiTypeKey.DENTIST, PoiCategory.HEALTHCARE),
  PoiType(PoiTag.AMENITY, "pharmacy", PoiTypeKey.PHARMACY, PoiCategory.HEALTHCARE),
  PoiType(PoiTag.AMENITY, "toilets", PoiTypeKey.TOILETS, PoiCategory.TOILETS),
  PoiType(PoiTag.SHOP, "supermarket", PoiTypeKey.SUPERMARKET, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "convenience", PoiTypeKey.CONVENIENCE, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "bakery", PoiTypeKey.BAKERY, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "butcher", PoiTypeKey.BUTCHER, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "greengrocer", PoiTypeKey.GREENGROCER, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "alcohol", PoiTypeKey.ALCOHOL, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "beverages", PoiTypeKey.BEVERAGES, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "kiosk", PoiTypeKey.KIOSK, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "general", PoiTypeKey.GENERAL, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "department_store", PoiTypeKey.DEPARTMENT_STORE, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "mall", PoiTypeKey.MALL, PoiCategory.FOOD),
  PoiType(PoiTag.SHOP, "marketplace", PoiTypeKey.MARKETPLACE, PoiCategory.FOOD),
  PoiType(PoiTag.AMENITY, "restaurant", PoiTypeKey.RESTAURANT, PoiCategory.DINING),
  PoiType(PoiTag.AMENITY, "fast_food", PoiTypeKey.FAST_FOOD, PoiCategory.DINING),
  PoiType(PoiTag.AMENITY, "cafe", PoiTypeKey.CAFE, PoiCategory.DINING),
  PoiType(PoiTag.AMENITY, "pub", PoiTypeKey.PUB, PoiCategory.DINING),
  PoiType(PoiTag.AMENITY, "bar", PoiTypeKey.BAR, PoiCategory.DINING),
  PoiType(PoiTag.AMENITY, "biergarten", PoiTypeKey.BIERGARTEN, PoiCategory.DINING),
  PoiType(PoiTag.AMENITY, "veterinary", PoiTypeKey.VETERINARY, PoiCategory.HEALTH_SHOPS),
  PoiType(PoiTag.SHOP, "chemist", PoiTypeKey.CHEMIST, PoiCategory.HEALTH_SHOPS),
  PoiType(PoiTag.SHOP, "optician", PoiTypeKey.OPTICIAN, PoiCategory.HEALTH_SHOPS),
  PoiType(PoiTag.AMENITY, "bank", PoiTypeKey.BANK, PoiCategory.MONEY),
  PoiType(PoiTag.AMENITY, "atm", PoiTypeKey.ATM, PoiCategory.MONEY),
  PoiType(PoiTag.TOURISM, "hotel", PoiTypeKey.HOTEL, PoiCategory.LODGING),
  PoiType(PoiTag.TOURISM, "motel", PoiTypeKey.MOTEL, PoiCategory.LODGING),
  PoiType(PoiTag.TOURISM, "hostel", PoiTypeKey.HOSTEL, PoiCategory.LODGING),
  PoiType(PoiTag.TOURISM, "guest_house", PoiTypeKey.GUEST_HOUSE, PoiCategory.LODGING),
  PoiType(PoiTag.TOURISM, "bed_and_breakfast", PoiTypeKey.BED_AND_BREAKFAST, PoiCategory.LODGING),
  PoiType(PoiTag.TOURISM, "camp_site", PoiTypeKey.CAMP_SITE, PoiCategory.LODGING),
  PoiType(PoiTag.AMENITY, "drinking_water", PoiTypeKey.DRINKING_WATER, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.AMENITY, "post_box", PoiTypeKey.POST_BOX, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.AMENITY, "telephone", PoiTypeKey.TELEPHONE, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.AMENITY, "car_rental", PoiTypeKey.CAR_RENTAL, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.AMENITY, "car_sharing", PoiTypeKey.CAR_SHARING, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.AMENITY, "bicycle_rental", PoiTypeKey.BICYCLE_RENTAL, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.AMENITY, "vending_machine", PoiTypeKey.VENDING_MACHINE, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.SHOP, "hairdresser", PoiTypeKey.HAIRDRESSER, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.SHOP, "laundry", PoiTypeKey.LAUNDRY, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.SHOP, "dry_cleaning", PoiTypeKey.DRY_CLEANING, PoiCategory.EVERYDAY_SERVICES),
  PoiType(PoiTag.SHOP, "travel_agency", PoiTypeKey.TRAVEL_AGENCY, PoiCategory.EVERYDAY_SERVICES),
)

/**
 * Les valeurs de `shop` qui disent qu'il n'y a **pas** de commerce.
 *
 * OpenStreetMap emploie `shop=no` et `shop=vacant` pour un local fermé ou vide : les compter
 * comme des commerces poserait un pictogramme sur une vitrine éteinte.
 */
val NOT_A_SHOP: Set<String> = setOf("no", "vacant")

private val TYPES_BY_TAG: Map<PoiTag, Map<String, PoiType>> =
  POI_TYPES.groupBy { it.tag }.mapValues { (_, types) -> types.associateBy { it.value } }

/**
 * La catégorie d'un lieu, ou `null` s'il n'entre dans aucune des douze (SPEC.md § 5.7).
 *
 * `shop` est interrogé en premier, et une valeur qu'il porte sans que la table la nomme reste un
 * commerce : elle tombe dans [PoiCategory.OTHER_SHOPS], comme le veut la dernière ligne du
 * tableau de la spec. Un `amenity` hors table, lui, n'est pas un commerce et rend `null` : la
 * carte n'invente pas une catégorie pour une fontaine.
 */
fun poiCategory(
  shop: String? = null,
  amenity: String? = null,
  tourism: String? = null,
  historic: String? = null,
  manMade: String? = null,
): PoiCategory? = shopCategory(shop)
  ?: typeOf(PoiTag.AMENITY, amenity)?.category
  ?: typeOf(PoiTag.TOURISM, tourism)?.category
  ?: typeOf(PoiTag.HISTORIC, historic)?.category
  ?: typeOf(PoiTag.MAN_MADE, manMade)?.category

/**
 * La clé de traduction du type d'un lieu, ou `null` si la table ne le nomme pas.
 *
 * Les cinq étiquettes sont interrogées dans l'ordre de [poiCategory]. Un commerce que la table ne
 * nomme pas n'a pas de clé : la fiche affiche alors le libellé de sa catégorie, qui reste vrai.
 */
fun poiTypeKey(
  shop: String? = null,
  amenity: String? = null,
  tourism: String? = null,
  historic: String? = null,
  manMade: String? = null,
): PoiTypeKey? = typeOf(PoiTag.SHOP, shop)?.key
  ?: typeOf(PoiTag.AMENITY, amenity)?.key
  ?: typeOf(PoiTag.TOURISM, tourism)?.key
  ?: typeOf(PoiTag.HISTORIC, historic)?.key
  ?: typeOf(PoiTag.MAN_MADE, manMade)?.key

/**
 * Le complément qui suit le type dans la fiche (SPEC.md § 5.7).
 *
 * « Restaurant · italien », « Lieu de culte · catholique », « Banque · distributeur » : un seul
 * complément, celui qui apprend le plus. La cuisine passe avant la confession, qui passe avant le
 * distributeur, parce qu'un lieu n'en porte qu'un en pratique.
 */
fun poiComplement(
  cuisine: String? = null,
  atm: Boolean = false,
  religion: String? = null,
  denomination: String? = null,
): PoiComplement? = poiDetail(cuisine)
  ?: poiDetail(denomination)
  ?: poiDetail(religion)
  ?: PoiComplement.CashMachine.takeIf { atm }

/** Ce qui complète le type d'un lieu dans la fiche. */
sealed interface PoiComplement {
  /** Une valeur que la table nomme : elle a une traduction, et c'est elle qui s'affiche. */
  data class Named(val key: PoiDetailKey) : PoiComplement

  /**
   * Une valeur que la table ne nomme pas.
   *
   * OpenStreetMap en compte des centaines, et il en naît chaque semaine : plutôt que de taire
   * ce qu'on ne sait pas traduire, on le rend lisible — « coffee_shop » devient « Coffee
   * shop ». C'est de l'anglais, mais c'est vrai, et cela reste plus utile que rien.
   */
  data class Unnamed(val text: String) : PoiComplement

  /** La banque distribue des billets (`atm=yes`) : le complément est une chaîne traduite. */
  data object CashMachine : PoiComplement
}

/**
 * La clé de traduction d'une valeur de `cuisine`, de `religion` ou de `denomination`.
 *
 * Ces trois étiquettes portent des valeurs OpenStreetMap, donc **en anglais dans la tuile** :
 * les afficher telles quelles mettrait de l'anglais dans une interface en français, ce que
 * CLAUDE.md interdit. Une seule table pour les trois, parce que leurs valeurs ne se recouvrent
 * pas et qu'un lieu n'en porte qu'une à la fois.
 */
enum class PoiDetailKey {
  ITALIAN,
  FRENCH,
  PIZZA,
  BURGER,
  KEBAB,
  SUSHI,
  JAPANESE,
  CHINESE,
  INDIAN,
  THAI,
  VIETNAMESE,
  MEXICAN,
  GREEK,
  LEBANESE,
  TURKISH,
  SPANISH,
  PORTUGUESE,
  GERMAN,
  AMERICAN,
  GEORGIAN,
  MOROCCAN,
  KOREAN,
  ASIAN,
  AFRICAN,
  SEAFOOD,
  FISH,
  FISH_AND_CHIPS,
  VEGETARIAN,
  VEGAN,
  COFFEE_SHOP,
  SANDWICH,
  BAKERY,
  ICE_CREAM,
  CREPE,
  BARBECUE,
  CHICKEN,
  STEAK_HOUSE,
  NOODLE,
  RAMEN,
  TAPAS,
  BREAKFAST,
  REGIONAL,
  INTERNATIONAL,
  CHRISTIAN,
  MUSLIM,
  JEWISH,
  BUDDHIST,
  HINDU,
  SIKH,
  SHINTO,
  TAOIST,
  BAHAI,
  JAIN,
  ZOROASTRIAN,
  PAGAN,
  MULTIFAITH,
  CATHOLIC,
  ROMAN_CATHOLIC,
  PROTESTANT,
  ORTHODOX,
  GREEK_ORTHODOX,
  RUSSIAN_ORTHODOX,
  COPTIC_ORTHODOX,
  ARMENIAN_APOSTOLIC,
  LUTHERAN,
  ANGLICAN,
  BAPTIST,
  METHODIST,
  EVANGELICAL,
  PRESBYTERIAN,
  PENTECOSTAL,
  REFORMED,
  ADVENTIST,
  MORMON,
  JEHOVAHS_WITNESS,
  SUNNI,
  SHIA,
}

/** Les valeurs de complément que la table nomme, par leur valeur OpenStreetMap. */
private val DETAIL_KEYS: Map<String, PoiDetailKey> = mapOf(
  "italian" to PoiDetailKey.ITALIAN,
  "french" to PoiDetailKey.FRENCH,
  "pizza" to PoiDetailKey.PIZZA,
  "burger" to PoiDetailKey.BURGER,
  "kebab" to PoiDetailKey.KEBAB,
  "sushi" to PoiDetailKey.SUSHI,
  "japanese" to PoiDetailKey.JAPANESE,
  "chinese" to PoiDetailKey.CHINESE,
  "indian" to PoiDetailKey.INDIAN,
  "thai" to PoiDetailKey.THAI,
  "vietnamese" to PoiDetailKey.VIETNAMESE,
  "mexican" to PoiDetailKey.MEXICAN,
  "greek" to PoiDetailKey.GREEK,
  "lebanese" to PoiDetailKey.LEBANESE,
  "turkish" to PoiDetailKey.TURKISH,
  "spanish" to PoiDetailKey.SPANISH,
  "portuguese" to PoiDetailKey.PORTUGUESE,
  "german" to PoiDetailKey.GERMAN,
  "american" to PoiDetailKey.AMERICAN,
  "georgian" to PoiDetailKey.GEORGIAN,
  "moroccan" to PoiDetailKey.MOROCCAN,
  "korean" to PoiDetailKey.KOREAN,
  "asian" to PoiDetailKey.ASIAN,
  "african" to PoiDetailKey.AFRICAN,
  "seafood" to PoiDetailKey.SEAFOOD,
  "fish" to PoiDetailKey.FISH,
  "fish_and_chips" to PoiDetailKey.FISH_AND_CHIPS,
  "vegetarian" to PoiDetailKey.VEGETARIAN,
  "vegan" to PoiDetailKey.VEGAN,
  "coffee_shop" to PoiDetailKey.COFFEE_SHOP,
  "sandwich" to PoiDetailKey.SANDWICH,
  "bakery" to PoiDetailKey.BAKERY,
  "ice_cream" to PoiDetailKey.ICE_CREAM,
  "crepe" to PoiDetailKey.CREPE,
  "barbecue" to PoiDetailKey.BARBECUE,
  "chicken" to PoiDetailKey.CHICKEN,
  "steak_house" to PoiDetailKey.STEAK_HOUSE,
  "noodle" to PoiDetailKey.NOODLE,
  "ramen" to PoiDetailKey.RAMEN,
  "tapas" to PoiDetailKey.TAPAS,
  "breakfast" to PoiDetailKey.BREAKFAST,
  "regional" to PoiDetailKey.REGIONAL,
  "international" to PoiDetailKey.INTERNATIONAL,
  "christian" to PoiDetailKey.CHRISTIAN,
  "muslim" to PoiDetailKey.MUSLIM,
  "jewish" to PoiDetailKey.JEWISH,
  "buddhist" to PoiDetailKey.BUDDHIST,
  "hindu" to PoiDetailKey.HINDU,
  "sikh" to PoiDetailKey.SIKH,
  "shinto" to PoiDetailKey.SHINTO,
  "taoist" to PoiDetailKey.TAOIST,
  "bahai" to PoiDetailKey.BAHAI,
  "jain" to PoiDetailKey.JAIN,
  "zoroastrian" to PoiDetailKey.ZOROASTRIAN,
  "pagan" to PoiDetailKey.PAGAN,
  "multifaith" to PoiDetailKey.MULTIFAITH,
  "catholic" to PoiDetailKey.CATHOLIC,
  "roman_catholic" to PoiDetailKey.ROMAN_CATHOLIC,
  "protestant" to PoiDetailKey.PROTESTANT,
  "orthodox" to PoiDetailKey.ORTHODOX,
  "greek_orthodox" to PoiDetailKey.GREEK_ORTHODOX,
  "russian_orthodox" to PoiDetailKey.RUSSIAN_ORTHODOX,
  "coptic_orthodox" to PoiDetailKey.COPTIC_ORTHODOX,
  "armenian_apostolic" to PoiDetailKey.ARMENIAN_APOSTOLIC,
  "lutheran" to PoiDetailKey.LUTHERAN,
  "anglican" to PoiDetailKey.ANGLICAN,
  "baptist" to PoiDetailKey.BAPTIST,
  "methodist" to PoiDetailKey.METHODIST,
  "evangelical" to PoiDetailKey.EVANGELICAL,
  "presbyterian" to PoiDetailKey.PRESBYTERIAN,
  "pentecostal" to PoiDetailKey.PENTECOSTAL,
  "reformed" to PoiDetailKey.REFORMED,
  "adventist" to PoiDetailKey.ADVENTIST,
  "mormon" to PoiDetailKey.MORMON,
  "jehovahs_witness" to PoiDetailKey.JEHOVAHS_WITNESS,
  "sunni" to PoiDetailKey.SUNNI,
  "shia" to PoiDetailKey.SHIA,
)

/**
 * Le complément d'une valeur brute de tuile, traduit si la table le nomme.
 *
 * Exposée pour être éprouvée seule : c'est la règle que [poiComplement] applique trois fois.
 */
fun poiDetail(raw: String?): PoiComplement? {
  val value = osmValue(raw) ?: return null
  return DETAIL_KEYS[value]?.let(PoiComplement::Named) ?: PoiComplement.Unnamed(readable(value))
}

/**
 * La valeur de la tuile, telle que la table la nomme : premier terme, sans espace, en
 * minuscules — et **avec ses tirets bas**, qui font partie de la valeur OpenStreetMap.
 *
 * `yes` et `no` ne sont pas des mots : ils disent qu'une chose est là, pas ce qu'elle est.
 */
internal fun osmValue(raw: String?): String? = raw
  ?.substringBefore(';')
  ?.trim()
  ?.lowercase()
  ?.takeIf { it.isNotEmpty() && it != "yes" && it != "no" }

/** « coffee_shop » devient « Coffee shop » : une capitale et des espaces, rien d'inventé. */
private fun readable(value: String): String = value.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Un commerce, nommé par la table ou non, à condition que la vitrine ne soit pas éteinte. */
private fun shopCategory(shop: String?): PoiCategory? {
  val value = normalized(shop)?.takeIf { it !in NOT_A_SHOP } ?: return null
  return typeOf(PoiTag.SHOP, value)?.category ?: PoiCategory.OTHER_SHOPS
}

/** La ligne de la table qui décrit cette valeur, s'il y en a une. */
private fun typeOf(tag: PoiTag, value: String?): PoiType? = normalized(value)?.let { TYPES_BY_TAG[tag]?.get(it) }

/** Les tuiles écrivent parfois une valeur avec des espaces ou une majuscule ; la table, jamais. */
private fun normalized(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
