package io.github.mgdx.escale.data.mapper

import io.github.mgdx.escale.core.format.HexColor
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Stop
import io.github.mgdx.escale.core.model.StopLine
import io.github.mgdx.escale.data.dto.StopInfoDto
import io.github.mgdx.escale.data.dto.StopPlaceDto
import io.github.mgdx.escale.data.dto.StopRouteDto

/**
 * `Place` -> [Stop], ou `null` quand le point n'est pas un arrêt.
 *
 * Un `Place` sans `stopId` ne peut mener nulle part : ni infobulle, ni prochains départs. Le
 * laisser passer donnerait un marqueur qui ne répond pas au doigt.
 */
internal fun StopPlaceDto.toStop(): Stop? {
  val id = stopId?.takeIf { it.isNotBlank() } ?: return null
  return Stop(
    id = id,
    name = name,
    coordinates = LatLon(lat = lat, lon = lon),
    // Un doublon renvoyé par le serveur, ou deux valeurs inconnues repliées sur OTHER, ne doivent
    // pas compter deux fois dans l'infobulle.
    modes = modes.map(::transitModeOf).distinct(),
  )
}

/** `Place[]` -> les arrêts de l'emprise, dans l'ordre rendu par le serveur. */
internal fun List<StopPlaceDto>.toStops(): List<Stop> = mapNotNull { it.toStop() }

/**
 * Le corps de `/api/v6/stop` -> [Stop] et ses lignes.
 *
 * Les lignes sont dédoublonnées et ordonnées : le serveur en rend une par sens et par variante de
 * parcours — trente pour Châtelet, dont une poignée de numéros distincts seulement.
 */
internal fun StopInfoDto.toStop(): Stop? = place.toStop()?.copy(lines = routes.toLines())

private fun List<StopRouteDto>.toLines(): List<StopLine> = map { it.toLine() }
  .distinctBy { it.label to it.mode }
  .sortedWith(compareBy({ it.mode.ordinal }, { it.label.length }, { it.label }))

private fun StopRouteDto.toLine(): StopLine = StopLine(
  id = routeId,
  shortName = routeShortName,
  longName = routeLongName,
  mode = transitModeOf(mode),
  agencyName = agencyName,
  // Le serveur envoie la couleur avec ou sans `#`, sur trois ou six chiffres selon la source GTFS.
  color = HexColor.normalize(routeColor),
  textColor = HexColor.normalize(routeTextColor) ?: HexColor.readableTextOn(routeColor),
)
