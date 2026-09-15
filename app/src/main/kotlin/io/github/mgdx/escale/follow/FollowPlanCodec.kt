package io.github.mgdx.escale.follow

import io.github.mgdx.escale.core.follow.FollowLeg
import io.github.mgdx.escale.core.follow.FollowPlan
import io.github.mgdx.escale.core.follow.FollowStop
import io.github.mgdx.escale.core.follow.StreetKind
import io.github.mgdx.escale.core.model.TransitMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/**
 * Le plan de suivi, réduit à une chaîne que l'intent du service sait transporter.
 *
 * Même dispositif que `SavedSearch` pour le brouillon de recherche : les types de `:core` ne
 * connaissent pas `kotlinx.serialization`, la conversion vit donc ici, dans des types de transport
 * privés à ce fichier. **Ce n'est pas de la persistance** : la chaîne ne va que dans l'intent de
 * démarrage, que le système garde en mémoire le temps de relancer le service s'il tue le
 * processus, et jamais sur le disque (SPEC.md § 5.3.1).
 */
@Serializable
private data class PlanDto(val key: String, val itineraryId: String?, val legs: List<LegDto>)

@Serializable
private data class LegDto(
  val kind: String,
  val start: Long,
  val end: Long,
  val from: String,
  val to: String,
  val cancelled: Boolean,
  val mode: String? = null,
  val line: String? = null,
  val headsign: String? = null,
  val track: String? = null,
  val stops: List<StopDto> = emptyList(),
  val durationSeconds: Long = 0,
)

@Serializable
private data class StopDto(val name: String, val time: Long, val cancelled: Boolean)

private const val KIND_TRANSIT = "transit"

private val json = Json { ignoreUnknownKeys = true }

fun encodeFollowPlan(plan: FollowPlan): String = json.encodeToString(
  PlanDto(
    key = plan.key,
    itineraryId = plan.itineraryId,
    legs = plan.legs.map(FollowLeg::toDto),
  ),
)

/** Relit un plan encodé, ou rend `null` si la chaîne est inexploitable : le service s'arrête alors. */
fun decodeFollowPlan(encoded: String): FollowPlan? = try {
  val dto = json.decodeFromString<PlanDto>(encoded)
  FollowPlan(key = dto.key, itineraryId = dto.itineraryId, legs = dto.legs.map(LegDto::toLeg))
} catch (_: SerializationException) {
  null
} catch (_: IllegalArgumentException) {
  null
}

private fun FollowLeg.toDto(): LegDto = when (this) {
  is FollowLeg.Transit -> LegDto(
    kind = KIND_TRANSIT,
    start = start.toEpochMilli(),
    end = end.toEpochMilli(),
    from = fromName,
    to = toName,
    cancelled = cancelled,
    mode = mode.name,
    line = lineLabel,
    headsign = headsign,
    track = track,
    stops = stops.map { StopDto(name = it.name, time = it.time.toEpochMilli(), cancelled = it.cancelled) },
  )

  is FollowLeg.Street -> LegDto(
    kind = kind.name,
    start = start.toEpochMilli(),
    end = end.toEpochMilli(),
    from = fromName,
    to = toName,
    cancelled = cancelled,
    durationSeconds = duration.seconds,
  )
}

private fun LegDto.toLeg(): FollowLeg = if (kind == KIND_TRANSIT) {
  FollowLeg.Transit(
    start = Instant.ofEpochMilli(start),
    end = Instant.ofEpochMilli(end),
    fromName = from,
    toName = to,
    cancelled = cancelled,
    mode = TransitMode.entries.firstOrNull { it.name == mode } ?: TransitMode.OTHER,
    lineLabel = line,
    headsign = headsign,
    track = track,
    stops = stops.map { FollowStop(name = it.name, time = Instant.ofEpochMilli(it.time), cancelled = it.cancelled) },
  )
} else {
  FollowLeg.Street(
    start = Instant.ofEpochMilli(start),
    end = Instant.ofEpochMilli(end),
    fromName = from,
    toName = to,
    cancelled = cancelled,
    kind = StreetKind.entries.firstOrNull { it.name == kind } ?: StreetKind.WALK,
    duration = Duration.ofSeconds(durationSeconds),
  )
}
