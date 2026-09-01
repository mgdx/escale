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
   * Les paramètres de filtrage à joindre à la requête d'une catégorie.
   *
   * @param allowed réglage d'usager (`SearchPreferences.allowedRentalFormFactors`). Vide signifie
   *   « aucun filtre » et non « aucun véhicule », conformément à l'API.
   * @return une entrée par paramètre, ou une map vide quand le serveur n'a rien à filtrer. Une map
   *   vide **pour l'onglet Vélo** signifie en outre que l'usager a exclu tous les véhicules de cet
   *   onglet : l'appelant retire alors `RENTAL` de `directModes`.
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

  private fun directParameters(allowed: Set<RentalFormFactor>): Map<String, String> {
    val formFactors = bikeTabFormFactors(allowed)
    return if (formFactors.isEmpty()) emptyMap() else mapOf(DIRECT to join(formFactors))
  }

  private fun transitParameters(allowed: Set<RentalFormFactor>): Map<String, String> {
    // Sans réglage, on laisse le serveur proposer tous les véhicules en rabattement : c'est son
    // comportement par défaut, et l'envoyer explicitement n'apporterait rien.
    if (allowed.isEmpty()) return emptyMap()
    val ordered = join(RentalFormFactor.entries.filter { it in allowed })
    return mapOf(PRE_TRANSIT to ordered, POST_TRANSIT to ordered)
  }

  /**
   * Le réglage d'usager s'applique partout, y compris dans l'onglet Vélo : celui qui refuse les
   * trottinettes ne doit pas en trouver ici (SPEC.md § 5.2).
   */
  private fun bikeTabFormFactors(allowed: Set<RentalFormFactor>): List<RentalFormFactor> =
    if (allowed.isEmpty()) BIKE_TAB_FORM_FACTORS else BIKE_TAB_FORM_FACTORS.filter { it in allowed }

  private fun join(formFactors: List<RentalFormFactor>): String = formFactors.joinToString(",") { it.name }
}
