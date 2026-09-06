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
 * Les bascules de couches sont indépendantes du zoom : elles masquent complètement une catégorie,
 * même quand l'échelle la justifierait (SPEC.md § 5.7). L'ordre des onglets, lui, ne masque rien :
 * il ne fait que les ranger autrement (SPEC.md § 5.6).
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
  /**
   * Les catégories de points d'intérêt visibles sur la carte (SPEC.md § 5.6 et § 5.7).
   *
   * Douze bascules indépendantes, réglées une par une dans l'écran « Couches de la carte » : les
   * quatre catégories de repères et les toilettes publiques sont là au premier lancement, les sept
   * autres attendent d'être demandées. Chacune commande **une** couche de la feuille de style,
   * qu'on allume ou qu'on éteint sans jamais l'ajouter ni la retirer (SPEC.md § 5.7, règle 8).
   *
   * Ce champ remplace le `showPointsOfInterest` des versions antérieures ; sa reprise est décrite
   * dans `PreferencesRepositoryImpl`.
   */
  val visiblePoiCategories: Set<PoiCategory> = PoiCategory.DEFAULT_VISIBLE,
  /** Faux : les recherches ne sont plus enregistrées dans l'historique (SPEC.md § 5.5). */
  val historyEnabled: Boolean = true,
)
