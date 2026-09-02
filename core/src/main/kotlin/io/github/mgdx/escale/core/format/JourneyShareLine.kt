package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.Journey
import io.github.mgdx.escale.core.model.JourneyLeg
import java.time.Duration
import java.time.Instant

/**
 * Une ligne du texte de partage d'un trajet (SPEC.md § 5.3, « partager le trajet en texte »).
 *
 * Ce type ne porte **aucun mot** : il dit de quoi la ligne parle, `:app` choisit la formulation
 * dans `strings_detail.xml`. C'est ce qui permet de partager un trajet dans la langue de l'usager
 * sans concaténer une seule chaîne codée en dur (CLAUDE.md), tout en gardant l'ordre et le contenu
 * du texte vérifiables par un test JVM.
 */
sealed interface JourneyShareLine {

  /** L'en-tête : heures de départ et d'arrivée, durée totale, nombre de correspondances. */
  data class Summary(val start: Instant, val end: Instant, val duration: Duration, val transfers: Int) :
    JourneyShareLine

  /** Le point de départ ou le point d'arrivée du trajet. */
  data class Endpoint(val name: String, val time: Instant, val isOrigin: Boolean) : JourneyShareLine

  /** Une portion en transport en commun. [label] est déjà arbitré par [transitLineLabel]. */
  data class Transit(val leg: JourneyLeg.Transit, val label: String?) : JourneyShareLine

  /** Une portion à pied, à vélo, en voiture ou en véhicule partagé. */
  data class Street(val leg: JourneyLeg) : JourneyShareLine
}

/**
 * Le plan du texte de partage de [journey] : un en-tête, le départ, une ligne par portion dans
 * l'ordre, puis l'arrivée.
 *
 * Les heures retenues sont les heures **effectives** : c'est celles-là que lira le destinataire du
 * message, et une heure théorique périmée l'enverrait sur le mauvais train.
 */
fun shareLinesOf(journey: Journey): List<JourneyShareLine> {
  val first = journey.legs.firstOrNull()
  val last = journey.legs.lastOrNull()
  val lines = mutableListOf<JourneyShareLine>(
    JourneyShareLine.Summary(
      start = journey.startTime,
      end = journey.endTime,
      duration = journey.duration,
      transfers = journey.transfers,
    ),
  )
  if (first != null) {
    lines += JourneyShareLine.Endpoint(first.from.name, journey.startTime, isOrigin = true)
  }
  journey.legs.mapTo(lines) { leg ->
    if (leg is JourneyLeg.Transit) {
      JourneyShareLine.Transit(leg, transitLineLabel(leg))
    } else {
      JourneyShareLine.Street(leg)
    }
  }
  if (last != null) {
    lines += JourneyShareLine.Endpoint(last.to.name, journey.endTime, isOrigin = false)
  }
  return lines
}
