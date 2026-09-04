package io.github.mgdx.escale.core.model

/** Choix de thème de l'écran de réglages (SPEC.md § 5.6). */
enum class ThemeChoice {
  SYSTEM,
  LIGHT,
  DARK,
}

/** Format d'affichage de l'heure (SPEC.md § 5.6). */
enum class ClockFormat {
  /** Suit le réglage système de l'appareil. */
  SYSTEM,
  HOURS_12,
  HOURS_24,
}

/**
 * Réglages d'affichage, indépendants des paramètres envoyés au serveur.
 *
 * Les trois bascules de couches sont indépendantes du zoom : elles masquent complètement une
 * catégorie, même quand l'échelle la justifierait (SPEC.md § 5.7). L'ordre des onglets, lui, ne
 * masque rien : il ne fait que les ranger autrement (SPEC.md § 5.6).
 */
data class DisplayPreferences(
  val theme: ThemeChoice = ThemeChoice.SYSTEM,
  val clockFormat: ClockFormat = ClockFormat.SYSTEM,
  /**
   * L'ordre des onglets de l'écran de résultats (SPEC.md § 5.2), réglable au glissé-déposé.
   *
   * Purement local : il ne change que la disposition des onglets et l'onglet ouvert d'emblée,
   * jamais les requêtes envoyées. Toujours complet et sans doublon — [CategoryOrder] s'en charge.
   */
  val categoryOrder: List<JourneyCategory> = CategoryOrder.DEFAULT,
  val showStops: Boolean = true,
  val showRentals: Boolean = true,
  val showPointsOfInterest: Boolean = true,
  /** Faux : les recherches ne sont plus enregistrées dans l'historique (SPEC.md § 5.5). */
  val historyEnabled: Boolean = true,
)
