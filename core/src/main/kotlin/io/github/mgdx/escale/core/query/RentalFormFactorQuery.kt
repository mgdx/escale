package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalFormFactorSelection

/**
 * **Le seul endroit du projet qui nomme le paramètre de types de véhicules partagés.**
 *
 * `directRentalFormFactors` est marqué « Experimental » par l'OpenAPI MOTIS : son nom comme son
 * comportement peuvent changer sans changement de version d'API. SPEC.md § 5.2 impose donc de
 * l'isoler dans une fonction unique, pour n'avoir qu'un endroit à corriger le jour où MOTIS le
 * renomme.
 *
 * Ses deux jumeaux `preTransitRentalFormFactors` et `postTransitRentalFormFactors` ne sont plus
 * envoyés : l'onglet Transport en commun n'emprunte aucun véhicule partagé en premier ou dernier
 * kilomètre (SPEC.md § 5.2), sa requête ne demande donc que la marche.
 *
 * Ne pas disperser cette chaîne ailleurs, ni la recopier dans un test : les tests passent par
 * [DIRECT] pour rester justes après un renommage côté serveur.
 */
object RentalFormFactorQuery {

  /** Filtre des véhicules empruntés en trajet direct (onglet Vélo). */
  const val DIRECT = "directRentalFormFactors"

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

    // L'onglet Transport en commun ne fait que marche et courses ; les onglets Voiture et À pied
    // n'empruntent aucun véhicule partagé (SPEC.md § 5.2).
    JourneyCategory.TRANSIT, JourneyCategory.CAR, JourneyCategory.WALK -> emptyMap()
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

  /**
   * Le réglage d'usager restreint la liste offerte : celui qui refuse les trottinettes ne doit pas
   * en trouver ici (SPEC.md § 5.2).
   */
  private fun bikeTabFormFactors(allowed: Set<RentalFormFactor>): List<RentalFormFactor> {
    val offered = RentalFormFactorSelection.OFFERED
    return if (allowed.isEmpty()) offered else offered.filter { it in allowed }
  }

  private fun join(formFactors: List<RentalFormFactor>): String = formFactors.joinToString(",") { it.name }
}
