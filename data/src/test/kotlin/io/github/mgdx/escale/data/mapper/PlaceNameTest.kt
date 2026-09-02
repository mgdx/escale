package io.github.mgdx.escale.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les valeurs sentinelles du champ `name` de `Place` (SPEC.md § 5.3, docs/architecture.md § 1).
 *
 * Elles ne sont documentées nulle part dans `docs/motis-openapi.yaml` : c'est justement pour cela
 * qu'un test les fige, plutôt que de compter sur la mémoire du prochain lecteur.
 */
class PlaceNameTest {

  @Test
  fun `les marqueurs d'extremite ne sont pas des noms de lieu`() {
    assertEquals("", placeName("START"))
    assertEquals("", placeName("END"))
  }

  @Test
  fun `une chaine vide ou blanche vaut absence de nom`() {
    assertEquals("", placeName(""))
    assertEquals("", placeName("   "))
  }

  @Test
  fun `un vrai nom traverse le mapping intact, espaces superflus mis a part`() {
    assertEquals("Nürnberg Hbf", placeName("  Nürnberg Hbf  "))
  }

  @Test
  fun `la comparaison est sensible a la casse, pour ne pas effacer un arret qui s'appellerait Start`() {
    assertEquals("Start", placeName("Start"))
    assertEquals("End Street", placeName("End Street"))
  }
}
