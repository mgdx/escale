package io.github.mgdx.escale.core.follow

/**
 * Où en est l'usager dans son trajet, à un instant donné (SPEC.md § 5.3.1).
 *
 * C'est ce que la notification permanente et le bandeau de l'écran de détail mettent en mots. Le
 * calcul qui y mène est dans [FollowTimeline] ; ici, seulement ce qu'il y a à dire.
 */
sealed interface FollowState {

  /** Avant le départ : la première portion n'a pas commencé. */
  data class Waiting(
    val first: FollowLeg,
    /** La première portion en transport en commun, à annoncer avec son heure et son quai. */
    val firstTransit: FollowLeg.Transit?,
  ) : FollowState

  /** À bord d'un véhicule. */
  data class OnBoard(
    /** La position de la portion dans le trajet, pour la mettre en évidence à l'écran. */
    val legIndex: Int,
    val leg: FollowLeg.Transit,
    /**
     * Le prochain arrêt intermédiaire desservi, par sa position dans [FollowLeg.Transit.stops] ;
     * `null` quand le prochain arrêt est la descente.
     */
    val nextStopIndex: Int?,
    /** Les arrêts encore à atteindre, descente comprise, arrêts supprimés exclus. */
    val stopsRemaining: Int,
  ) : FollowState {
    val nextStopName: String
      get() = nextStopIndex?.let { leg.stops[it].name } ?: leg.toName
  }

  /**
   * Entre deux véhicules : un cheminement, ou l'attente sur place de la prochaine correspondance.
   *
   * [street] est la portion de rue en cours, `null` quand la correspondance se fait sans en
   * parcourir une. [nextTransit] est le prochain véhicule à prendre, `null` quand le trajet
   * s'achève par ce cheminement.
   */
  data class Connecting(val streetIndex: Int?, val street: FollowLeg.Street?, val nextTransit: FollowLeg.Transit?) :
    FollowState

  /** Le trajet est terminé : le suivi s'arrête de lui-même. */
  data object Arrived : FollowState
}

/**
 * Une alerte : sonore et vibrante, aux seuls moments qui demandent un geste (SPEC.md § 5.3.1).
 *
 * [priority] départage deux alertes qui tomberaient au même instant : une seule est émise, la plus
 * pressante. C'est la « fusion » que la spec demande pour les deux alertes de descente.
 */
sealed interface FollowAlert {
  val priority: Int

  /** « Descente dans 3 arrêts ». */
  data class StopsBefore(val stops: Int, val stopName: String) : FollowAlert {
    override val priority: Int get() = PRIORITY_STOPS_BEFORE
  }

  /** « Descente au prochain arrêt ». */
  data class NextStop(val stopName: String) : FollowAlert {
    override val priority: Int get() = PRIORITY_NEXT_STOP
  }

  /** « Descendez ici », suivi de ce qui vient ensuite. */
  data class Alight(val stopName: String, val then: FollowState.Connecting) : FollowAlert {
    override val priority: Int get() = PRIORITY_ALIGHT
  }

  /** « Arrivée à destination ». */
  data class Arrived(val destinationName: String) : FollowAlert {
    override val priority: Int get() = PRIORITY_ARRIVED
  }

  /** Un rafraîchissement a annulé une portion du trajet suivi. */
  data object LegCancelled : FollowAlert {
    override val priority: Int get() = PRIORITY_ARRIVED
  }

  companion object {
    private const val PRIORITY_STOPS_BEFORE = 1
    private const val PRIORITY_NEXT_STOP = 2
    private const val PRIORITY_ALIGHT = 3
    private const val PRIORITY_ARRIVED = 4
  }
}
