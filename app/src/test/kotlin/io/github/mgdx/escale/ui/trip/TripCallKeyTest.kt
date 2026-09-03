package io.github.mgdx.escale.ui.trip

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Place
import io.github.mgdx.escale.core.model.StopVisit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Les clés de la desserte d'une course (SPEC.md § 5.3).
 *
 * Une clé de liste paresseuse doit être unique **dans le pire cas** : deux clés égales ne dégradent
 * pas l'affichage, elles font lever la liste et emportent l'écran.
 */
class TripCallKeyTest {

  private fun call(stopId: String?, name: String, minute: Long): StopVisit {
    val time = Instant.parse("2026-03-02T08:00:00Z").plusSeconds(minute * 60)
    return StopVisit(
      place = Place(
        name = name,
        coordinates = LatLon(48.8443, 2.3735),
        stopId = stopId,
        track = null,
        scheduledTime = time,
        time = time,
      ),
      arrival = time,
      departure = time,
    )
  }

  @Test
  fun `une ligne circulaire repasse au meme arret sans produire deux fois la meme cle`() {
    // Le cas réel : une navette qui boucle, ou un train qui rebrousse. L'identifiant désigne
    // l'arrêt, jamais le passage.
    val calls = listOf(
      call("de:06:1234", "Gare", minute = 0),
      call("de:06:5678", "Marché", minute = 8),
      call("de:06:1234", "Gare", minute = 20),
    )

    val keys = calls.map { it.key() }

    assertEquals(3, keys.size)
    assertEquals(keys.size, keys.distinct().size)
  }

  @Test
  fun `deux arrets homonymes sans identifiant restent distincts`() {
    val calls =
      listOf(call(stopId = null, name = "Mairie", minute = 0), call(stopId = null, name = "Mairie", minute = 12))

    val keys = calls.map { it.key() }

    assertEquals(keys.size, keys.distinct().size)
  }

  @Test
  fun `le meme passage garde la meme cle quand le temps reel decale l heure`() {
    val call = call("de:06:1234", "Gare", minute = 0)
    // L'heure effective bouge, l'horaire théorique non : la position de défilement survit au
    // rafraîchissement, ce qu'une clé bâtie sur l'heure réelle aurait perdu.
    val delayed = call.copy(place = call.place.copy(time = call.place.time.plusSeconds(180)))

    assertEquals(call.key(), delayed.key())
  }
}
