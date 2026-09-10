package io.github.mgdx.escale.ui.session

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.TimeChoice
import io.github.mgdx.escale.core.model.TransitMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * La recherche en cours, réduite à une chaîne que `SavedStateHandle` sait conserver.
 *
 * `SearchDraft` porte des types de `:core`, qui ne connaissent ni `Parcelable` ni
 * `kotlinx.serialization` — et c'est très bien ainsi (docs/architecture.md § 1). La conversion vit
 * donc ici, dans `:app`, dans des types de transport privés à ce fichier.
 *
 * Ce n'est pas de la persistance : rien n'est écrit sur le disque. `SavedStateHandle` garde cet
 * état le temps que le système tue puis relance le processus, et il disparaît avec la tâche. Les
 * lieux cherchés ne survivent donc pas à la fermeture de l'application (SPEC.md § 11) ; leur
 * conservation volontaire, c'est l'historique de SPEC.md § 5.5, et lui seul.
 *
 * **L'identifiant d'arrêt et la nature du lieu font partie du voyage** : les perdre reviendrait à
 * relancer, après une rotation, une requête par coordonnées là où docs/architecture.md § 11.3 exige
 * un `stopId`.
 */
@Serializable
private data class SavedLocation(
  val id: String?,
  val name: String,
  val description: String?,
  val lat: Double,
  val lon: Double,
  val kind: String,
  val modes: List<String>,
)

@Serializable
private data class SavedDraft(
  val from: SavedLocation?,
  val to: SavedLocation?,
  val timeMode: String,
  val timeMillis: Long?,
)

private const val TIME_NOW = "now"
private const val TIME_DEPART_AT = "departAt"
private const val TIME_ARRIVE_BY = "arriveBy"

private val json = Json { ignoreUnknownKeys = true }

/** Encode le brouillon pour `SavedStateHandle`. */
fun encodeSearchDraft(draft: SearchDraft): String = json.encodeToString(
  SavedDraft(
    from = draft.from?.toSaved(),
    to = draft.to?.toSaved(),
    timeMode = when (draft.time) {
      TimeChoice.Now -> TIME_NOW
      is TimeChoice.DepartAt -> TIME_DEPART_AT
      is TimeChoice.ArriveBy -> TIME_ARRIVE_BY
    },
    timeMillis = when (val time = draft.time) {
      TimeChoice.Now -> null
      is TimeChoice.DepartAt -> time.instant.toEpochMilli()
      is TimeChoice.ArriveBy -> time.instant.toEpochMilli()
    },
  ),
)

/**
 * Relit un brouillon encodé, ou rend `null` si la chaîne est inexploitable.
 *
 * Un état sauvegardé écrit par une version antérieure de l'application ne doit jamais faire planter
 * la suivante : dans le doute, on repart d'une recherche vide.
 */
fun decodeSearchDraft(encoded: String): SearchDraft? = try {
  json.decodeFromString<SavedDraft>(encoded).toDraft()
} catch (_: SerializationException) {
  null
} catch (_: IllegalArgumentException) {
  null
}

private fun Location.toSaved() = SavedLocation(
  id = id,
  name = name,
  description = description,
  lat = coordinates.lat,
  lon = coordinates.lon,
  kind = kind.name,
  modes = servedModes.map { it.name },
)

private fun SavedDraft.toDraft() = SearchDraft(
  from = from?.toLocation(),
  to = to?.toLocation(),
  time = when (timeMode) {
    TIME_DEPART_AT -> timeMillis?.let { TimeChoice.DepartAt(Instant.ofEpochMilli(it)) } ?: TimeChoice.Now
    TIME_ARRIVE_BY -> timeMillis?.let { TimeChoice.ArriveBy(Instant.ofEpochMilli(it)) } ?: TimeChoice.Now
    else -> TimeChoice.Now
  },
)

private fun SavedLocation.toLocation() = Location(
  id = id,
  name = name,
  description = description,
  coordinates = LatLon(lat, lon),
  // Une valeur inconnue — état écrit par une version qui nommait autrement — ne fait pas échouer la
  // relecture : le lieu retombe sur une adresse, qui part en coordonnées comme avant.
  kind = PlaceKind.entries.firstOrNull { it.name == kind } ?: PlaceKind.ADDRESS,
  servedModes = modes.mapNotNull { name -> TransitMode.entries.firstOrNull { it.name == name } },
)
