package io.github.mgdx.escale.ui.server

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.ServerCheck
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** SPEC.md § 5.6.1 : la machine à états de l'écran « Serveur MOTIS ». */
class ServerSettingsViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeServerRepository()
  private val consentStore = FakeCleartextConsentStore()

  /**
   * Le même `SavedStateHandle` d'un `ViewModel` à l'autre : c'est ce que fait le système quand il
   * tue le processus en arrière-plan puis restitue l'écran.
   */
  private val savedState = SavedStateHandle()

  private fun viewModel() = ServerSettingsViewModel(repository, consentStore, savedState)

  @Test
  fun `le champ reprend le serveur en service tant que rien n est saisi`() = runTest {
    val viewModel = viewModel()

    assertEquals(ServerUrl.DEFAULT_BASE_URL, viewModel.uiState.value.currentServerUrl)
    assertEquals(ServerUrl.DEFAULT_BASE_URL, viewModel.uiState.value.input)
  }

  @Test
  fun `la saisie est normalisee en racine de serveur`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("exemple.org/api/")

    assertEquals("https://exemple.org", viewModel.uiState.value.normalizedInput)
    assertFalse(viewModel.uiState.value.inputInvalid)
  }

  @Test
  fun `une saisie aberrante est signalee et ne lance aucun test`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("ftp://exemple.org")
    viewModel.onTestConnection()

    assertTrue(viewModel.uiState.value.inputInvalid)
    assertNull(viewModel.uiState.value.normalizedInput)
    assertEquals(emptyList<String>(), repository.testedUrls)
  }

  @Test
  fun `un test concluant ouvre l enregistrement`() = runTest {
    repository.testOutcome = Outcome.Success(
      ServerCheck(reachable = true, apiCompatible = true, tilesAvailable = true),
    )
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.PASSED, state.connectionTest.reachable)
    assertEquals(CheckStepState.PASSED, state.connectionTest.apiVersion)
    assertEquals(CheckStepState.PASSED, state.connectionTest.tiles)
    assertTrue(state.canSave)
    assertFalse(state.canForce)
  }

  @Test
  fun `un serveur sans tuiles reste utilisable`() = runTest {
    repository.testOutcome = Outcome.Success(
      ServerCheck(reachable = true, apiCompatible = true, tilesAvailable = false),
    )
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.ABSENT, state.connectionTest.tiles)
    assertTrue(state.canSave)
  }

  @Test
  fun `une version d API trop ancienne ferme l enregistrement direct`() = runTest {
    repository.testOutcome = Outcome.Success(
      ServerCheck(reachable = true, apiCompatible = false, tilesAvailable = false),
    )
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.FAILED, state.connectionTest.apiVersion)
    assertFalse(state.canSave)
    assertTrue(state.canForce)
  }

  @Test
  fun `un serveur injoignable expose son erreur`() = runTest {
    repository.testOutcome = Outcome.Failure(EscaleError.NoNetwork)
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.FAILED, state.connectionTest.reachable)
    assertEquals(EscaleError.NoNetwork, state.connectionTest.error)
    assertFalse(state.canSave)
  }

  @Test
  fun `retoucher l URL rend le test caduc`() = runTest {
    repository.testOutcome = Outcome.Success(
      ServerCheck(reachable = true, apiCompatible = true, tilesAvailable = true),
    )
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()
    viewModel.onInputChange("https://autre.exemple.org")

    assertEquals(CheckStepState.IDLE, viewModel.uiState.value.connectionTest.reachable)
    assertFalse(viewModel.uiState.value.canSave)
  }

  @Test
  fun `rien n est enregistre avant l annonce des effets du changement`() = runTest {
    repository.testOutcome = Outcome.Success(
      ServerCheck(reachable = true, apiCompatible = true, tilesAvailable = true),
    )
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()
    viewModel.onSave()

    assertEquals(emptyList<ServerConfig>(), repository.savedConfigs)
    val dialog = viewModel.uiState.value.dialog
    assertTrue(dialog is ServerDialog.SwitchEffects)
    assertFalse((dialog as ServerDialog.SwitchEffects).untested)

    viewModel.onConfirmSwitch()

    assertEquals(listOf("https://motis.exemple.org"), repository.savedConfigs.map { it.baseUrl })
    assertTrue(repository.savedConfigs.single().hasTiles)
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `utiliser quand meme enregistre un serveur non teste, en le signalant`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onUseAnyway()
    val dialog = viewModel.uiState.value.dialog as ServerDialog.SwitchEffects
    assertTrue(dialog.untested)

    viewModel.onConfirmSwitch()

    assertEquals("https://motis.exemple.org", repository.savedConfigs.single().baseUrl)
    assertNull(repository.savedConfigs.single().lastCheckedAt)
  }

  @Test
  fun `aucune requete en clair ne part sans consentement`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("http://192.168.1.10:8080")
    viewModel.onTestConnection()

    assertEquals(emptyList<String>(), repository.testedUrls)
    val dialog = viewModel.uiState.value.dialog
    assertTrue(dialog is ServerDialog.CleartextWarning)
    assertEquals("192.168.1.10", (dialog as ServerDialog.CleartextWarning).host)
    assertEquals(PendingCleartextAction.TEST, dialog.pending)
  }

  @Test
  fun `le consentement accepte relance l action et n est demande qu une fois par hote`() = runTest {
    repository.testOutcome = Outcome.Success(
      ServerCheck(reachable = true, apiCompatible = true, tilesAvailable = false),
    )
    val viewModel = viewModel()

    viewModel.onInputChange("http://192.168.1.10:8080")
    viewModel.onTestConnection()
    viewModel.onAcceptCleartext()

    assertEquals(listOf("192.168.1.10"), consentStore.acceptedHosts)
    assertEquals(listOf("http://192.168.1.10:8080"), repository.testedUrls)
    assertNull(viewModel.uiState.value.dialog)

    // Deuxième requête vers le même hôte : plus aucun avertissement.
    viewModel.onTestConnection()

    assertEquals(1, consentStore.acceptedHosts.size)
    assertEquals(2, repository.testedUrls.size)
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `un consentement deja memorise n interrompt plus rien`() = runTest {
    val store = FakeCleartextConsentStore(initial = setOf("192.168.1.10"))
    repository.testOutcome = Outcome.Success(
      ServerCheck(reachable = true, apiCompatible = true, tilesAvailable = false),
    )
    val viewModel = ServerSettingsViewModel(repository, store, savedState)

    viewModel.onInputChange("http://192.168.1.10:8080")
    viewModel.onTestConnection()

    assertEquals(listOf("http://192.168.1.10:8080"), repository.testedUrls)
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `une bascule vers un serveur connu ne demande pas de ressaisir l URL`() = runTest {
    val known = ServerConfig(baseUrl = "https://motis.exemple.org", label = "motis.exemple.org")
    repository.addKnown(known)
    val viewModel = viewModel()

    viewModel.onSelectKnownServer(known)

    assertEquals("https://motis.exemple.org", viewModel.uiState.value.input)
    val dialog = viewModel.uiState.value.dialog as ServerDialog.SwitchEffects
    assertEquals("https://motis.exemple.org", dialog.baseUrl)

    viewModel.onConfirmSwitch()

    assertEquals("https://motis.exemple.org", repository.savedConfigs.single().baseUrl)
  }

  @Test
  fun `une bascule sans nouveau test conserve le drapeau des tuiles`() = runTest {
    val known = ServerConfig(
      baseUrl = "https://motis.exemple.org",
      label = "motis.exemple.org",
      hasTiles = true,
    )
    repository.addKnown(known)
    val viewModel = viewModel()

    viewModel.onSelectKnownServer(known)
    viewModel.onConfirmSwitch()

    assertTrue(repository.savedConfigs.single().hasTiles)
  }

  @Test
  fun `le balayage retire un serveur des serveurs connus`() = runTest {
    val viewModel = viewModel()

    viewModel.onForgetServer("https://motis.exemple.org")

    assertEquals(listOf("https://motis.exemple.org"), repository.forgottenUrls)
  }

  @Test
  fun `le retour au serveur par defaut passe par la meme confirmation`() = runTest {
    val other = ServerConfig(baseUrl = "https://motis.exemple.org", label = "motis.exemple.org")
    repository.save(other)
    val viewModel = viewModel()

    viewModel.onResetToDefault()
    val dialog = viewModel.uiState.value.dialog as ServerDialog.SwitchEffects
    assertEquals(ServerUrl.DEFAULT_BASE_URL, dialog.baseUrl)

    viewModel.onConfirmSwitch()

    assertEquals(ServerUrl.DEFAULT_BASE_URL, repository.savedConfigs.last().baseUrl)
  }

  @Test
  fun `annuler une boite de dialogue n enregistre rien`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onUseAnyway()
    viewModel.onDismissDialog()

    assertNull(viewModel.uiState.value.dialog)
    assertEquals(emptyList<ServerConfig>(), repository.savedConfigs)
  }

  @Test
  fun `la saisie survit a la mort du processus`() = runTest {
    // Anomalie A3 : le système tue l'application en arrière-plan, et le champ revenait vide.
    viewModel().onInputChange("exemple.org/api/")

    val restored = viewModel()

    assertEquals("exemple.org/api/", restored.uiState.value.input)
    assertEquals("https://exemple.org", restored.uiState.value.normalizedInput)
  }

  @Test
  fun `une saisie aberrante est de nouveau signalee apres la mort du processus`() = runTest {
    viewModel().onInputChange("ftp://exemple.org")

    val restored = viewModel()

    assertTrue(restored.uiState.value.inputInvalid)
    assertNull(restored.uiState.value.normalizedInput)
  }

  @Test
  fun `sans saisie, le champ reprend le serveur en service apres la mort du processus`() = runTest {
    viewModel()

    val restored = viewModel()

    assertEquals(ServerUrl.DEFAULT_BASE_URL, restored.uiState.value.input)
  }
}
