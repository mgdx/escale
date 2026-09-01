package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.result.Outcome

/** Autocomplétion et géocodage inverse (`/api/v1/geocode`, `/api/v1/reverse-geocode`). */
interface GeocodeRepository {
  /**
   * Autocomplétion sur [text].
   *
   * Appel unitaire : c'est [autocompleteStream] qui applique les règles de sobriété de
   * SPEC.md § 7.1 — anti-rebond de 350 ms, minimum de trois caractères, annulation de la requête
   * précédente — et c'est lui qu'un champ de saisie branche. Cette fonction refuse malgré tout les
   * saisies de moins de trois caractères, en rendant une liste vide sans rien demander au serveur.
   * Les résultats sont mis en cache sur disque 24 h (SPEC.md § 7.5).
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

  /**
   * Vide le cache disque de géocodage.
   *
   * Appelée quand l'usager change de serveur (SPEC.md § 5.6.1) : les résultats d'un serveur n'ont
   * aucune valeur pour un autre. C'est aussi la seule façon d'effacer les lieux recherchés, qui
   * sont des données personnelles (SPEC.md § 11).
   */
  suspend fun clearGeocodeCache(): Outcome<Unit>

  companion object {
    /** `numResults=10`, valeur fixée par SPEC.md § 5.1. */
    const val DEFAULT_RESULT_COUNT = 10
  }
}
