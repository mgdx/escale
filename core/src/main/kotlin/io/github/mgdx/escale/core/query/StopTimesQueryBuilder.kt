package io.github.mgdx.escale.core.query

import io.github.mgdx.escale.core.model.TransitMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Assemblage des paramètres de `GET /api/v6/stoptimes` et de `GET /api/v6/trip` (SPEC.md § 5.4
 * et § 5.3).
 *
 * Comme [PlanQueryBuilder] et `StopsQueryBuilder`, c'est du Kotlin pur : `:data` ne fait que poser
 * ces couples sur la requête, et la construction se vérifie en JVM (SPEC.md § 10). Les noms
 * viennent un à un de `docs/motis-openapi.yaml`, opérations `stoptimes` (ligne 2785) et `trip`
 * (ligne 2377), et non d'une opération voisine qui les orthographierait autrement — `stoptimes`
 * écrit `mode` au singulier là où `map/stops` écrit `modes`.
 */
object StopTimesQueryBuilder {

  /**
   * Le serveur attend une date-heure ISO-8601. `Instant.toString()` omet les secondes quand elles
   * sont nulles, ce que tous les analyseurs n'acceptent pas : on les écrit toujours. Même motif et
   * même motif de format que [PlanQueryBuilder].
   */
  private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

  /**
   * Les paramètres des prochains départs à un arrêt.
   *
   * @param stopId identifiant de l'arrêt, tel que le serveur l'a rendu. Jamais des coordonnées :
   *   docs/architecture.md § 11.3 en fait une règle du projet, et le point d'entrée ne cherche par
   *   `center` que faute de `stopId`.
   * @param time instant autour duquel chercher. **Ignoré quand [cursor] est fourni**, comme le
   *   veut l'API : une page suivante se demande par son seul curseur, la requête restant par
   *   ailleurs identique (même règle qu'au § 5.2 pour `plan`).
   * @param count valeur de `n`. C'est un **minimum** et non un maximum : le serveur rend tous les
   *   événements de la dernière minute atteinte, souvent une dizaine de plus que demandé.
   * @param modes filtre par mode. Vide : le paramètre est **omis**, ce qui veut dire « tous les
   *   modes de transport en commun » côté serveur — un `mode` vide n'aurait pas le même sens.
   *   Les valeurs sont des **feuilles**, jamais un parapluie
   *   (`io.github.mgdx.escale.core.model.DepartureModeFilter`).
   * @param arriveBy `true` pour lister des arrivées plutôt que des départs.
   * @param cursor `previousPageCursor` ou `nextPageCursor` d'une page déjà obtenue.
   */
  fun stopTimes(
    stopId: String,
    time: Instant?,
    count: Int,
    modes: Set<TransitMode> = emptySet(),
    arriveBy: Boolean = false,
    cursor: String? = null,
  ): Map<String, String> {
    val parameters = LinkedHashMap<String, String>()
    parameters[STOP_ID] = stopId
    parameters[COUNT] = count.toString()
    // SPEC.md § 5.4 le demande explicitement : sans lui, aucun bandeau de perturbation ne serait
    // possible. C'est déjà le défaut du serveur, on ne s'en remet pas à un défaut qui peut changer.
    parameters[WITH_ALERTS] = TRUE
    if (arriveBy) parameters[ARRIVE_BY] = TRUE
    if (modes.isNotEmpty()) parameters[MODE] = modes.joinToString(",") { it.name }
    if (cursor != null) {
      parameters[PAGE_CURSOR] = cursor
    } else {
      if (time != null) parameters[TIME] = TIME_FORMAT.format(time)
      parameters[DIRECTION] = if (arriveBy) EARLIER else LATER
    }
    return parameters
  }

  /**
   * Les paramètres de la desserte complète d'une course.
   *
   * @param detailedLegs `false` par défaut, à rebours du défaut serveur : l'écran « détail de la
   *   course » liste des arrêts, il ne trace rien. La géométrie divise par sept la taille de la
   *   réponse quand on l'omet — 55 ko contre 8 ko sur un ICE Hambourg → Nuremberg — et SPEC.md
   *   § 7.6 demande de ne la réclamer que là où elle sert.
   */
  fun trip(tripId: String, detailedLegs: Boolean = false): Map<String, String> = mapOf(
    TRIP_ID to tripId,
    DETAILED_LEGS to detailedLegs.toString(),
  )

  private const val STOP_ID = "stopId"
  private const val TIME = "time"
  private const val COUNT = "n"
  private const val MODE = "mode"
  private const val ARRIVE_BY = "arriveBy"
  private const val WITH_ALERTS = "withAlerts"
  private const val PAGE_CURSOR = "pageCursor"
  private const val DIRECTION = "direction"
  private const val TRIP_ID = "tripId"
  private const val DETAILED_LEGS = "detailedLegs"
  private const val TRUE = "true"

  /**
   * `EARLIER` rend les événements **avant** `time`, `LATER` ceux d'après.
   *
   * La prose de `docs/motis-openapi.yaml` dit exactement l'inverse (« the next `n` … in case
   * `EARLIER` is selected »), et c'est elle qui se trompe : vérifié sur `api.transitous.org` à
   * Hambourg, `direction=EARLIER` autour de 08:00 rend 07:58 → 08:00 et `direction=LATER` rend
   * 08:00 → 08:01. Les curseurs, eux, sont sans ambiguïté : ils s'appellent `EARLIER|…` et
   * `LATER|…`. Ne pas « corriger » ces deux constantes d'après la documentation.
   */
  private const val EARLIER = "EARLIER"

  private const val LATER = "LATER"
}
