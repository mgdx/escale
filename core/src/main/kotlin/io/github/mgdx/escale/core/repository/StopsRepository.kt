package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.BoundingBox
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.TransitMode
import io.github.mgdx.escale.core.result.Outcome

/** Arrêts affichés sur la carte (`/api/v6/map/stops`). */
interface StopsRepository {
  /**
   * Arrêts contenus dans [area].
   *
   * L'appelant respecte les règles de sobriété de SPEC.md § 5.7 : aucune requête sous le zoom 11,
   * déclenchement à l'arrêt de la caméra après 300 ms, emprise déjà élargie de 30 %, annulation de
   * la requête en vol dès que la caméra bouge. L'implémentation ajoute un cache par emprise et par
   * palier, valable 10 minutes.
   *
   * @param modes modes retenus pour le palier de zoom courant. Vide : tous les modes.
   * @param grouped laisse le serveur regrouper les quais d'une même gare.
   */
  suspend fun stopsIn(
    area: BoundingBox,
    modes: Set<TransitMode> = emptySet(),
    grouped: Boolean = true,
  ): Outcome<List<Stop>>

  /** Détail d'un arrêt et des lignes qui le desservent (`/api/v6/stop`). */
  suspend fun stop(stopId: String): Outcome<Stop>
}
