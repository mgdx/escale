package io.github.mgdx.escale.ui.server

import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.result.EscaleError

/**
 * L'état d'une des trois étapes du test de connexion (SPEC.md § 5.6.1).
 *
 * Chaque étape est rendue à l'écran par une icône **et** un texte : SPEC.md § 9 interdit qu'une
 * information soit portée par la seule couleur.
 */
enum class CheckStepState {
  /** Le test n'a pas encore été lancé pour l'URL saisie. */
  IDLE,

  /** Étape en cours. */
  RUNNING,

  /** Étape concluante. */
  PASSED,

  /** Étape en échec : le serveur ne répond pas, ou son API est trop ancienne. */
  FAILED,

  /**
   * Étape aboutie, service absent, sans que cela empêche d'utiliser le serveur.
   * C'est le cas des tuiles : un serveur qui n'en sert pas reste utilisable (SPEC.md § 5.7).
   */
  ABSENT,
}

/**
 * Le résultat des trois étapes du test de connexion, affichées séparément (SPEC.md § 5.6.1).
 *
 * Les trois étapes sont menées par `ServerRepository.test()`, qui les enchaîne en un seul appel :
 * elles passent donc toutes en [CheckStepState.RUNNING] ensemble, puis reçoivent leur résultat
 * ensemble. L'usager voit néanmoins trois lignes distinctes, chacune avec son propre état.
 */
data class ConnectionTest(
  val reachable: CheckStepState = CheckStepState.IDLE,
  val apiVersion: CheckStepState = CheckStepState.IDLE,
  val tiles: CheckStepState = CheckStepState.IDLE,
  /** L'échec de la première étape, seul cas où le test s'arrête en erreur. */
  val error: EscaleError? = null,
) {
  val running: Boolean get() = reachable == CheckStepState.RUNNING

  /** Le test est-il concluant ? Les tuiles n'entrent pas en compte : elles sont facultatives. */
  val passed: Boolean
    get() = reachable == CheckStepState.PASSED && apiVersion == CheckStepState.PASSED

  /** Le test a-t-il été mené jusqu'au bout, quel qu'en soit le verdict ? */
  val finished: Boolean
    get() = !running && reachable != CheckStepState.IDLE
}

/**
 * Ce que l'usager voulait faire quand l'avertissement sur le trafic en clair s'est interposé.
 *
 * docs/architecture.md § 11.1 : le consentement est demandé avant **toute** requête en clair, donc
 * aussi avant le test de connexion, et pas seulement avant l'enregistrement.
 */
enum class PendingCleartextAction {
  TEST,
  SAVE,
}

/** Les boîtes de dialogue de l'écran. Une seule à la fois. */
sealed interface ServerDialog {

  /**
   * Avertissement explicite avant la première requête en clair vers [host]
   * (SPEC.md § 5.6.1, docs/architecture.md § 11.1).
   */
  data class CleartextWarning(val host: String, val pending: PendingCleartextAction) : ServerDialog

  /**
   * Effets d'un changement de serveur, annoncés **avant** confirmation (SPEC.md § 5.6.1).
   *
   * [untested] signale un « Utiliser quand même » : le serveur n'a pas passé le test, l'usager
   * l'accepte en connaissance de cause.
   */
  data class SwitchEffects(val baseUrl: String, val hasTiles: Boolean, val untested: Boolean) : ServerDialog
}

/**
 * L'état de l'écran « Serveur MOTIS » (SPEC.md § 5.6.1).
 *
 * Il vit entièrement dans le `ViewModel` : la rotation et le retour depuis l'arrière-plan le
 * retrouvent intact.
 */
data class ServerSettingsUiState(
  /** Le serveur effectivement en service, tel que rendu par le dépôt. */
  val currentServerUrl: String = "",
  /** La saisie brute, telle que tapée ou collée. */
  val input: String = "",
  /** La racine normalisée qui serait enregistrée, ou `null` si la saisie n'en forme pas une. */
  val normalizedInput: String? = null,
  /** Vrai quand la saisie n'est pas vide mais ne forme aucune URL exploitable. */
  val inputInvalid: Boolean = false,
  val connectionTest: ConnectionTest = ConnectionTest(),
  /** Les serveurs déjà utilisés, les plus récents d'abord (SPEC.md § 5.6.1). */
  val knownServers: List<ServerConfig> = emptyList(),
  val dialog: ServerDialog? = null,
) {
  /** La saisie désigne-t-elle un serveur en clair, qui appelle un consentement explicite ? */
  val inputIsCleartext: Boolean
    get() = normalizedInput != null && ServerUrl.isCleartext(normalizedInput)

  /** La saisie diffère-t-elle du serveur en service ? Sinon, il n'y a rien à enregistrer. */
  val inputIsNewServer: Boolean
    get() = normalizedInput != null && normalizedInput != currentServerUrl

  /**
   * L'enregistrement direct n'est ouvert qu'après un test concluant (SPEC.md § 5.6.1) ; sinon,
   * l'écran ne propose qu'un « Utiliser quand même » explicite.
   */
  val canSave: Boolean
    get() = inputIsNewServer && connectionTest.passed

  val canForce: Boolean
    get() = inputIsNewServer && !connectionTest.passed && !connectionTest.running
}
