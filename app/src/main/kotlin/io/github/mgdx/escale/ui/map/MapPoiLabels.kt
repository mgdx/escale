package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiDetailKey
import io.github.mgdx.escale.core.model.PoiTypeKey

/**
 * Le libellé traduit de chaque type de lieu (SPEC.md § 5.7).
 *
 * L'association vit ici et non dans `:core`, qui ne connaît aucune ressource Android. Elle est
 * **exhaustive** : `MapPoiLabelsTest` échoue si une entrée n'a pas sa chaîne, ce qu'un `when`
 * aurait garanti au prix de soixante-dix branches.
 */
internal val POI_TYPE_LABELS: Map<PoiTypeKey, Int> = mapOf(
  PoiTypeKey.TOWNHALL to R.string.map_place_type_townhall,
  PoiTypeKey.COURTHOUSE to R.string.map_place_type_courthouse,
  PoiTypeKey.POST_OFFICE to R.string.map_place_type_post_office,
  PoiTypeKey.POLICE to R.string.map_place_type_police,
  PoiTypeKey.FIRE_STATION to R.string.map_place_type_fire_station,
  PoiTypeKey.COMMUNITY_CENTRE to R.string.map_place_type_community_centre,
  PoiTypeKey.PLACE_OF_WORSHIP to R.string.map_place_type_place_of_worship,
  PoiTypeKey.SCHOOL to R.string.map_place_type_school,
  PoiTypeKey.UNIVERSITY to R.string.map_place_type_university,
  PoiTypeKey.COLLEGE to R.string.map_place_type_college,
  PoiTypeKey.LIBRARY to R.string.map_place_type_library,
  PoiTypeKey.THEATRE to R.string.map_place_type_theatre,
  PoiTypeKey.CINEMA to R.string.map_place_type_cinema,
  PoiTypeKey.ARTS_CENTRE to R.string.map_place_type_arts_centre,
  PoiTypeKey.CASTLE to R.string.map_place_type_castle,
  PoiTypeKey.MONUMENT to R.string.map_place_type_monument,
  PoiTypeKey.MEMORIAL to R.string.map_place_type_memorial,
  PoiTypeKey.FORT to R.string.map_place_type_fort,
  PoiTypeKey.RUINS to R.string.map_place_type_ruins,
  PoiTypeKey.ARCHAEOLOGICAL_SITE to R.string.map_place_type_archaeological_site,
  PoiTypeKey.BATTLEFIELD to R.string.map_place_type_battlefield,
  PoiTypeKey.ARTWORK to R.string.map_place_type_artwork,
  PoiTypeKey.VIEWPOINT to R.string.map_place_type_viewpoint,
  PoiTypeKey.LIGHTHOUSE to R.string.map_place_type_lighthouse,
  PoiTypeKey.HOSPITAL to R.string.map_place_type_hospital,
  PoiTypeKey.CLINIC to R.string.map_place_type_clinic,
  PoiTypeKey.DOCTORS to R.string.map_place_type_doctors,
  PoiTypeKey.DENTIST to R.string.map_place_type_dentist,
  PoiTypeKey.PHARMACY to R.string.map_place_type_pharmacy,
  PoiTypeKey.TOILETS to R.string.map_place_type_toilets,
  PoiTypeKey.SUPERMARKET to R.string.map_place_type_supermarket,
  PoiTypeKey.CONVENIENCE to R.string.map_place_type_convenience,
  PoiTypeKey.BAKERY to R.string.map_place_type_bakery,
  PoiTypeKey.BUTCHER to R.string.map_place_type_butcher,
  PoiTypeKey.GREENGROCER to R.string.map_place_type_greengrocer,
  PoiTypeKey.ALCOHOL to R.string.map_place_type_alcohol,
  PoiTypeKey.BEVERAGES to R.string.map_place_type_beverages,
  PoiTypeKey.KIOSK to R.string.map_place_type_kiosk,
  PoiTypeKey.GENERAL to R.string.map_place_type_general,
  PoiTypeKey.DEPARTMENT_STORE to R.string.map_place_type_department_store,
  PoiTypeKey.MALL to R.string.map_place_type_mall,
  PoiTypeKey.MARKETPLACE to R.string.map_place_type_marketplace,
  PoiTypeKey.RESTAURANT to R.string.map_place_type_restaurant,
  PoiTypeKey.FAST_FOOD to R.string.map_place_type_fast_food,
  PoiTypeKey.CAFE to R.string.map_place_type_cafe,
  PoiTypeKey.PUB to R.string.map_place_type_pub,
  PoiTypeKey.BAR to R.string.map_place_type_bar,
  PoiTypeKey.BIERGARTEN to R.string.map_place_type_biergarten,
  PoiTypeKey.VETERINARY to R.string.map_place_type_veterinary,
  PoiTypeKey.CHEMIST to R.string.map_place_type_chemist,
  PoiTypeKey.OPTICIAN to R.string.map_place_type_optician,
  PoiTypeKey.BANK to R.string.map_place_type_bank,
  PoiTypeKey.ATM to R.string.map_place_type_atm,
  PoiTypeKey.HOTEL to R.string.map_place_type_hotel,
  PoiTypeKey.MOTEL to R.string.map_place_type_motel,
  PoiTypeKey.HOSTEL to R.string.map_place_type_hostel,
  PoiTypeKey.GUEST_HOUSE to R.string.map_place_type_guest_house,
  PoiTypeKey.BED_AND_BREAKFAST to R.string.map_place_type_bed_and_breakfast,
  PoiTypeKey.CAMP_SITE to R.string.map_place_type_camp_site,
  PoiTypeKey.DRINKING_WATER to R.string.map_place_type_drinking_water,
  PoiTypeKey.POST_BOX to R.string.map_place_type_post_box,
  PoiTypeKey.TELEPHONE to R.string.map_place_type_telephone,
  PoiTypeKey.CAR_RENTAL to R.string.map_place_type_car_rental,
  PoiTypeKey.CAR_SHARING to R.string.map_place_type_car_sharing,
  PoiTypeKey.BICYCLE_RENTAL to R.string.map_place_type_bicycle_rental,
  PoiTypeKey.VENDING_MACHINE to R.string.map_place_type_vending_machine,
  PoiTypeKey.HAIRDRESSER to R.string.map_place_type_hairdresser,
  PoiTypeKey.LAUNDRY to R.string.map_place_type_laundry,
  PoiTypeKey.DRY_CLEANING to R.string.map_place_type_dry_cleaning,
  PoiTypeKey.TRAVEL_AGENCY to R.string.map_place_type_travel_agency,
)

/**
 * Le libellé traduit de chacune des douze catégories.
 *
 * Il sert deux fois : à nommer une bascule dans l'écran « Couches de la carte », et à nommer un
 * lieu dont la tuile ne porte ni nom ni valeur connue. Une seule table, donc, et pas deux
 * traductions à tenir d'accord.
 */
private val POI_CATEGORY_LABELS: Map<PoiCategory, Int> = mapOf(
  PoiCategory.PUBLIC_SERVICES to R.string.map_place_category_public_services,
  PoiCategory.EDUCATION to R.string.map_place_category_education,
  PoiCategory.CULTURE to R.string.map_place_category_culture,
  PoiCategory.HEALTHCARE to R.string.map_place_category_healthcare,
  PoiCategory.TOILETS to R.string.map_place_category_toilets,
  PoiCategory.FOOD to R.string.map_place_category_food,
  PoiCategory.DINING to R.string.map_place_category_dining,
  PoiCategory.HEALTH_SHOPS to R.string.map_place_category_health_shops,
  PoiCategory.MONEY to R.string.map_place_category_money,
  PoiCategory.LODGING to R.string.map_place_category_lodging,
  PoiCategory.EVERYDAY_SERVICES to R.string.map_place_category_everyday_services,
  PoiCategory.OTHER_SHOPS to R.string.map_place_category_other_shops,
)

/**
 * Le libellé d'une catégorie, pour l'écran des couches comme pour la fiche.
 *
 * La table est fermée et l'énumération vient de `:core` : il n'y a pas de catégorie sans
 * libellé, et `MapPoiLabelsTest` le vérifie plutôt que de laisser un `!!` en décider.
 */
internal fun poiCategoryLabel(category: PoiCategory): Int = POI_CATEGORY_LABELS.getValue(category)

/**
 * Le libellé traduit de chaque valeur de `cuisine`, `religion` ou `denomination`.
 *
 * Exhaustive, comme les deux tables ci-dessus, et vérifiée par le même cas d'essai.
 */
private val POI_DETAIL_LABELS: Map<PoiDetailKey, Int> = mapOf(
  PoiDetailKey.ITALIAN to R.string.map_place_detail_italian,
  PoiDetailKey.FRENCH to R.string.map_place_detail_french,
  PoiDetailKey.PIZZA to R.string.map_place_detail_pizza,
  PoiDetailKey.BURGER to R.string.map_place_detail_burger,
  PoiDetailKey.KEBAB to R.string.map_place_detail_kebab,
  PoiDetailKey.SUSHI to R.string.map_place_detail_sushi,
  PoiDetailKey.JAPANESE to R.string.map_place_detail_japanese,
  PoiDetailKey.CHINESE to R.string.map_place_detail_chinese,
  PoiDetailKey.INDIAN to R.string.map_place_detail_indian,
  PoiDetailKey.THAI to R.string.map_place_detail_thai,
  PoiDetailKey.VIETNAMESE to R.string.map_place_detail_vietnamese,
  PoiDetailKey.MEXICAN to R.string.map_place_detail_mexican,
  PoiDetailKey.GREEK to R.string.map_place_detail_greek,
  PoiDetailKey.LEBANESE to R.string.map_place_detail_lebanese,
  PoiDetailKey.TURKISH to R.string.map_place_detail_turkish,
  PoiDetailKey.SPANISH to R.string.map_place_detail_spanish,
  PoiDetailKey.PORTUGUESE to R.string.map_place_detail_portuguese,
  PoiDetailKey.GERMAN to R.string.map_place_detail_german,
  PoiDetailKey.AMERICAN to R.string.map_place_detail_american,
  PoiDetailKey.GEORGIAN to R.string.map_place_detail_georgian,
  PoiDetailKey.MOROCCAN to R.string.map_place_detail_moroccan,
  PoiDetailKey.KOREAN to R.string.map_place_detail_korean,
  PoiDetailKey.ASIAN to R.string.map_place_detail_asian,
  PoiDetailKey.AFRICAN to R.string.map_place_detail_african,
  PoiDetailKey.SEAFOOD to R.string.map_place_detail_seafood,
  PoiDetailKey.FISH to R.string.map_place_detail_fish,
  PoiDetailKey.FISH_AND_CHIPS to R.string.map_place_detail_fish_and_chips,
  PoiDetailKey.VEGETARIAN to R.string.map_place_detail_vegetarian,
  PoiDetailKey.VEGAN to R.string.map_place_detail_vegan,
  PoiDetailKey.COFFEE_SHOP to R.string.map_place_detail_coffee_shop,
  PoiDetailKey.SANDWICH to R.string.map_place_detail_sandwich,
  PoiDetailKey.BAKERY to R.string.map_place_detail_bakery,
  PoiDetailKey.ICE_CREAM to R.string.map_place_detail_ice_cream,
  PoiDetailKey.CREPE to R.string.map_place_detail_crepe,
  PoiDetailKey.BARBECUE to R.string.map_place_detail_barbecue,
  PoiDetailKey.CHICKEN to R.string.map_place_detail_chicken,
  PoiDetailKey.STEAK_HOUSE to R.string.map_place_detail_steak_house,
  PoiDetailKey.NOODLE to R.string.map_place_detail_noodle,
  PoiDetailKey.RAMEN to R.string.map_place_detail_ramen,
  PoiDetailKey.TAPAS to R.string.map_place_detail_tapas,
  PoiDetailKey.BREAKFAST to R.string.map_place_detail_breakfast,
  PoiDetailKey.REGIONAL to R.string.map_place_detail_regional,
  PoiDetailKey.INTERNATIONAL to R.string.map_place_detail_international,
  PoiDetailKey.CHRISTIAN to R.string.map_place_detail_christian,
  PoiDetailKey.MUSLIM to R.string.map_place_detail_muslim,
  PoiDetailKey.JEWISH to R.string.map_place_detail_jewish,
  PoiDetailKey.BUDDHIST to R.string.map_place_detail_buddhist,
  PoiDetailKey.HINDU to R.string.map_place_detail_hindu,
  PoiDetailKey.SIKH to R.string.map_place_detail_sikh,
  PoiDetailKey.SHINTO to R.string.map_place_detail_shinto,
  PoiDetailKey.TAOIST to R.string.map_place_detail_taoist,
  PoiDetailKey.BAHAI to R.string.map_place_detail_bahai,
  PoiDetailKey.JAIN to R.string.map_place_detail_jain,
  PoiDetailKey.ZOROASTRIAN to R.string.map_place_detail_zoroastrian,
  PoiDetailKey.PAGAN to R.string.map_place_detail_pagan,
  PoiDetailKey.MULTIFAITH to R.string.map_place_detail_multifaith,
  PoiDetailKey.CATHOLIC to R.string.map_place_detail_catholic,
  PoiDetailKey.ROMAN_CATHOLIC to R.string.map_place_detail_roman_catholic,
  PoiDetailKey.PROTESTANT to R.string.map_place_detail_protestant,
  PoiDetailKey.ORTHODOX to R.string.map_place_detail_orthodox,
  PoiDetailKey.GREEK_ORTHODOX to R.string.map_place_detail_greek_orthodox,
  PoiDetailKey.RUSSIAN_ORTHODOX to R.string.map_place_detail_russian_orthodox,
  PoiDetailKey.COPTIC_ORTHODOX to R.string.map_place_detail_coptic_orthodox,
  PoiDetailKey.ARMENIAN_APOSTOLIC to R.string.map_place_detail_armenian_apostolic,
  PoiDetailKey.LUTHERAN to R.string.map_place_detail_lutheran,
  PoiDetailKey.ANGLICAN to R.string.map_place_detail_anglican,
  PoiDetailKey.BAPTIST to R.string.map_place_detail_baptist,
  PoiDetailKey.METHODIST to R.string.map_place_detail_methodist,
  PoiDetailKey.EVANGELICAL to R.string.map_place_detail_evangelical,
  PoiDetailKey.PRESBYTERIAN to R.string.map_place_detail_presbyterian,
  PoiDetailKey.PENTECOSTAL to R.string.map_place_detail_pentecostal,
  PoiDetailKey.REFORMED to R.string.map_place_detail_reformed,
  PoiDetailKey.ADVENTIST to R.string.map_place_detail_adventist,
  PoiDetailKey.MORMON to R.string.map_place_detail_mormon,
  PoiDetailKey.JEHOVAHS_WITNESS to R.string.map_place_detail_jehovahs_witness,
  PoiDetailKey.SUNNI to R.string.map_place_detail_sunni,
  PoiDetailKey.SHIA to R.string.map_place_detail_shia,
)

/** Le libellé d'un complément nommé par la table. */
internal fun poiDetailLabel(key: PoiDetailKey): Int = POI_DETAIL_LABELS.getValue(key)
