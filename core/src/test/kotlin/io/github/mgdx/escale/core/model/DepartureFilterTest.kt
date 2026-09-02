package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les puces de filtre des prochains départs (SPEC.md § 5.4), et **le verrou du piège des
 * parapluies** (docs/architecture.md § 11.5, docs/motis-api.md piège n° 8).
 *
 * Ce fichier existe pour qu'un lot suivant ne « simplifie » pas [DepartureModeFilter.TRAIN] en un
 * unique `RAIL` : la simplification ne casserait aucun test d'affichage, ne produirait aucune
 * erreur, et ferait seulement disparaître les trains régionaux d'un tableau de départs.
 */
class DepartureFilterTest {

  @Test
  fun `le filtre train reconnait un train regional, que le parapluie RAIL cacherait`() {
    // Le cas de Châtelet - Les Halles, mot pour mot : MOTIS l'annonce en `REGIONAL_RAIL` seul.
    // Un filtre qui chercherait littéralement `RAIL` — la valeur écrite dans SPEC.md § 5.7 — ne
    // reconnaîtrait pas ce mode, et l'arrêt n'aurait pas de puce « train ».
    assertTrue(DepartureModeFilter.TRAIN.matches(TransitMode.REGIONAL_RAIL))
    assertTrue(DepartureModeFilter.TRAIN.matches(TransitMode.NIGHT_RAIL))
    assertTrue(DepartureModeFilter.TRAIN.matches(TransitMode.SUBURBAN))
    assertTrue(DepartureModeFilter.TRAIN.matches(TransitMode.HIGHSPEED_RAIL))
    assertTrue(DepartureModeFilter.TRAIN.matches(TransitMode.LONG_DISTANCE))
  }

  @Test
  fun `un mode envoye au serveur n'est jamais un parapluie`() {
    // « Un Mode envoyé au serveur peut être un parapluie » — mais pas ici : `RAIL` couvre `SUBWAY`
    // (docs/motis-openapi.yaml, schéma Mode), et un usager qui demande « train » ne demande pas le
    // métro. Vérifié sur api.transitous.org : `mode=RAIL` à Hamburg Hbf rend du `SUBWAY`, les cinq
    // feuilles listées une à une n'en rendent pas (fixture stoptimes_rail_only.json).
    DepartureModeFilter.entries.forEach { filter ->
      assertFalse(filter.name, TransitMode.RAIL in filter.requestModes)
      assertFalse(filter.name, TransitMode.TRANSIT in filter.requestModes)
    }
    assertFalse(TransitMode.SUBWAY in DepartureModeFilter.TRAIN.requestModes)
  }

  @Test
  fun `un parapluie recu du serveur reste reconnu`() {
    // Rien n'interdit à un serveur de renvoyer la valeur agrégée telle quelle : la laisser tomber
    // ferait disparaître la puce d'un arrêt qui la mérite.
    assertTrue(DepartureModeFilter.TRAIN.matches(TransitMode.RAIL))
    assertTrue(DepartureModeFilter.OTHER.matches(TransitMode.TRANSIT))
  }

  @Test
  fun `chaque mode de transport en commun releve d'une puce et d'une seule`() {
    val transitModes = TransitMode.entries.filter { it.isTransit }
    transitModes.forEach { mode ->
      val matching = DepartureModeFilter.entries.filter { it.matches(mode) }
      assertEquals(mode.name, 1, matching.size)
    }
  }

  @Test
  fun `un mode de rue ne releve d'aucune puce`() {
    assertNull(DepartureFilters.of(TransitMode.WALK))
    assertNull(DepartureFilters.of(TransitMode.CAR))
    assertNull(DepartureFilters.of(TransitMode.BIKE))
  }

  @Test
  fun `l'autocar est rangé avec le bus`() {
    // L'API distingue `BUS` et `COACH`, l'usager qui attend au quai non.
    assertEquals(DepartureModeFilter.BUS, DepartureFilters.of(TransitMode.COACH))
    assertEquals(DepartureModeFilter.BUS, DepartureFilters.of(TransitMode.BUS))
  }

  @Test
  fun `les puces d'un arret annonce en train regional seul contiennent le train`() {
    val filters = DepartureFilters.available(listOf(TransitMode.REGIONAL_RAIL, TransitMode.BUS))
    assertEquals(listOf(DepartureModeFilter.TRAIN, DepartureModeFilter.BUS), filters)
  }

  @Test
  fun `les puces suivent l'ordre de declaration, du plus structurant au plus rare`() {
    val filters = DepartureFilters.available(
      listOf(TransitMode.BUS, TransitMode.AIRPLANE, TransitMode.SUBWAY, TransitMode.SUBURBAN),
    )
    assertEquals(
      listOf(
        DepartureModeFilter.TRAIN,
        DepartureModeFilter.SUBWAY,
        DepartureModeFilter.BUS,
        DepartureModeFilter.OTHER,
      ),
      filters,
    )
  }

  @Test
  fun `un arret desservi par un seul mode n'offre aucune puce`() {
    // Une puce unique ne trie rien : elle n'ajouterait qu'un bouton à ne pas appuyer.
    assertTrue(DepartureFilters.available(listOf(TransitMode.BUS, TransitMode.COACH)).isEmpty())
    assertTrue(DepartureFilters.available(emptyList()).isEmpty())
  }
}
