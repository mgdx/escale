package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.result.EscaleError

/**
 * Les trois étapes du test de connexion de l'écran « Serveur MOTIS » (SPEC.md § 5.6.1), dans
 * l'ordre où elles sont menées.
 */
enum class ServerTestStep {
  /** `GET /api/v1/health` : le serveur est-il joignable ? */
  REACHABLE,

  /** `GET /api/v6/map/stops` sur une petite emprise : la version d'API est-elle celle attendue ? */
  API_VERSION,

  /** Une tuile vectorielle : le serveur sert-il un fond de carte ? */
  TILES,
}

/**
 * L'avancement du test de connexion, **étape par étape**.
 *
 * SPEC.md § 5.6.1 demande « trois résultats distincts affichés ». Rendre un seul résultat à la fin
 * des trois appels satisfait la lettre mais pas l'intention : sur un serveur lent, les trois lignes
 * restent figées ensemble et l'usager ne sait pas laquelle progresse. Le dépôt rend donc un flux :
 * chaque étape signale son départ, puis son verdict, dès qu'il est connu.
 *
 * L'ordre des émissions est garanti : pour chaque étape, un [Started] puis exactement un [Finished]
 * ou un [Skipped], les étapes se succédant dans l'ordre de [ServerTestStep].
 */
sealed interface ServerTestProgress {

  val step: ServerTestStep

  /** L'appel de [step] vient de partir ; son verdict n'est pas connu. */
  data class Started(override val step: ServerTestStep) : ServerTestProgress

  /**
   * [step] a rendu son verdict.
   *
   * [passed] est faux pour un serveur sans tuiles comme pour une API trop ancienne : c'est
   * l'appelant qui sait que la troisième étape est facultative (SPEC.md § 5.7) et la seconde non.
   * [error] n'est renseignée que par [ServerTestStep.REACHABLE], seule étape dont l'échec arrête le
   * test ; [health] n'est renseignée que par cette même étape, en cas de succès.
   */
  data class Finished(
    override val step: ServerTestStep,
    val passed: Boolean,
    val error: EscaleError? = null,
    val health: ServerHealth? = null,
  ) : ServerTestProgress

  /**
   * [step] n'a pas été menée : une étape précédente l'a rendue sans objet.
   *
   * Le seul cas est celui d'un serveur injoignable — interroger l'API et les tuiles d'un serveur
   * qui n'a pas répondu ferait attendre l'usager deux expirations de plus pour ne rien lui
   * apprendre, et afficher « échec » sur ces deux lignes affirmerait sur ce serveur quelque chose
   * qui n'a pas été mesuré.
   */
  data class Skipped(override val step: ServerTestStep) : ServerTestProgress
}
