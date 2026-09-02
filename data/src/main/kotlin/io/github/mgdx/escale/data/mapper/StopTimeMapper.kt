package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.format.HexColor
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.StopTimeEntry
import io.github.mgdx.escale.core.model.StopTimePage
import io.github.mgdx.escale.data.dto.StopTimeDto
import io.github.mgdx.escale.data.dto.StopTimePlaceDto
import io.github.mgdx.escale.data.dto.StopTimesResponseDto
import java.time.Instant

// `StopTime` -> StopTimeEntry, et la réponse entière -> StopTimePage (SPEC.md § 5.4).
//
// Deux principes, les mêmes que pour `JourneyMapper` :
// - l'heure théorique ET l'heure effective sont conservées, seule façon de calculer un retard, et
//   `realTime = false` est reporté tel quel pour que l'interface s'abstienne d'annoncer « à
//   l'heure » une entrée dont personne ne connaît l'avance ou le retard (SPEC.md § 5.2) ;
// - une entrée dont le mode est inconnu n'est jamais perdue : `transitModeOf` la replie sur
//   `OTHER`, plutôt que de faire échouer toute la réponse.

/**
 * Une page de prochains départs.
 *
 * @param arriveBy vrai quand la requête demandait des arrivées : c'est ce qui décide, pour chaque
 *   entrée, laquelle des deux heures du schéma `Place` est celle que l'usager attend.
 */
internal fun StopTimesResponseDto.toDomain(arriveBy: Boolean): StopTimePage = StopTimePage(
  entries = stopTimes.map { it.toDomain(arriveBy) },
  stop = place?.toStop(),
  previousPageCursor = previousPageCursor.trimToNull(),
  nextPageCursor = nextPageCursor.trimToNull(),
)

/**
 * Un départ (ou une arrivée) à un arrêt.
 *
 * Le `tripId` peut manquer, sur une course ajoutée en temps réel : l'entrée reste affichée — un
 * départ qu'on voit au tableau ne doit pas disparaître de l'écran — mais elle ne mène alors à
 * aucune desserte, et l'interface n'en fait pas une ligne cliquable.
 */
internal fun StopTimeDto.toDomain(arriveBy: Boolean): StopTimeEntry {
  val scheduledRaw = if (arriveBy) {
    place.scheduledArrival ?: place.scheduledDeparture
  } else {
    place.scheduledDeparture ?: place.scheduledArrival
  }
  val actualRaw = if (arriveBy) place.arrival ?: place.departure else place.departure ?: place.arrival
  val scheduled = instantOrNull(scheduledRaw) ?: instantOrNull(actualRaw) ?: Instant.EPOCH
  return StopTimeEntry(
    tripId = tripId.trimToNull().orEmpty(),
    mode = transitModeOf(mode),
    // Le serveur arbitre déjà entre numéro court et nom long dans `displayName` ; les deux autres
    // ne servent que de repli, exactement comme sur une portion de trajet.
    lineName = displayName.trimToNull() ?: routeShortName.trimToNull() ?: routeLongName.trimToNull().orEmpty(),
    headsign = headsign.trimToNull().orEmpty(),
    agencyName = agencyName.trimToNull().orEmpty(),
    // Le quai temps réel prime sur celui de la base horaire, qui sert de repli.
    track = place.track.trimToNull() ?: place.scheduledTrack.trimToNull(),
    scheduledTime = scheduled,
    time = instantOrNull(actualRaw) ?: scheduled,
    realTime = realTime,
    // Le passage est supprimé dès que la course entière l'est : le serveur remplit déjà les deux
    // champs ainsi, on ne s'en remet pas à lui pour une information qui barre une ligne à l'écran.
    cancelled = cancelled || tripCancelled,
    tripCancelled = tripCancelled,
    routeColor = HexColor.normalize(routeColor),
    routeTextColor = HexColor.normalize(routeTextColor) ?: HexColor.readableTextOn(routeColor),
    alerts = place.alerts.map { it.toDomain() },
  )
}

/**
 * L'arrêt interrogé, tel que le serveur le décrit en tête de réponse.
 *
 * Ses `modes` sont des **feuilles** — le serveur a développé ses parapluies (docs/architecture.md
 * § 11.5) — et c'est d'eux que l'écran tire ses puces de filtre. Nul quand la réponse décrit un
 * point sans identifiant : il ne mènerait à aucune requête suivante.
 */
private fun StopTimePlaceDto.toStop(): Stop? {
  val id = stopId.trimToNull() ?: return null
  return Stop(
    id = id,
    name = placeName(name),
    coordinates = LatLon(lat = lat, lon = lon),
    modes = modes.map(::transitModeOf).distinct(),
  )
}
