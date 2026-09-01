package io.github.mgdx.escale.ui.map

import android.content.res.Resources
import io.github.mgdx.escale.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * Les feuilles de style MapLibre embarquées (SPEC.md § 5.7).
 *
 * Aucune feuille n'est téléchargée : un serveur MOTIS n'en sert pas (`/tiles/style.json` répond
 * 501). L'application embarque donc la sienne, en clair et en sombre, et n'y substitue qu'une
 * chose : la racine du serveur configuré. C'est ce qui permet à la carte de **suivre un changement
 * de serveur** au lieu de figer l'URL au démarrage.
 *
 * Le schéma des tuiles MOTIS est **Shortbread v1.0**, pas OpenMapTiles : les couches s'appellent
 * `streets`, `land`, `water_polygons`, `buildings`, `pois`, `place_labels`… Les deux fichiers de
 * `res/raw` sont deux rendus du même jeu de couches, à deux palettes près.
 *
 * La lecture et la substitution se font hors du fil principal, et le modèle lu est gardé en
 * mémoire : SPEC.md § 5.7 vise moins de 1,5 s jusqu'à la première image.
 */
class MapStyles(private val resources: Resources, private val io: CoroutineDispatcher = Dispatchers.IO) {

  private val templates = ConcurrentHashMap<Int, String>()
  private val json = Json { ignoreUnknownKeys = true }

  /** La feuille complète, tuiles du serveur [baseUrl] comprises. */
  suspend fun tiledStyle(baseUrl: String, dark: Boolean): String = withContext(io) {
    template(dark).replace(BASE_URL_TOKEN, baseUrl.trimEnd('/'))
  }

  /**
   * Un fond neutre, sans aucune source de tuiles.
   *
   * C'est ce qu'affiche un serveur qui ne sert pas de fond de carte (SPEC.md § 5.7) : la couleur
   * de fond de la feuille du thème courant, et rien d'autre. **Aucun repli sur un fournisseur
   * tiers**, la spec l'interdit. Les tracés de trajet, eux, viendront s'y poser au jalon 4.
   */
  suspend fun blankStyle(dark: Boolean): String = withContext(io) {
    val model = json.parseToJsonElement(template(dark)).jsonObject
    buildJsonObject {
      put("version", STYLE_SPEC_VERSION)
      put("name", model["name"] ?: JsonPrimitive(""))
      put("sources", buildJsonObject { })
      put("layers", buildJsonArray { backgroundLayer(model)?.let(::add) })
    }.toString()
  }

  /** La couche `background` de la feuille, seule couche qui ne demande aucune tuile. */
  private fun backgroundLayer(model: JsonObject): JsonObject? = model["layers"]
    ?.jsonArray
    ?.map { it.jsonObject }
    ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "background" }

  private fun template(dark: Boolean): String {
    val resourceId = if (dark) R.raw.map_style_dark else R.raw.map_style_light
    return templates.getOrPut(resourceId) {
      resources.openRawResource(resourceId).use { it.readBytes().decodeToString() }
    }
  }

  private companion object {
    /** Le jeton que les deux fichiers de `res/raw` portent à la place de la racine du serveur. */
    const val BASE_URL_TOKEN = "__BASE_URL__"
    const val STYLE_SPEC_VERSION = 8
  }
}
