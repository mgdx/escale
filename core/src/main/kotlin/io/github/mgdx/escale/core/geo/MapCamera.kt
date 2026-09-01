package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.LatLon

/**
 * Un cadrage de carte : un centre et un niveau de zoom.
 *
 * Sert aux trois sources de cadrage initial de SPEC.md § 5.1 — la caméra mémorisée du dernier
 * lancement, la position déjà connue de l'usager, et le cadrage rendu par `/api/v1/map/initial`.
 * Le zoom est le zoom MapLibre (entier ou fractionnaire, 0 = planète entière).
 */
data class MapCamera(val center: LatLon, val zoom: Double)
