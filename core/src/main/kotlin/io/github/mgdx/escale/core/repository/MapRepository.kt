package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.geo.MapCamera
import io.github.mgdx.escale.core.result.Outcome

/**
 * Ce que le serveur MOTIS sait dire de sa propre carte (`/api/v1/map/initial`).
 *
 * Dernier recours du cadrage initial de SPEC.md § 5.1 : la caméra mémorisée d'abord, la position
 * déjà connue de l'usager ensuite, et seulement à défaut le cadrage que le serveur propose sur sa
 * zone de données. Un serveur qui ne répond pas n'empêche pas la carte de s'afficher : l'appelant
 * retombe sur un cadrage planétaire.
 */
interface MapRepository {
  /** Centre et zoom de la zone couverte par le serveur courant. */
  suspend fun initialCamera(): Outcome<MapCamera>
}
