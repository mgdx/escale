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
 * « Restaurant · italian », « Lieu de culte · catholic », « Banque · distributeur » : un seul
 * complément, celui qui apprend le plus. La cuisine passe avant la confession, qui passe avant le
 * distributeur, parce qu'un lieu n'en porte qu'un en pratique.
 */
fun poiComplement(
  cuisine: String? = null,
  atm: Boolean = false,
  religion: String? = null,
  denomination: String? = null,
): PoiComplement? = osmWord(cuisine)?.let(PoiComplement::Detail)
  ?: osmWord(denomination)?.let(PoiComplement::Detail)
  ?: osmWord(religion)?.let(PoiComplement::Detail)
  ?: PoiComplement.CashMachine.takeIf { atm }

/** Ce qui complète le type d'un lieu dans la fiche. */
sealed interface PoiComplement {
  /** Une valeur de la tuile, reprise telle quelle : « italian », « catholic ». */
  data class Detail(val value: String) : PoiComplement

  /** La banque distribue des billets (`atm=yes`) : le complément est une chaîne traduite. */
  data object CashMachine : PoiComplement
}

/**
 * Une valeur OpenStreetMap rendue lisible : « italian;pizza » devient « italian », et
 * « fish_and_chips » devient « fish and chips ».
 *
 * `yes` et `no` ne sont pas des mots : ils disent qu'une chose est là, pas ce qu'elle est.
 */
internal fun osmWord(raw: String?): String? = raw
  ?.substringBefore(';')
  ?.trim()
  ?.replace('_', ' ')
  ?.lowercase()
  ?.takeIf { it.isNotEmpty() && it != "yes" && it != "no" }

/** Un commerce, nommé par la table ou non, à condition que la vitrine ne soit pas éteinte. */
private fun shopCategory(shop: String?): PoiCategory? {
  val value = normalized(shop)?.takeIf { it !in NOT_A_SHOP } ?: return null
  return typeOf(PoiTag.SHOP, value)?.category ?: PoiCategory.OTHER_SHOPS
}

/** La ligne de la table qui décrit cette valeur, s'il y en a une. */
private fun typeOf(tag: PoiTag, value: String?): PoiType? = normalized(value)?.let { TYPES_BY_TAG[tag]?.get(it) }

/** Les tuiles écrivent parfois une valeur avec des espaces ou une majuscule ; la table, jamais. */
private fun normalized(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
