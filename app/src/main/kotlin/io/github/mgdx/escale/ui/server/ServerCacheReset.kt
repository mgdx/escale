package io.github.mgdx.escale.ui.server

import io.github.mgdx.escale.core.result.Outcome

/**
 * Le vidage des caches lors d'un changement de serveur (SPEC.md § 5.6.1).
 *
 * Le § 5.6.1 énumère exactement ce qui doit disparaître et ce qui doit survivre : « les caches de
 * résultats, de géocodage et de tuiles sont vidés ; les favoris et l'historique sont conservés ».
 * Cette classe ne connaît donc que les trois caches. **Elle n'a aucun moyen d'atteindre les favoris
 * ni l'historique, et ce n'est pas un oubli** : le jour où ils existeront, rien ici ne les touchera.
 *
 * Les trois purges arrivent en paramètres plutôt qu'en dépendances directes pour la même raison que
 * `TileCacheCleaner` du côté des réglages : l'écran « Serveur MOTIS » n'a pas à connaître MapLibre,
 * et le `ViewModel` reste assemblable et testable sans carte ni disque.
 *
 * **Politique en cas d'échec**, une fois pour toutes (SPEC.md § 8) :
 * - les trois purges sont tentées **indépendamment** ; l'échec de l'une n'empêche pas les autres ;
 * - **aucune n'annule ni ne bloque le changement de serveur**, qui est déjà enregistré quand on
 *   arrive ici. Refuser une bascule parce qu'un fichier de cache résiste serait un remède pire que
 *   le mal ;
 * - rien n'est affiché et **rien n'est journalisé** : ces caches contiennent des lieux cherchés par
 *   l'usager (SPEC.md § 11), et il n'existe de toute façon aucune action utile à lui proposer ;
 * - le pire résidu possible est borné et sans danger : le cache de résultats est indexé par serveur,
 *   si bien qu'un résultat d'une autre instance ne peut pas ressortir ; le cache de géocodage expire
 *   de lui-même en 24 h ; les tuiles ne sont qu'une image. Les trois restent effaçables à la main
 *   depuis « Réglages → Données ».
 */
class ServerCacheReset(
  private val resultsCache: suspend () -> Outcome<Unit>,
  private val geocodeCache: suspend () -> Outcome<Unit>,
  private val tileCache: suspend () -> Boolean,
) {

  /**
   * Vide les trois caches, du moins coûteux au plus coûteux.
   *
   * Les valeurs rendues sont délibérément ignorées : voir la politique décrite sur la classe. Elles
   * ne sont pas non plus remontées à l'appelant, pour qu'aucun code d'écran ne soit tenté d'en
   * faire un message.
   */
  suspend fun clearAll() {
    resultsCache()
    geocodeCache()
    tileCache()
  }
}
