package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.RentalFormFactor

/**
 * **Le seul endroit du projet qui nomme les paramètres de types de véhicules partagés.**
 *
 * `directRentalFormFactors`, `preTransitRentalFormFactors` et `postTransitRentalFormFactors` sont
 * marqués « Experimental » par l'OpenAPI MOTIS : leur nom comme leur comportement peuvent changer
 * sans changement de version d'API. SPEC.md § 5.2 impose donc de les isoler dans une fonction
 * unique, pour n'avoir qu'un endroit à corriger le jour où MOTIS les renomme.
 *
 * Ne pas disperser ces trois chaînes ailleurs, ni les recopier dans un test : les tests passent
 * par [DIRECT], [PRE_TRANSIT] et [POST_TRANSIT] pour rester justes après un renommage côté serveur.
 */
object RentalFormFactorQuery {

  /** Filtre des véhicules empruntés en trajet direct (onglet Vélo). */
  const val DIRECT = "directRentalFormFactors"

  /** Filtre des véhicules empruntés en rabattement vers le premier arrêt. */
  const val PRE_TRANSIT = "preTransitRentalFormFactors"

  /** Filtre des véhicules empruntés depuis le dernier arrêt. */
  const val POST_TRANSIT = "postTransitRentalFormFactors"

  /**
   * Types de véhicules que l'onglet Vélo accepte, dans l'ordre imposé par SPEC.md § 5.2.
   *
   * Une voiture ou un cyclomoteur en libre-service ne relèvent pas de cet onglet : les proposer
   * ferait apparaître un trajet motorisé sous un pictogramme de vélo.
   */
  private val BIKE_TAB_FORM_FACTORS = listOf(
    RentalFormFactor.BICYCLE,
    RentalFormFactor.SCOOTER_STANDING,
    RentalFormFactor.SCOOTER_SEATED,
  )

  /**
   * Types de véhicules que l'onglet Transport en commun accepte en rabattement, dans l'ordre de
   * l'énumération de l'API.
   *
   * **La voiture partagée en est exclue** (SPEC.md § 5.2) : un premier ou dernier kilomètre en
   * voiture n'est pas du transport en commun, et le proposer sous cet onglet reviendrait à ranger
   * un trajet motorisé individuel là où l'usager cherche des courses. Aucun autre onglet ne
   * l'emprunte non plus : elle ne fait donc plus partie des types offerts par les réglages.
   */
  private val TRANSIT_TAB_FORM_FACTORS = RentalFormFactor.entries.filter { it != RentalFormFactor.CAR }

  /**
   * Les paramètres de filtrage à joindre à la requête d'une catégorie.
   *
   * @param allowed réglage d'usager (`SearchPreferences.allowedRentalFormFactors`). Vide signifie
   *   « aucun filtre » et non « aucun véhicule », conformément à l'API.
   * @return une entrée par paramètre, ou une map vide quand le serveur n'a rien à filtrer. Une map
   *   vide **pour l'onglet Vélo ou l'onglet Transport en commun** signifie en outre que l'usager a
   *   exclu tous les véhicules de cet onglet : l'appelant retire alors `RENTAL` des modes.
   */
  fun parameters(category: JourneyCategory, allowed: Set<RentalFormFactor>): Map<String, String> = when (category) {
    JourneyCategory.BIKE -> directParameters(allowed)

    JourneyCategory.TRANSIT -> transitParameters(allowed)

    // Les onglets Voiture et À pied n'empruntent aucun véhicule partagé (SPEC.md § 5.2).
    JourneyCategory.CAR, JourneyCategory.WALK -> emptyMap()
  }

  /**
   * Vrai quand l'onglet Vélo a encore au moins un type de véhicule partagé à proposer. Faux :
   * l'usager les a tous exclus, la requête ne demande plus que le vélo personnel.
   */
  fun bikeTabAcceptsRentals(allowed: Set<RentalFormFactor>): Boolean = bikeTabFormFactors(allowed).isNotEmpty()

  /**
   * Vrai quand l'onglet Transport en commun a encore un véhicule partagé à proposer en rabattement.
   * Faux : l'usager n'a gardé que des types absents de cet onglet, et la requête se limite alors à
   * la marche en premier et dernier kilomètre.
   */
  fun transitTabAcceptsRentals(allowed: Set<RentalFormFactor>): Boolean = transitTabFormFactors(allowed).isNotEmpty()

  private fun directParameters(allowed: Set<RentalFormFactor>): Map<String, String> {
    val formFactors = bikeTabFormFactors(allowed)
    return if (formFactors.isEmpty()) emptyMap() else mapOf(DIRECT to join(formFactors))
  }

  /**
   * Le filtre est ici toujours envoyé, même sans réglage d'usager : le défaut du serveur inclut la
   * voiture partagée, que cet onglet ne veut pas.
   */
  private fun transitParameters(allowed: Set<RentalFormFactor>): Map<String, String> {
    val formFactors = transitTabFormFactors(allowed)
    if (formFactors.isEmpty()) return emptyMap()
    val ordered = join(formFactors)
    return mapOf(PRE_TRANSIT to ordered, POST_TRANSIT to ordered)
  }

  /**
   * Le réglage d'usager s'applique partout, y compris dans l'onglet Vélo : celui qui refuse les
   * trottinettes ne doit pas en trouver ici (SPEC.md § 5.2).
   */
  private fun bikeTabFormFactors(allowed: Set<RentalFormFactor>): List<RentalFormFactor> =
    if (allowed.isEmpty()) BIKE_TAB_FORM_FACTORS else BIKE_TAB_FORM_FACTORS.filter { it in allowed }

  private fun transitTabFormFactors(allowed: Set<RentalFormFactor>): List<RentalFormFactor> =
    if (allowed.isEmpty()) TRANSIT_TAB_FORM_FACTORS else TRANSIT_TAB_FORM_FACTORS.filter { it in allowed }

  private fun join(formFactors: List<RentalFormFactor>): String = formFactors.joinToString(",") { it.name }
}
