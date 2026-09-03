package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Le recollement des pages de départs (SPEC.md § 5.4, « boutons plus tôt / plus tard »). */
class DepartureFeedTest {

  private val noon: Instant = Instant.parse("2026-09-02T12:00:00Z")

  private fun entry(minutes: Long, trip: String = "course-$minutes", delayMinutes: Long = 0) = StopTimeEntry(
    tripId = trip,
    mode = TransitMode.BUS,
    lineName = "18",
    headsign = "Hauptbahnhof/ZOB",
    agencyName = "Hochbahn",
    track = null,
    scheduledTime = noon.plusSeconds(minutes * 60),
    time = noon.plusSeconds((minutes + delayMinutes) * 60),
    realTime = delayMinutes != 0L,
  )

  private fun page(entries: List<StopTimeEntry>, previous: String? = "EARLIER|1", next: String? = "LATER|1") =
    StopTimePage(entries = entries, stop = null, previousPageCursor = previous, nextPageCursor = next)

  @Test
  fun `plus tard etend la liste au lieu de la remplacer`() {
    val feed = DepartureFeed.of(page(listOf(entry(0), entry(5)), next = "LATER|1"))
      .later(page(listOf(entry(10), entry(15)), previous = "EARLIER|2", next = "LATER|2"))

    assertEquals(listOf(0L, 5L, 10L, 15L), feed.entries.map { minutesAfterNoon(it) })
    // C'est le curseur suivant qui avance ; le précédent continue de désigner le début affiché.
    assertEquals("LATER|2", feed.nextPageCursor)
    assertEquals("EARLIER|1", feed.previousPageCursor)
  }

  @Test
  fun `plus tot etend la liste par le haut`() {
    val feed = DepartureFeed.of(page(listOf(entry(10)), previous = "EARLIER|1", next = "LATER|1"))
      .earlier(page(listOf(entry(0), entry(5)), previous = "EARLIER|0", next = "LATER|0"))

    assertEquals(listOf(0L, 5L, 10L), feed.entries.map { minutesAfterNoon(it) })
    assertEquals("EARLIER|0", feed.previousPageCursor)
    assertEquals("LATER|1", feed.nextPageCursor)
  }

  @Test
  fun `deux pages qui se recouvrent n'affichent pas deux fois le meme depart`() {
    // `n` est un minimum : le serveur complète toujours la dernière minute atteinte, si bien que
    // deux pages voisines se recouvrent régulièrement.
    val shared = entry(10)
    val feed = DepartureFeed.of(page(listOf(entry(5), shared)))
      .later(page(listOf(shared, entry(15))))

    assertEquals(3, feed.entries.size)
    assertEquals(listOf(5L, 10L, 15L), feed.entries.map { minutesAfterNoon(it) })
  }

  @Test
  fun `sur un recouvrement, c'est l'exemplaire de la page qui arrive qui est retenu`() {
    val stale = entry(10, trip = "course-A")
    val fresh = entry(10, trip = "course-A", delayMinutes = 3)
    // « Plus tôt » passe la page reçue en premier : c'est elle qui gagne le dédoublonnage, et
    // c'est elle qui porte le retard le plus récent.
    val feed = DepartureFeed(entries = listOf(stale)).earlier(page(listOf(fresh)))

    assertEquals(1, feed.entries.size)
    assertTrue(feed.entries.single().realTime)
  }

  @Test
  fun `la liste est triee sur l'heure effective, pas sur l'horaire theorique`() {
    // Un bus de 12:00 retardé de dix minutes passe après celui de 12:05 : c'est l'ordre du quai.
    val late = entry(0, trip = "course-A", delayMinutes = 10)
    val onTime = entry(5, trip = "course-B")
    val feed = DepartureFeed.of(page(listOf(late, onTime)))

    assertEquals(listOf("course-B", "course-A"), feed.entries.map { it.tripId })
  }

  @Test
  fun `un serveur sans curseur masque les deux boutons`() {
    val feed = DepartureFeed.of(page(listOf(entry(0)), previous = null, next = null))
    assertNull(feed.previousPageCursor)
    assertNull(feed.nextPageCursor)
  }

  @Test
  fun `une course ajoutee en temps reel, sans identifiant, reste distinguee`() {
    val first = entry(0, trip = "").copy(headsign = "Wedel")
    val second = entry(0, trip = "").copy(headsign = "Poppenbüttel")
    val feed = DepartureFeed.of(page(listOf(first, second)))
    assertEquals(2, feed.entries.size)
  }

  @Test
  fun `une page vide laisse une liste vide, jamais une liste absente`() {
    assertTrue(DepartureFeed.of(page(emptyList())).isEmpty)
  }

  private fun minutesAfterNoon(entry: StopTimeEntry): Long = (entry.scheduledTime.epochSecond - noon.epochSecond) / 60
}
