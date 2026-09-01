package io.github.mgdx.escale.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.escale.AppContainer
import io.github.mgdx.escale.core.model.ServerCheck
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.repository.ServerRepository
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * L'écran « Serveur MOTIS » (SPEC.md § 5.6.1).
 *
 * Il porte deux garanties que l'interface ne doit jamais contourner :
 * - **rien n'est enregistré tant que le test de connexion n'a pas réussi**, sauf « Utiliser quand
 *   même » explicite ;
 * - **aucune requête en clair ne part sans consentement**, une fois par hôte
 *   (docs/architecture.md § 11.1). Le consentement est demandé avant le test lui-même, pas
 *   seulement avant l'enregistrement.
 *
 * L'état vit ici en entier : la rotation et le retour depuis l'arrière-plan le retrouvent intact.
 */
class ServerSettingsViewModel(
  private val serverRepository: ServerRepository,
  private val cleartextConsentStore: CleartextConsentStore,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ServerSettingsUiState())
  val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

  /** Les hôtes en clair déjà acceptés. Suivis à part : ce n'est pas une information d'écran. */
  private var consentedHosts: Set<String> = emptySet()

  /** Tant que l'usager n'a rien tapé, le champ recopie le serveur en service. */
  private var inputTouched: Boolean = false

  init {
    viewModelScope.launch {
      combine(serverRepository.current, serverRepository.knownServers, ::Pair)
        .collect { (current, known) -> onServersChanged(current, known) }
    }
    viewModelScope.launch {
      cleartextConsentStore.consentedHosts.collect { hosts -> consentedHosts = hosts }
    }
  }

  /** Saisie ou collage : la normalisation est calculée à chaque frappe et montrée à l'usager. */
  fun onInputChange(raw: String) {
    inputTouched = true
    val normalized = ServerUrl.normalize(raw)
    _uiState.update { state ->
      state.copy(
        input = raw,
        normalizedInput = normalized,
        inputInvalid = raw.isNotBlank() && normalized == null,
        // Un test ne vaut que pour l'URL testée : toute retouche le rend caduc.
        connectionTest = if (normalized == state.normalizedInput) state.connectionTest else ConnectionTest(),
      )
    }
  }

  /** Bouton « Tester la connexion » (SPEC.md § 5.6.1). */
  fun onTestConnection() {
    val target = _uiState.value.normalizedInput ?: run {
      _uiState.update { it.copy(inputInvalid = it.input.isNotBlank()) }
      return
    }
    if (needsCleartextConsent(target)) {
      askCleartextConsent(target, PendingCleartextAction.TEST)
      return
    }
    runConnectionTest(target)
  }

  /** Bouton « Enregistrer », ouvert seulement après un test concluant. */
  fun onSave() = requestSwitch(untested = false)

  /** Bouton « Utiliser quand même » : l'usager passe outre un test manqué (SPEC.md § 5.6.1). */
  fun onUseAnyway() = requestSwitch(untested = true)

  /** Bascule vers un serveur déjà utilisé, sans ressaisie de l'URL (SPEC.md § 5.6.1). */
  fun onSelectKnownServer(config: ServerConfig) {
    if (config.baseUrl == _uiState.value.currentServerUrl) return
    if (needsCleartextConsent(config.baseUrl)) {
      _uiState.update {
        it.copy(input = config.baseUrl, normalizedInput = config.baseUrl, inputInvalid = false)
      }
      inputTouched = true
      askCleartextConsent(config.baseUrl, PendingCleartextAction.SAVE)
      return
    }
    _uiState.update {
      it.copy(
        input = config.baseUrl,
        normalizedInput = config.baseUrl,
        inputInvalid = false,
        connectionTest = ConnectionTest(),
        dialog = ServerDialog.SwitchEffects(
          baseUrl = config.baseUrl,
          hasTiles = config.hasTiles,
          untested = config.lastCheckedAt == null,
        ),
      )
    }
    inputTouched = true
  }

  /** Suppression par balayage d'un serveur déjà utilisé (SPEC.md § 5.6.1). */
  fun onForgetServer(baseUrl: String) {
    viewModelScope.launch { serverRepository.forget(baseUrl) }
  }

  /** Bouton « Rétablir le serveur par défaut », toujours accessible (SPEC.md § 5.6.1). */
  fun onResetToDefault() {
    val default = ServerUrl.DEFAULT_BASE_URL
    if (default == _uiState.value.currentServerUrl) return
    val known = _uiState.value.knownServers.firstOrNull { it.baseUrl == default }
    _uiState.update {
      it.copy(
        input = default,
        normalizedInput = default,
        inputInvalid = false,
        connectionTest = ConnectionTest(),
        dialog = ServerDialog.SwitchEffects(
          baseUrl = default,
          hasTiles = known?.hasTiles ?: false,
          untested = false,
        ),
      )
    }
    inputTouched = true
  }

  /** L'usager a lu et accepté l'avertissement sur le trafic en clair. */
  fun onAcceptCleartext() {
    val dialog = _uiState.value.dialog as? ServerDialog.CleartextWarning ?: return
    _uiState.update { it.copy(dialog = null) }
    viewModelScope.launch {
      cleartextConsentStore.accept(dialog.host)
      consentedHosts = consentedHosts + dialog.host.lowercase()
      when (dialog.pending) {
        PendingCleartextAction.TEST -> onTestConnection()
        PendingCleartextAction.SAVE -> requestSwitch(untested = !_uiState.value.connectionTest.passed)
      }
    }
  }

  /** L'usager a confirmé le changement de serveur, effets compris. */
  fun onConfirmSwitch() {
    val dialog = _uiState.value.dialog as? ServerDialog.SwitchEffects ?: return
    _uiState.update { it.copy(dialog = null) }
    viewModelScope.launch {
      val known = _uiState.value.knownServers.firstOrNull { it.baseUrl == dialog.baseUrl }
      val config = ServerConfig(
        baseUrl = dialog.baseUrl,
        label = known?.label ?: ServerUrl.hostOf(dialog.baseUrl) ?: dialog.baseUrl,
        hasTiles = dialog.hasTiles,
        lastCheckedAt = if (dialog.untested) known?.lastCheckedAt else Instant.now(),
      )
      // `save` plutôt que `resetToDefault` même pour l'instance publique : le drapeau des tuiles
      // relevé par le test serait perdu par le second, qui repart d'une fiche vierge.
      if (serverRepository.save(config) is Outcome.Success) {
        clearCaches()
      }
    }
  }

  fun onDismissDialog() {
    _uiState.update { it.copy(dialog = null) }
  }

  private fun onServersChanged(current: ServerConfig, known: List<ServerConfig>) {
    _uiState.update { state ->
      state.copy(
        currentServerUrl = current.baseUrl,
        knownServers = known,
        input = if (inputTouched) state.input else current.baseUrl,
        normalizedInput = if (inputTouched) state.normalizedInput else current.baseUrl,
      )
    }
  }

  /** L'avertissement n'est demandé qu'une fois par hôte (docs/architecture.md § 11.1). */
  private fun needsCleartextConsent(baseUrl: String): Boolean {
    if (!ServerUrl.isCleartext(baseUrl)) return false
    val host = ServerUrl.hostOf(baseUrl) ?: return true
    return host.lowercase() !in consentedHosts
  }

  private fun askCleartextConsent(baseUrl: String, pending: PendingCleartextAction) {
    val host = ServerUrl.hostOf(baseUrl) ?: baseUrl
    _uiState.update { it.copy(dialog = ServerDialog.CleartextWarning(host = host, pending = pending)) }
  }

  private fun requestSwitch(untested: Boolean) {
    val state = _uiState.value
    val target = state.normalizedInput ?: run {
      _uiState.update { it.copy(inputInvalid = it.input.isNotBlank()) }
      return
    }
    if (target == state.currentServerUrl) return
    if (needsCleartextConsent(target)) {
      askCleartextConsent(target, PendingCleartextAction.SAVE)
      return
    }
    // Sans test dans cette session, le drapeau des tuiles relevé la dernière fois reste vrai : le
    // perdre priverait la carte de son fond sans raison (SPEC.md § 5.7).
    val known = state.knownServers.firstOrNull { it.baseUrl == target }
    val hasTiles = if (state.connectionTest.finished) {
      state.connectionTest.tiles == CheckStepState.PASSED
    } else {
      known?.hasTiles == true
    }
    _uiState.update {
      it.copy(
        dialog = ServerDialog.SwitchEffects(baseUrl = target, hasTiles = hasTiles, untested = untested),
      )
    }
  }

  private fun runConnectionTest(baseUrl: String) {
    _uiState.update { it.copy(connectionTest = RUNNING_TEST) }
    viewModelScope.launch {
      val test = when (val outcome = serverRepository.test(baseUrl)) {
        is Outcome.Success -> outcome.value.toConnectionTest()

        is Outcome.Failure -> ConnectionTest(
          reachable = CheckStepState.FAILED,
          apiVersion = CheckStepState.FAILED,
          tiles = CheckStepState.FAILED,
          error = outcome.error,
        )
      }
      _uiState.update { it.copy(connectionTest = test) }
    }
  }

  /**
   * Point d'accroche du vidage des caches lors d'un changement de serveur (SPEC.md § 5.6.1).
   *
   * **À COMPLÉTER AU JALON 3** : vider les caches de résultats, de géocodage et de tuiles. Les
   * favoris et l'historique sont conservés, et un arrêt favori que le nouveau serveur ne reconnaît
   * plus reste affiché avec ses coordonnées et un signalement discret — il n'est jamais supprimé.
   * Aucun de ces caches n'existe à ce jalon ; la fonction est néanmoins appelée sur tous les chemins
   * de changement de serveur, pour que le lot qui les ajoutera n'ait qu'un seul endroit à remplir.
   */
  private fun clearCaches() = Unit

  companion object {
    private val RUNNING_TEST = ConnectionTest(
      reachable = CheckStepState.RUNNING,
      apiVersion = CheckStepState.RUNNING,
      tiles = CheckStepState.RUNNING,
    )

    private fun ServerCheck.toConnectionTest() = ConnectionTest(
      reachable = if (reachable) CheckStepState.PASSED else CheckStepState.FAILED,
      apiVersion = if (apiCompatible) CheckStepState.PASSED else CheckStepState.FAILED,
      // Un serveur sans tuiles reste utilisable : ce n'est pas un échec (SPEC.md § 5.7).
      tiles = if (tilesAvailable) CheckStepState.PASSED else CheckStepState.ABSENT,
    )

    /** Fabrique propre à ce ViewModel (docs/architecture.md § 3, règle 3). */
    fun factory(container: AppContainer) = viewModelFactory {
      initializer {
        ServerSettingsViewModel(container.serverRepository, container.cleartextConsentStore)
      }
    }
  }
}
