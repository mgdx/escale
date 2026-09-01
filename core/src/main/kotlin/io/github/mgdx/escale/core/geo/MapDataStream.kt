package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.BoundingBox
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Le flux des requêtes de carte à émettre, à partir des arrêts successifs de la caméra
 * (SPEC.md § 5.7, règles 1, 3 et 5).
 *
 * L'appelant n'alimente [viewports] qu'à `onCameraIdle` : **aucune requête pendant le mouvement**
 * (règle 1). L'anti-rebond de 300 ms fait qu'un déplacement suivi d'un autre n'émet qu'une
 * requête, la dernière. La décision de réémettre ou non est celle de [planMapLoad].
 *
 * **Règle 2, l'annulation, appartient au consommateur** : il collecte ce flux avec un
 * `collectLatest` ou un `flatMapLatest`, si bien qu'une nouvelle requête annule la coroutine — donc
 * la requête HTTP — de la précédente. C'est le même mécanisme que celui de `autocompleteStream`.
 *
 * La mémoire de ce qui a été demandé est locale à chaque collecte : deux collectes indépendantes ne
 * se contaminent pas, et une nouvelle collecte repart d'une carte vierge, ce qui est exactement
 * l'état de la carte après un changement de serveur.
 *
 * @param debounceMillis paramétrable pour les tests uniquement ; jamais en deçà de 300 ms.
 */
@OptIn(FlowPreview::class)
fun mapDataRequests(
  viewports: Flow<MapViewport>,
  debounceMillis: Long = MapLoadRules.CAMERA_IDLE_DEBOUNCE_MILLIS,
): Flow<MapDataRequest> = flow {
  var loaded: MapDataRequest? = null
  viewports
    .distinctUntilChanged()
    .debounce(debounceMillis)
    .collect { viewport ->
      val request = planMapLoad(loaded, viewport.visibleArea, viewport.zoom)
      if (request != null) {
        loaded = request
        emit(request)
      }
    }
}

/** L'emprise de l'écran élargie de 30 %, telle que SPEC.md § 5.7 règle 3 l'exige. */
fun BoundingBox.asRequestArea(): BoundingBox = expandBy(MapLoadRules.AREA_EXPANSION_RATIO)
