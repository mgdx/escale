package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.result.Outcome

/** Autocomplétion et géocodage inverse (`/api/v1/geocode`, `/api/v1/reverse-geocode`). */
interface GeocodeRepository {
  /**
   * Autocomplétion sur [text].
   *
   * L'anti-rebond de 350 ms, le minimum de trois caractères et l'annulation de la requête
   * précédente (SPEC.md § 7.1) sont du ressort de l'appelant : ils dépendent de la frappe, pas du
   * dépôt. Les résultats sont mis en cache sur disque 24 h (SPEC.md § 7.5).
   *
   * @param bias centre de la carte, pour privilégier les résultats proches. Nul : aucun biais.
   * @param language code de langue de l'interface, transmis tel quel au serveur.
   */
  suspend fun autocomplete(
    text: String,
    bias: LatLon? = null,
    language: String? = null,
    limit: Int = DEFAULT_RESULT_COUNT,
  ): Outcome<List<Location>>

  /**
   * Libellé lisible d'une position, pour l'utiliser comme point de départ (SPEC.md § 5.1).
   * Rend `null` quand le serveur ne connaît rien à cet endroit.
   */
  suspend fun reverseGeocode(point: LatLon, language: String? = null): Outcome<Location?>

  companion object {
    /** `numResults=10`, valeur fixée par SPEC.md § 5.1. */
    const val DEFAULT_RESULT_COUNT = 10
  }
}
