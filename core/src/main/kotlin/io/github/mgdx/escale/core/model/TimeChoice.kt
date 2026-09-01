package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Choix d'heure de la carte de recherche (SPEC.md § 5.1).
 *
 * [Now] n'est pas un [DepartAt] figé à l'instant de la saisie : la requête doit partir avec l'heure
 * du moment où elle est émise, pas de celui où l'usager a tapé sa destination.
 */
sealed interface TimeChoice {
  data object Now : TimeChoice

  data class DepartAt(val instant: Instant) : TimeChoice

  /** Correspond au paramètre `arriveBy=true` de `/api/v6/plan`. */
  data class ArriveBy(val instant: Instant) : TimeChoice
}
