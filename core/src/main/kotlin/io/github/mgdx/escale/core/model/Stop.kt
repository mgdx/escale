package io.github.mgdx.escale.core.model

/**
 * Arrêt affiché sur la carte ou enregistré en favori.
 *
 * Volontairement plus pauvre que [Place] : il ne porte aucune heure, puisqu'il ne s'inscrit dans
 * aucun trajet.
 */
data class Stop(
  val id: String,
  val name: String,
  val coordinates: LatLon,
  /** Modes desservis, tels que renvoyés par `/api/v6/map/stops`. */
  val modes: List<TransitMode> = emptyList(),
  /**
   * Lignes desservant l'arrêt, telles que renvoyées par `/api/v6/stop`.
   *
   * Vide tant que le détail n'a pas été demandé : `/api/v6/map/stops` ne rend que les modes, et
   * demander les lignes de chaque arrêt d'une emprise coûterait une requête par point.
   * L'infobulle de SPEC.md § 5.7 les charge à l'appui, pour le seul arrêt touché.
   */
  val lines: List<StopLine> = emptyList(),
)

/**
 * Une ligne desservant un arrêt, décalque du schéma `Route` de l'OpenAPI MOTIS.
 *
 * C'est ce que l'infobulle de SPEC.md § 5.7 annonce sous le nom de l'arrêt : « le nom et les lignes
 * desservies ».
 */
data class StopLine(
  val id: String,
  /** Numéro lu sur le véhicule (« 14 », « RER A »). Vide quand le réseau ne le publie pas. */
  val shortName: String,
  /** Nom long (« Château de Vincennes - La Défense »). Vide aussi souvent que le précédent. */
  val longName: String,
  val mode: TransitMode,
  val agencyName: String,
  /** Couleur de la ligne, au format `#RRGGBB`. Nulle quand le transporteur n'en publie pas. */
  val color: String? = null,
  /** Couleur du texte à poser sur [color], au format `#RRGGBB`. */
  val textColor: String? = null,
) {
  /**
   * Le libellé à afficher : le numéro court d'abord, le nom long à défaut.
   *
   * Vide quand le réseau ne nomme la ligne d'aucune façon ; l'interface affiche alors le seul mode
   * de transport, ce qui reste vrai (même arbitrage que `transitLineLabel`).
   */
  val label: String get() = shortName.ifBlank { longName }
}
