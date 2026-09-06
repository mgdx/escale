package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiTypeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les libellés des points d'intérêt, un par valeur retenue et un par catégorie.
 *
 * La table de `:core` est fermée ; celle de `:app` doit l'être aussi. Sans ce cas d'essai, une
 * valeur ajoutée à `POI_TYPES` sans sa chaîne ne se verrait qu'à l'écran, sur un lieu qu'un
 * relecteur n'aurait pas pensé à toucher.
 */
class MapPoiLabelsTest {

  @Test
  fun `chaque type de lieu a son libelle, et pas un de trop`() {
    assertEquals(PoiTypeKey.entries.toSet(), POI_TYPE_LABELS.keys)
    assertTrue(POI_TYPE_LABELS.values.all { it != 0 })
  }

  @Test
  fun `chaque categorie a son libelle`() {
    // `poiCategoryLabel` lève sur une catégorie absente : la parcourir toutes est le cas d'essai.
    PoiCategory.entries.forEach { category -> assertTrue(poiCategoryLabel(category) != 0) }
  }

  @Test
  fun `deux types ne partagent jamais la meme chaine`() {
    // Deux libellés identiques sont permis — « Chambre d'hôtes » traduit `guest_house` comme
    // `bed_and_breakfast` —, mais deux clés ne doivent pas pointer sur la *même* ressource : ce
    // serait le signe d'un copier-coller, et la traduction de l'une changerait l'autre.
    val shared = POI_TYPE_LABELS.values.groupingBy { it }.eachCount().filterValues { it > 1 }
    assertEquals(emptyMap<Int, Int>(), shared)
  }
}
