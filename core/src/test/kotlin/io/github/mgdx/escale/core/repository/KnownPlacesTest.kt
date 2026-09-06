package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.SearchHistoryEntry
import io.github.mgdx.escale.core.model.TimeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Le bloc « déjà utilisés » de SPEC.md § 5.1 : ce qu'il propose, sans rien demander au serveur. */
class KnownPlacesTest {

  private var nextPoint = 0

  private fun location(
    name: String,
    description: String? = "Paris, Île-de-France",
    kind: PlaceKind = PlaceKind.STOP,
  ): Location {
    nextPoint++
    return Location(
      id = if (kind == PlaceKind.STOP) "stop-$nextPoint" else null,
      name = name,
      description = description,
      coordinates = LatLon(lat = 48.0 + nextPoint / 100.0, lon = 2.0 + nextPoint / 100.0),
      kind = kind,
    )
  }

  private fun search(from: Location, to: Location, minutes: Long) = SearchHistoryEntry(
    id = minutes,
    from = from,
    to = to,
    time = TimeChoice.Now,
    searchedAt = Instant.parse("2026-03-02T08:00:00Z").plusSeconds(minutes * 60),
  )

  @Test
  fun `des la premiere lettre, un lieu enregistre est propose`() {
    val home = location("Gare de l'Est", kind = PlaceKind.ADDRESS)
    assertEquals(listOf(home), matchingKnownPlaces("g", saved = listOf(home), recent = emptyList()))
  }

  @Test
  fun `le filtre ignore la casse et les accents, dans les deux sens`() {
    val asnieres = location("Asnières-sur-Seine")
    val saved = listOf(asnieres)
    assertEquals(listOf(asnieres), matchingKnownPlaces("asnieres", saved, emptyList()))
    assertEquals(listOf(asnieres), matchingKnownPlaces("ASNIÈRES", saved, emptyList()))
    // Les espaces de bordure d'une saisie au clavier ne doivent rien changer.
    assertEquals(listOf(asnieres), matchingKnownPlaces("  Asniè ", saved, emptyList()))
  }

  @Test
  fun `le complement de localisation compte autant que le nom`() {
    val place = location("Colonne de Juillet", description = "Bastille, Paris")
    assertEquals(listOf(place), matchingKnownPlaces("bastille", listOf(place), emptyList()))
  }

  @Test
  fun `ce qui ne correspond pas n est pas propose`() {
    assertTrue(matchingKnownPlaces("montparnasse", listOf(location("Gare du Nord")), emptyList()).isEmpty())
  }

  @Test
  fun `une saisie vide ou blanche ne propose rien`() {
    val saved = listOf(location("Gare du Nord"))
    assertTrue(matchingKnownPlaces("", saved, emptyList()).isEmpty())
    assertTrue(matchingKnownPlaces("   ", saved, emptyList()).isEmpty())
  }

  @Test
  fun `les lieux enregistres passent devant l historique, du plus recent au plus ancien`() {
    val home = location("Gare du Nord", kind = PlaceKind.ADDRESS)
    val ancienne = search(location("Gare de Lyon"), location("Gare d'Austerlitz"), minutes = 1)
    val recente = search(location("Gare Saint-Lazare"), location("Gare Montparnasse"), minutes = 9)

    val proposed = matchingKnownPlaces("gare", listOf(home), listOf(ancienne, recente))

    assertEquals(
      listOf("Gare du Nord", "Gare Saint-Lazare", "Gare Montparnasse", "Gare de Lyon", "Gare d'Austerlitz"),
      proposed.map { it.name },
    )
  }

  @Test
  fun `le bloc est borne, et garde les premiers`() {
    val recent = List(6) { rang ->
      search(location("Gare $rang"), location("Arrivée $rang"), minutes = rang.toLong())
    }
    val proposed = matchingKnownPlaces("gare", saved = emptyList(), recent = recent)
    assertEquals(MAX_KNOWN_PLACES, proposed.size)
    // La recherche la plus récente est celle du plus grand instant : c'est elle qui ouvre le bloc.
    assertEquals("Gare 5", proposed.first().name)
  }

  @Test
  fun `le domicile qui est aussi le depart des dernieres recherches n apparait qu une fois`() {
    val home = location("Gare du Nord")
    // Le même arrêt, tel qu'une recherche passée l'a enregistré : même identifiant, même point.
    val historique = search(home, location("Gare de Lyon"), minutes = 3)

    val proposed = matchingKnownPlaces("gare", listOf(home), listOf(historique))

    assertEquals(listOf("Gare du Nord", "Gare de Lyon"), proposed.map { it.name })
  }

  @Test
  fun `historique desactive, seuls les lieux enregistres sont proposes`() {
    // SPEC.md § 5.5 : la bascule est tenue par le dépôt, qui ne rend alors aucune entrée. Il n'y a
    // rien de plus à vérifier ici, et surtout rien à recopier.
    val home = location("Gare du Nord", kind = PlaceKind.ADDRESS)
    assertEquals(listOf(home), matchingKnownPlaces("gare", listOf(home), recent = emptyList()))
  }

  @Test
  fun `les suggestions du serveur perdent celles que le bloc montre deja`() {
    val gareDuNord = location("Gare du Nord")
    val server = listOf(gareDuNord, location("Gare de Lyon"), location("Gare de l'Est"))

    val filtered = server.withoutKnownPlaces(listOf(gareDuNord))

    assertEquals(listOf("Gare de Lyon", "Gare de l'Est"), filtered.map { it.name })
  }

  @Test
  fun `un bloc vide ou lui-meme en double ne mange aucune suggestion`() {
    val gareDuNord = location("Gare du Nord")
    val server = listOf(location("Gare de Lyon"), location("Gare de l'Est"))
    assertEquals(server, server.withoutKnownPlaces(emptyList()))
    // Un appelant qui donnerait deux fois le même lieu ne doit pas décaler le décompte.
    assertEquals(server, server.withoutKnownPlaces(listOf(gareDuNord, gareDuNord)))
  }
}
