package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.JourneyLeg

/**
 * Le libellé de ligne à afficher pour une portion en transport en commun (SPEC.md § 5.2 et § 5.3).
 *
 * MOTIS renvoie deux champs et n'en garantit aucun : `routeShortName` est le numéro que l'usager
 * lit sur le véhicule (« E5 », « 14 »), `displayName` est l'arbitrage du serveur, qui retombe sur
 * le nom long quand il n'y a pas de numéro. Le numéro court passe donc devant, et une chaîne vide
 * — que l'API renvoie couramment — vaut « absent ».
 *
 * Rend `null` quand le serveur ne nomme la ligne d'aucune façon : l'interface affiche alors le seul
 * mode de transport, ce qui reste vrai.
 */
fun transitLineLabel(leg: JourneyLeg.Transit): String? =
  leg.routeShortName?.takeIf(String::isNotBlank) ?: leg.lineName.takeIf(String::isNotBlank)
