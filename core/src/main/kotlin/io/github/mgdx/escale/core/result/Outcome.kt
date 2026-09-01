package io.github.mgdx.escale.core.result

/**
 * Résultat d'une opération susceptible d'échouer, tel que fixé par docs/architecture.md § 6.
 *
 * Aucune fonction de dépôt ne lève d'exception : elle rend un [Failure] portant un [EscaleError]
 * que l'interface sait traduire en message (SPEC.md § 8).
 */
sealed interface Outcome<out T> {
  data class Success<T>(val value: T) : Outcome<T>

  data class Failure(val error: EscaleError) : Outcome<Nothing>
}

/** La valeur en cas de succès, `null` en cas d'échec. */
fun <T> Outcome<T>.getOrNull(): T? = when (this) {
  is Outcome.Success -> value
  is Outcome.Failure -> null
}

/** L'erreur en cas d'échec, `null` en cas de succès. */
fun <T> Outcome<T>.errorOrNull(): EscaleError? = when (this) {
  is Outcome.Success -> null
  is Outcome.Failure -> error
}

/** Transforme la valeur portée sans toucher à l'erreur. */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
  is Outcome.Success -> Outcome.Success(transform(value))
  is Outcome.Failure -> this
}
