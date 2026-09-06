package io.github.mgdx.escale.ui.map

import io.github.mgdx.escale.core.model.POI_TYPES
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les deux feuilles de style embarquées, éprouvées comme des données (SPEC.md § 5.7).
 *
 * Elles ne sont lues ni par `Resources` ni par Robolectric — `:app` n'en a pas — mais **par leur
 * chemin dans le module** : ce sont deux fichiers de texte, et c'est exactement ce qu'il faut
 * vérifier. Une feuille abîmée ne se voit sinon qu'à l'exécution, sur un appareil, par une carte
 * blanche.
 *
 * Ce que ces cas d'essai tiennent : les douze couches existent des deux côtés, avec le bon palier,
 * le bon défaut, le même filtre en clair et en sombre, et **aucune valeur OpenStreetMap comptée
 * deux fois** — sans quoi un même lieu porterait deux pictogrammes superposés.
 */
class MapPoiLayersTest {

  private val json = Json { ignoreUnknownKeys = true }

  private val sheets: Map<String, List<JsonObject>> = listOf("light", "dark").associateWith { name ->
    val file = File("src/main/res/raw/map_style_$name.json")
    assertTrue("feuille introuvable : ${file.absolutePath}", file.exists())
    json.parseToJsonElement(file.readText()).jsonObject.getValue("layers").jsonArray.map { it.jsonObject }
  }

  private fun layer(sheet: String, id: String): JsonObject? =
    sheets.getValue(sheet).firstOrNull { it.getValue("id").jsonPrimitive.content == id }

  private fun ids(sheet: String): List<String> = sheets.getValue(sheet).map { it.getValue("id").jsonPrimitive.content }

  @Test
  fun `les deux feuilles portent les douze couches de points d'interet`() {
    assertEquals(12, POI_LAYERS.size)
    sheets.keys.forEach { sheet ->
      POI_LAYERS.values.forEach { id -> assertNotNull("$sheet : $id manque", layer(sheet, id)) }
    }
  }

  @Test
  fun `aucune couche de points d'interet ne traine hors de la table`() {
    // `poi-landmarks` a disparu au profit des douze catégories : s'il revenait, il dessinerait ses
    // pictogrammes par-dessus les leurs, sans qu'aucun réglage puisse l'éteindre.
    sheets.keys.forEach { sheet ->
      val unknown = ids(sheet).filter { it.startsWith("poi-") } - POI_LAYERS.values.toSet()
      assertEquals("$sheet : couche inconnue", emptyList<String>(), unknown)
    }
  }

  @Test
  fun `les paliers de zoom suivent la famille de la categorie`() {
    // Les repères dès 15 avec leur nom à 16, les commerces à 16 avec leur nom à 17 (SPEC.md § 5.7).
    sheets.keys.forEach { sheet ->
      POI_LAYERS.forEach { (category, id) ->
        val expected = if (category.kind == PoiKind.LANDMARK) 15 else 16
        val found = layer(sheet, id)!!
        assertEquals("$sheet : $id", expected, found.getValue("minzoom").jsonPrimitive.int)
        assertEquals("$sheet : $id", expected + 1, nameZoom(found))
      }
    }
  }

  @Test
  fun `la visibilite ecrite dans la feuille est celle du premier lancement`() {
    // Sans quoi la carte afficherait, le temps que le réglage arrive, des couches que l'usager n'a
    // pas demandées — ou masquerait celles qu'il attend.
    sheets.keys.forEach { sheet ->
      POI_LAYERS.forEach { (category, id) ->
        val expected = if (category.visibleByDefault) "visible" else "none"
        val visibility = layer(sheet, id)!!.getValue("layout").jsonObject.getValue("visibility")
        assertEquals("$sheet : $id", expected, visibility.jsonPrimitive.content)
      }
    }
  }

  @Test
  fun `le filtre d'une categorie est le meme en clair et en sombre`() {
    POI_LAYERS.values.forEach { id ->
      assertEquals(id, layer("light", id)!!.getValue("filter"), layer("dark", id)!!.getValue("filter"))
    }
  }

  @Test
  fun `les deux feuilles ne different que par leurs couleurs`() {
    POI_LAYERS.values.forEach { id ->
      val light = layer("light", id)!!
      val dark = layer("dark", id)!!
      assertEquals(id, light.getValue("layout"), dark.getValue("layout"))
      assertEquals(id, light.getValue("minzoom"), dark.getValue("minzoom"))
      assertTrue(id, light.getValue("paint") != dark.getValue("paint"))
    }
  }

  @Test
  fun `aucune valeur OpenStreetMap n'apparait dans deux filtres`() {
    // Le défaut le plus coûteux à voir : deux pictogrammes exactement superposés, dont l'un ne
    // s'éteint pas quand on décoche sa catégorie.
    //
    // « Autres commerces » est écarté de ce parcours, et de lui seul : son filtre cite les mêmes
    // valeurs que les autres, mais **en négatif**, pour les exclure. C'est le cas d'essai suivant
    // qui s'assure que cette exclusion est complète.
    val seen = mutableMapOf<String, String>()
    POI_LAYERS.filterKeys { it != PoiCategory.OTHER_SHOPS }.forEach { (category, id) ->
      values(layer("light", id)!!.getValue("filter")).forEach { value ->
        val other = seen.put(value, id)
        assertTrue("« $value » est dans $id et dans $other (catégorie $category)", other == null)
      }
    }
  }

  @Test
  fun `le filtre de chaque categorie reprend exactement la table de core`() {
    // La catégorie « autres commerces » est la seule exception : son filtre est une liste ouverte —
    // « toute autre valeur de `shop` » — et s'écrit donc en négatif.
    POI_LAYERS.filterKeys { it != PoiCategory.OTHER_SHOPS }.forEach { (category, id) ->
      val expected = POI_TYPES.filter { it.category == category }.map { it.value }.toSet()
      assertEquals(id, expected, values(layer("light", id)!!.getValue("filter")))
    }
  }

  @Test
  fun `les autres commerces excluent les valeurs deja nommees, et les vitrines eteintes`() {
    val filter = layer("light", POI_LAYERS.getValue(PoiCategory.OTHER_SHOPS))!!.getValue("filter")
    val excluded = values(filter)
    val namedShops = POI_TYPES.filter { it.tag.name == "SHOP" }.map { it.value }
    assertTrue(excluded.containsAll(namedShops))
    assertTrue(excluded.containsAll(listOf("no", "vacant")))
  }

  @Test
  fun `chaque couche puise ses pictogrammes dans les sprites du serveur`() {
    // Aucune image nouvelle dans l'APK : tout vient de `<base>/sprites/basics/sprites.json`, dont
    // POI_SPRITES tient la liste. Un nom mal orthographié ne se verrait sinon qu'à l'écran, par un
    // pictogramme manquant sur une carte réelle.
    val used = mutableSetOf<String>()
    POI_LAYERS.values.forEach { id ->
      val icons = values(layer("light", id)!!.getValue("layout").jsonObject.getValue("icon-image"))
        .filter { it.startsWith("icon-") }
      assertTrue("$id ne pose aucun pictogramme", icons.isNotEmpty())
      assertTrue("$id : pictogramme hors sprites — $icons", POI_SPRITES.containsAll(icons))
      used += icons
    }
    // Et l'inverse : un nom listé que plus aucune couche n'emploie n'a rien à faire dans la table.
    assertEquals(POI_SPRITES, used)
  }

  @Test
  fun `les commerces se placent sous les reperes`() {
    // L'ordre de la feuille décide de ce qui se dessine par-dessus quoi : les repères d'abord, et
    // les commerces en dessous (SPEC.md § 5.7).
    sheets.keys.forEach { sheet ->
      val order = ids(sheet)
      val lastShop = POI_LAYERS.filterKeys { it.kind == PoiKind.SHOP }.values.maxOf { order.indexOf(it) }
      val firstLandmark = POI_LAYERS.filterKeys { it.kind == PoiKind.LANDMARK }.values.minOf { order.indexOf(it) }
      assertTrue(sheet, lastShop < firstLandmark)
    }
  }

  /** Le zoom auquel le nom apparaît : le seuil du `step` que porte `text-field`. */
  private fun nameZoom(layer: JsonObject): Int {
    val field = layer.getValue("layout").jsonObject.getValue("text-field").jsonArray
    assertEquals("step", field[0].jsonPrimitive.content)
    return field[3].jsonPrimitive.int
  }

  /**
   * Toutes les chaînes citées par une expression, hors noms d'opérateurs et d'étiquettes.
   *
   * Une expression MapLibre est un arbre de listes dont le premier terme est **soit** un opérateur,
   * **soit** une valeur : `["match", ["get", "shop"], ["bakery", "butcher"], true, false]` mêle les
   * deux formes. Distinguer les deux demande de connaître les opérateurs employés, d'où
   * [OPERATORS] ; une liste qui n'en commence pas par un est une liste de valeurs, et se lit
   * entière. [TAG_OPERATORS] désigne celles dont l'argument est un nom d'étiquette et non une
   * valeur — `["get", "shop"]` ne cite pas la valeur « shop ».
   */
  private fun values(node: JsonElement): Set<String> = when (node) {
    is JsonPrimitive -> if (node.isString) setOf(node.content) else emptySet()

    is JsonArray -> {
      val head = (node.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
      when {
        head in TAG_OPERATORS -> emptySet()
        head in OPERATORS -> node.drop(1).flatMapTo(mutableSetOf(), ::values)
        else -> node.flatMapTo(mutableSetOf(), ::values)
      }
    }

    else -> emptySet()
  }

  private companion object {
    /** Les opérateurs d'expression que les deux feuilles emploient, et rien de plus. */
    val OPERATORS = setOf("match", "all", "any", "case", "step", "interpolate", "==", "!=", "!", "coalesce")

    /** Ceux dont l'argument nomme une étiquette ou un contexte, jamais une valeur. */
    val TAG_OPERATORS = setOf("get", "has", "zoom", "literal", "linear")
  }
}
