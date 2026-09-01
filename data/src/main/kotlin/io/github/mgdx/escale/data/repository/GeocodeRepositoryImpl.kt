package io.github.mgdx.escale.data.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.repository.AutocompleteRules
import io.github.mgdx.escale.core.repository.GeocodeRepository
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import io.github.mgdx.escale.core.result.map
import io.github.mgdx.escale.data.net.GeocodeApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Autocomplétion et géocodage inverse contre le serveur courant.
 *
 * Le serveur n'est pas retenu à la construction mais relu à chaque appel : l'usager peut en changer
 * pendant que l'écran de recherche est ouvert (SPEC.md § 5.6.1).
 */
class GeocodeRepositoryImpl(
  private val api: GeocodeApi,
  private val serverRepository: ServerRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GeocodeRepository {

  override suspend fun autocomplete(
    text: String,
    bias: LatLon?,
    language: String?,
    limit: Int,
  ): Outcome<List<Location>> {
    val query = text.trim()
    // Deuxième verrou du minimum de trois caractères de SPEC.md § 7.1 : `autocompleteStream` le
    // pose déjà, mais aucun appel direct ne doit pouvoir émettre une requête d'un caractère.
    if (query.length < AutocompleteRules.MIN_TEXT_LENGTH) return Outcome.Success(emptyList())
    return api.geocode(baseUrl(), query, bias, language, limit)
  }

  /**
   * Le libellé de la position de l'usager (SPEC.md § 5.1).
   *
   * Le serveur rend ses résultats par ordre de pertinence : le premier est le bon. Une réponse
   * vide n'est pas une erreur, c'est un endroit qu'il ne sait pas nommer.
   *
   * [language] est ignoré : `/api/v1/reverse-geocode` ne connaît pas ce paramètre
   * (docs/motis-openapi.yaml), il rend le libellé d'OpenStreetMap. Le contrat le déclare pour le
   * jour où l'API l'acceptera ; l'envoyer aujourd'hui ne ferait que salir le cache.
   */
  override suspend fun reverseGeocode(point: LatLon, language: String?): Outcome<Location?> =
    api.reverseGeocode(baseUrl(), point, REVERSE_RESULT_COUNT).map { it.firstOrNull() }

  override suspend fun clearGeocodeCache(): Outcome<Unit> = withContext(ioDispatcher) {
    try {
      api.clearCache()
      Outcome.Success(Unit)
    } catch (failure: IOException) {
      // Un cache qu'on n'a pas pu effacer est un incident disque, pas une panne de réseau.
      Outcome.Failure(EscaleError.Unknown(cause = failure::class.simpleName))
    }
  }

  private suspend fun baseUrl(): String = serverRepository.current.first().baseUrl

  private companion object {
    /** L'appelant ne veut qu'un libellé : inutile de faire calculer cinq candidats au serveur. */
    const val REVERSE_RESULT_COUNT = 1
  }
}
