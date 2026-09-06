package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Une frappe de l'usager dans un champ d'autocomplétion.
 *
 * [bias] accompagne chaque frappe plutôt que d'être fixé une fois pour toutes : le centre de la
 * carte bouge pendant la saisie, et c'est lui qui privilégie les résultats proches (SPEC.md § 5.1).
 */
data class AutocompleteQuery(val text: String, val bias: LatLon? = null, val language: String? = null)

/** Ce qu'un champ d'autocomplétion a à afficher à un instant donné. */
sealed interface AutocompleteState {
  /** Saisie trop courte, ou champ vidé : aucune requête n'a été émise, aucune liste à montrer. */
  data object Idle : AutocompleteState

  /** Une requête est en vol. */
  data object Loading : AutocompleteState

  /** Résultats du serveur. Une liste vide est un état vide légitime, pas une erreur. */
  data class Suggestions(val locations: List<Location>) : AutocompleteState

  /** Échec à traduire en message par l'interface (SPEC.md § 8). */
  data class Failed(val error: EscaleError) : AutocompleteState
}

/** Les trois règles de sobriété de l'autocomplétion (SPEC.md § 7.1), au même endroit. */
object AutocompleteRules {
  /** SPEC.md § 7.1 : « debounce ≥ 350 ms ». */
  const val DEBOUNCE_MILLIS = 350L

  /** SPEC.md § 7.1 : en deçà de trois caractères, aucune requête n'est émise. */
  const val MIN_TEXT_LENGTH = 3
}

/**
 * Applique les trois règles de sobriété de SPEC.md § 7.1 à un flux de frappes : anti-rebond de
 * 350 ms, minimum de trois caractères, annulation de la requête précédente.
 *
 * C'est de la logique, pas de l'affichage : elle vit ici, dans `:core`, où elle se teste en JVM
 * avec du temps virtuel (docs/architecture.md § 1). Un `ViewModel` n'a plus qu'à brancher le texte
 * de son champ dessus et à collecter les états.
 *
 * L'annulation vient de `flatMapLatest` : une nouvelle frappe annule la coroutine de la précédente,
 * donc la requête HTTP qu'elle attendait.
 *
 * Ce que le serveur rend est ensuite **composé** pour l'affichage (`composeSuggestions`) : vingt
 * candidats demandés, dix lignes montrées, choisies pour qu'un type de lieu n'écrase pas les autres
 * (SPEC.md § 5.1). Le dédoublonnage, lui, a déjà eu lieu : il appartient au dépôt, qui l'applique à
 * tous ses appelants.
 *
 * @param queries les frappes successives, une par caractère saisi.
 * @param limit `numResults`, vingt par défaut (SPEC.md § 5.1).
 * @param displayed le nombre de lignes affichables, dix par défaut (SPEC.md § 5.1).
 * @param debounceMillis paramétrable pour les tests uniquement ; jamais en deçà de 350 ms.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun GeocodeRepository.autocompleteStream(
  queries: Flow<AutocompleteQuery>,
  limit: Int = GeocodeRepository.DEFAULT_RESULT_COUNT,
  displayed: Int = GeocodeRepository.DISPLAYED_RESULT_COUNT,
  debounceMillis: Long = AutocompleteRules.DEBOUNCE_MILLIS,
): Flow<AutocompleteState> = queries
  .map { it.copy(text = it.text.trim()) }
  .distinctUntilChanged()
  // Vider le champ doit vider la liste tout de suite : n'attendre que sur une saisie exploitable.
  .debounce { query -> if (query.text.length < AutocompleteRules.MIN_TEXT_LENGTH) 0L else debounceMillis }
  .flatMapLatest { query ->
    if (query.text.length < AutocompleteRules.MIN_TEXT_LENGTH) {
      flowOf<AutocompleteState>(AutocompleteState.Idle)
    } else {
      flow {
        emit(AutocompleteState.Loading)
        emit(autocomplete(query.text, query.bias, query.language, limit).toState(displayed))
      }
    }
  }
  // Effacer trois caractères d'affilée ramène trois fois au même état vide : n'en montrer qu'un.
  .distinctUntilChanged()

private fun Outcome<List<Location>>.toState(displayed: Int): AutocompleteState = when (this) {
  is Outcome.Success -> AutocompleteState.Suggestions(composeSuggestions(value, displayed))
  is Outcome.Failure -> AutocompleteState.Failed(error)
}
