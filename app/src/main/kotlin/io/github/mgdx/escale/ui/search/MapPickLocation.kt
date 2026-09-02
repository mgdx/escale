package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.format.formatCoordinates
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.ui.map.MapPick

/**
 * Le point choisi par appui long sur la carte, tel qu'il entre dans la recherche (SPEC.md § 5.1).
 *
 * **Le libellé rendu par le géocodage inverse est repris intact** : son `id` et son `kind` sont
 * exactement ce que docs/architecture.md § 11.3 exige de transmettre à `plan`. Un appui long qui
 * tombe sur une gare rend un [Location] de type `STOP` avec son `stopId`, et c'est cet identifiant,
 * et non les coordonnées du doigt, qui rendra cinq itinéraires là où les coordonnées en rendaient
 * zéro. Le reconstruire ici serait précisément le contournement que ce paragraphe interdit.
 *
 * Sans libellé — le serveur ne connaît rien à cet endroit, ou n'a pas répondu — le point reste
 * utilisable : il prend ses coordonnées pour nom.
 */
fun MapPick.toLocation(): Location = label ?: point.asUnnamedLocation()

/** Un point sans nom, mais parfaitement utilisable : il s'affiche par ses coordonnées. */
fun LatLon.asUnnamedLocation(): Location = Location(
  id = null,
  name = formatCoordinates(this),
  description = null,
  coordinates = this,
  kind = PlaceKind.ADDRESS,
)
